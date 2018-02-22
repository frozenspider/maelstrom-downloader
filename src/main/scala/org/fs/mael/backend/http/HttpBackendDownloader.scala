package org.fs.mael.backend.http

import java.io.File
import java.io.RandomAccessFile
import java.net.SocketException
import java.net.UnknownHostException

import scala.util.Random

import org.apache.http.HttpEntity
import org.apache.http.HttpHeaders
import org.apache.http.HttpResponse
import org.apache.http.HttpStatus
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
import org.fs.mael.core.CoreUtils._
import org.fs.mael.core.Status
import org.fs.mael.core.UserFriendlyException
import org.fs.mael.core.entry.LogEntry
import org.fs.mael.core.event.EventManager
import org.slf4s.Logging

class HttpBackendDownloader extends BackendDownloader[HttpBackend.DE] with Logging {

  private val dlThreadGroup = new ThreadGroup(HttpBackend.Id + "_download").withCode { tg =>
    tg.setDaemon(true)
  }

  private var threads: Seq[DownloadingThread] = Seq.empty

  def startInner(de: HttpBackend.DE): Unit =
    this.synchronized {
      if (threads exists (t => t.de == de && t.isAlive && !t.isInterrupted)) {
        val activeThreads = threads.filter(t => t.isAlive && !t.isInterrupted)
        log.warn(s"Attempt to start an already-running entry: ${de.uri} (${de.id})" +
          s", active threads: ${activeThreads.size} (${activeThreads.map(_.getName).mkString(", ")})")
      } else {
        changeStatusAndFire(de, Status.Running)
        val newThread = new DownloadingThread(de)
        threads = newThread +: threads
        newThread.start()
        log.info(s"Download started: ${de.uri} (${de.id}) as ${newThread.getName}")
      }
    }

  def stopInner(de: HttpBackend.DE): Unit = {
    this.synchronized {
      val threadOption = threads find (_.de == de)
      threadOption match {
        case None =>
          log.warn(s"Attempt to stop a not started entry: ${de.uri} (${de.id})")
        case Some(t) =>
          t.interrupt()
      }
      stopLogAndFire(de, threadOption)
    }
  }

  private def stopLogAndFire(de: HttpBackend.DE, threadOption: Option[Thread]): Unit = {
    changeStatusAndFire(de, Status.Stopped)
    addLogAndFire(de, LogEntry.info("Download stopped"))
    threadOption match {
      case None    => log.info(s"Download stopped: ${de.uri} (${de.id})")
      case Some(t) => log.info(s"Download stopped: ${de.uri} (${de.id}) by ${t.getName}")
    }
  }

  private def errorLogAndFire(de: HttpBackend.DE, msg: String): Unit = {
    changeStatusAndFire(de, Status.Error)
    addLogAndFire(de, LogEntry.error(msg))
    log.info(s"Download error - $msg: ${de.uri} (${de.id})")
  }

  private def removeThread(t: Thread): Unit = {
    this.synchronized {
      threads = threads filter (_ != t)
    }
  }

  // TODO: Support multiple download threads
  // TODO: Handle "file already exists"
  // TODO: Handle partially downloaded file deleted
  private class DownloadingThread(val de: HttpBackend#DE)
    extends Thread(dlThreadGroup, dlThreadGroup.getName + "_" + de.id + "_" + Random.alphanumeric.take(10).mkString) {

    this.setDaemon(true)

    override def run(): Unit = {
      try {
        runInner()
      } finally {
        removeThread(this)
        log.debug(s"Thread removed: ${this.getName}")
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
        val connManager = createConnManager(0) // TODO: Timeout
        val httpClient = {
          val clientBuilder = HttpClients.custom()
            .setDefaultCookieStore(cookieStore)
            .setConnectionManager(connManager)
          clientBuilder.build()
        }

        val partial = de.downloadedSize > 0

        val req = {
          val rb = RequestBuilder.get(de.uri)
          if (partial) {
            // Note that range upper-bound is inclusive
            rb.addHeader(HttpHeaders.RANGE, "bytes=" + de.downloadedSize + "-")
          }
          rb.build()
        }
        val res = httpClient.execute(req)
        try {
          val entity = doChecked {
            val responseCode = res.getStatusLine.getStatusCode
            if ((!partial && responseCode != HttpStatus.SC_OK) || (partial && responseCode != HttpStatus.SC_PARTIAL_CONTENT)) {
              throw new UserFriendlyException(s"Server responded with an error")
            }
            res.getEntity
          }

          val filename = de.filenameOption getOrElse {
            val filename = deduceFilename(res)
            doChecked {
              de.filenameOption = Some(filename)
              EventManager.fireDetailsChanged(de)
            }
            filename
          }

          val contentLengthOption = entity.getContentLength match {
            case x if x > 0 => Some(x)
            case _          => None
          }
          val reportedSizeOption = contentLengthOption map (x => if (partial) x + de.downloadedSize else x)
          reportedSizeOption map { reportedSize =>
            doChecked {
              de.sizeOption match {
                case None =>
                  de.sizeOption = Some(reportedSize)
                  EventManager.fireDetailsChanged(de)
                case Some(prevSize) if prevSize != reportedSize =>
                  throw new UserFriendlyException("File size on server changed")
                case _ => // NOOP
              }
            }
          }

          doChecked {
            de.supportsResumingOption = Some(
              Option(res.getFirstHeader(HttpHeaders.ACCEPT_RANGES)) map (_.getValue == "bytes") getOrElse false
            )
            EventManager.fireDetailsChanged(de)
          }

          downloadEntity(entity)

          // If no exception is thrown
          changeStatusAndFire(de, Status.Complete)
          addLogAndFire(de, LogEntry.info("Download complete"))
          log.info(s"Download complete: ${de.uri} (${de.id})")
        } finally {
          res.close()
        }
      } catch {
        case ex: UserFriendlyException =>
          errorLogAndFire(de, ex.getMessage)
        case ex: UnknownHostException =>
          // TODO: Support retries
          errorLogAndFire(de, "Host cannot be resolved: " + ex.getMessage)
        case ex: SocketException =>
          // TODO: Support retries
          errorLogAndFire(de, ex.getMessage)
        case ex: InterruptedException =>
          log.debug(s"Thread interrupted: ${this.getName}")
        case ex: Exception =>
          log.error("Unexpected error", ex)
          errorLogAndFire(de, "Unexpected error: " + ex.toString)
      }
    }

    def deduceFilename(res: HttpResponse): String = {
      {
        // If filename is specified in Content-Disposition header
        Option(res.getFirstHeader("Content-Disposition")).flatMap(h => {
          val headerParts = (
            for {
              el <- h.getElements.toSeq
              param <- el.getParameters
            } yield (param.getName -> param.getValue)
          ).toMap
          // As per RFC-6266
          headerParts.get("filename*") orElse headerParts.get("filename")
        })
      } orElse {
        // Try to use the last part of URL path as filename
        de.uri.toURL.getPath.split("/").lastOption flatMap {
          case x if x.length > 0 => Some(x)
          case _                 => None
        }
      } map { fn =>
        // Replace invalid filename characters
        fn replaceAll ("[\\\\/:*?\"<>|]", "_")
      } getOrElse {
        // When everything else fails - generate a filename from UUID
        "file-" + de.id.toString.toUpperCase
      }
    }

    private def downloadEntity(entity: HttpEntity): Unit = {
      val file = instantiateFile()
      try {
        de.sizeOption map file.setLength
        file.seek(de.downloadedSize + 1)

        val bufferSize = 10 * 1024 // 10 Kb

        val is = entity.getContent
        try {
          val buffer = Array.fill[Byte](bufferSize)(0x00)
          val sectionStartPos: Long = 0
          var len = is.read(buffer)
          while (len > 0) {
            file.write(buffer, 0, len)
            doChecked {
              de.sections += (sectionStartPos -> (file.getFilePointer - 1 - sectionStartPos))
              EventManager.fireProgress(de)
              len = is.read(buffer)
            }
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

    private def createConnManager(connTimeoutMs: Int): HttpClientConnectionManager = {
      val connFactory: HttpConnectionFactory[HttpRoute, ManagedHttpClientConnection] =
        new ManagedHttpClientConnectionFactory(
          new HttpMessageWriterProxyFactory(
            DefaultHttpRequestWriterFactory.INSTANCE,
            msg => doChecked(addLogAndFire(de, LogEntry.request(msg)))
          ),
          new HttpMessageParserProxyFactory(
            DefaultHttpResponseParserFactory.INSTANCE,
            msg => doChecked(addLogAndFire(de, LogEntry.response(msg)))
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

    /** Execute an action only if thread isn't interrupted, in which case - throw {@code InterruptedException} */
    private def doChecked[T](action: => T): T = {
      if (this.isInterrupted) throw new InterruptedException
      action
    }
  }
}

object HttpBackendDownloader {
  private val SocketFactoryRegistry: Registry[ConnectionSocketFactory] = RegistryBuilder.create[ConnectionSocketFactory]
    .register("http", PlainConnectionSocketFactory.getSocketFactory())
    .register("https", SSLConnectionSocketFactory.getSocketFactory())
    .build()
}
