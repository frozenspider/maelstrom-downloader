package org.fs.mael.backend.http

import java.io.File
import java.io.RandomAccessFile
import java.net.UnknownHostException

import scala.util.Random

import org.apache.http.HttpEntity
import org.apache.http.client.methods.RequestBuilder
import org.apache.http.config.Registry
import org.apache.http.config.RegistryBuilder
import org.apache.http.config.SocketConfig
import org.apache.http.conn.HttpClientConnectionManager
import org.apache.http.conn.HttpConnectionFactory
import org.apache.http.conn.ManagedHttpClientConnection
import org.apache.http.conn.routing.HttpRoute
import org.apache.http.conn.socket.ConnectionSocketFactory
import org.apache.http.conn.socket.PlainConnectionSocketFactory
import org.apache.http.conn.ssl.SSLConnectionSocketFactory
import org.apache.http.impl.client.BasicCookieStore
import org.apache.http.impl.client.HttpClients
import org.apache.http.impl.conn.BasicHttpClientConnectionManager
import org.apache.http.impl.conn.DefaultHttpResponseParserFactory
import org.apache.http.impl.conn.ManagedHttpClientConnectionFactory
import org.apache.http.impl.io.DefaultHttpRequestWriterFactory
import org.fs.mael.core.BackendDownloader
import org.fs.mael.core.Status
import org.fs.mael.core.UserFriendlyException
import org.fs.mael.core.entry.LogEntry
import org.fs.mael.core.event.EventManager
import org.slf4s.Logging

class HttpBackendDownloader extends BackendDownloader[HttpBackend.DE] with Logging {

  private val dlThreadGroup = new ThreadGroup(HttpBackend.Id + "-dltg")

  private var threads: Seq[DownloadingThread] = Seq.empty

  def startInner(de: HttpBackend.DE): Unit =
    this.synchronized {
      if (threads exists (t => t.de == de && t.isAlive)) {
        log.warn(s"Attempt to start an already-running entry: ${de.uri} (${de.id})")
      } else {
        changeStatusAndFire(de, Status.Running)
        log.info(s"Download started: ${de.uri} (${de.id})")
        val newThread = new DownloadingThread(de)
        threads = newThread +: threads
        newThread.start()
      }
    }

  def stopInner(de: HttpBackend.DE): Unit = {
    this.synchronized {
      threads find (_.de == de) match {
        case None =>
          log.warn(s"Attempt to stop a not started entry: ${de.uri} (${de.id})")
        case Some(t) =>
          t.interrupt()
      }
      stopLogAndFire(de)
    }
  }

  private def stopLogAndFire(de: HttpBackend.DE): Unit = {
    changeStatusAndFire(de, Status.Stopped)
    addLogAndFire(de, LogEntry.info("Download stopped"))
    log.info(s"Download stopped: ${de.uri} (${de.id})")
  }

  private def errorLogAndFire(de: HttpBackend.DE, msg: String): Unit = {
    changeStatusAndFire(de, Status.Error)
    addLogAndFire(de, LogEntry.error(msg))
    log.info(s"Download error - $msg: ${de.uri} (${de.id})")
  }

  private def removeThread(t: Thread): Unit = {
    threads = threads filter (_ != t)
  }

  private class DownloadingThread(val de: HttpBackend#DE)
    extends Thread(dlThreadGroup, dlThreadGroup.getName + "_" + de.id) {

    override def run(): Unit = {
      try {
        runInner()
      } finally {
        removeThread(this)
      }
    }

    private def runInner(): Unit = {
      try {
        de.location.mkdirs
        if (!de.location.exists) {
          throw new UserFriendlyException(s"Can't create path ${de.location}")
        }
        addLogAndFire(de, LogEntry.info("Starting download..."))
        val cookieStore = new BasicCookieStore()
        val connManager = createConnManager(de)(0) // TODO: Timeout
        val httpClient = {
          val clientBuilder = HttpClients.custom()
            .setDefaultCookieStore(cookieStore)
            .setConnectionManager(connManager)
          clientBuilder.build()
        }

        val rb = RequestBuilder.get(de.uri)
        val req = rb.build()
        val res = httpClient.execute(req)
        try {
          if (res.getStatusLine.getStatusCode != 200) {
            throw new UserFriendlyException(s"Server responded with an error")
          }
          val entity = res.getEntity

          val filename = de.filenameOption match {
            case Some(s) => s
            case None =>
              // TODO: Deduce filename
              val randomStr = Random.alphanumeric.take(10).mkString
              val assignedName = "file-" + randomStr + ".txt"
              de.filenameOption = Some(assignedName)
              EventManager.fireDetailsChanged(de)
              assignedName
          }

          entity.getContentLength match {
            case x if x > 0 =>
              de.sizeOption = Some(x)
              EventManager.fireDetailsChanged(de)
            case _ => // NOOP
          }

          // TODO: de.supportsResumingOption

          downloadEntity(entity)

          // If no exception is thrown
          changeStatusAndFire(de, Status.Complete)
          addLogAndFire(de, LogEntry.info("Download complete"))
          log.info(s"Download finished: ${de.uri} (${de.id})")
        } finally {
          res.close()
        }
      } catch {
        case ex: UserFriendlyException =>
          errorLogAndFire(de, ex.getMessage)
        case ex: UnknownHostException =>
          errorLogAndFire(de, "Host cannot be resolved: " + ex.getMessage)
        case ex: InterruptedException =>
          // Otherwise terminate normally
          if (de.status.canBeStopped) stopLogAndFire(de)
        case ex: Exception =>
          log.error("Unexpected error", ex)
          errorLogAndFire(de, "Unexpected error: " + ex.toString)
      }
    }

    private def downloadEntity(entity: HttpEntity): Unit = {
      val file = instantiateFile()
      try {
        de.sizeOption map file.setLength

        val currThread = Thread.currentThread()
        val bufferSize = 10 * 1024 // 10 Kb

        val is = entity.getContent
        try {
          val buffer = Array.fill[Byte](bufferSize)(0x00)
          val startPos: Long = 0
          var len = is.read(buffer)
          while (len > 0) {
            file.write(buffer, 0, len)
            de.sections += (startPos -> (file.getFilePointer - startPos))
            EventManager.fireProgress(de)
            if (currThread.isInterrupted) throw new InterruptedException
            len = is.read(buffer)
          }
        } finally {
          is.close()
        }
      } finally {
        file.close()
      }
    }

    private def instantiateFile(): RandomAccessFile = {
      val f = new File(de.location, de.filenameOption.get)
      f.createNewFile()
      if (!f.canWrite) {
        throw new UserFriendlyException(s"Can't write to file ${f.getAbsolutePath}")
      }
      new RandomAccessFile(f, "rw")
    }

    private def createConnManager(de: HttpBackend#DE)(connTimeoutMs: Int): HttpClientConnectionManager = {
      val connFactory: HttpConnectionFactory[HttpRoute, ManagedHttpClientConnection] =
        new ManagedHttpClientConnectionFactory(
          new HttpMessageWriterProxyFactory(
            DefaultHttpRequestWriterFactory.INSTANCE,
            msg => addLogAndFire(de, LogEntry.request(msg))
          ),
          new HttpMessageParserProxyFactory(
            DefaultHttpResponseParserFactory.INSTANCE,
            msg => addLogAndFire(de, LogEntry.response(msg))
          )
        )
      val connMgr = new BasicHttpClientConnectionManager(HttpBackendDownloader.SocketFactoryRegistry, connFactory, null, null)
      connMgr.setSocketConfig(
        SocketConfig.custom()
          .setSoTimeout(connTimeoutMs)
          .build()
      )
      connMgr
    }
  }
}

object HttpBackendDownloader {
  private val SocketFactoryRegistry: Registry[ConnectionSocketFactory] = RegistryBuilder.create[ConnectionSocketFactory]
    .register("http", PlainConnectionSocketFactory.getSocketFactory())
    .register("https", SSLConnectionSocketFactory.getSocketFactory())
    .build()
}
