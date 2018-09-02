package org.fs.mael.backend.http

import java.io.RandomAccessFile
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.URLDecoder
import java.net.UnknownHostException

import scala.io.Codec
import scala.util.Random

import org.apache.http.HttpEntity
import org.apache.http.HttpHeaders
import org.apache.http.HttpResponse
import org.apache.http.HttpStatus
import org.apache.http.client.CookieStore
import org.apache.http.client.methods.HttpUriRequest
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
import org.apache.http.conn.ssl.NoopHostnameVerifier
import org.apache.http.conn.ssl.SSLConnectionSocketFactory
import org.apache.http.impl.client.BasicCookieStore
import org.apache.http.impl.client.HttpClients
import org.apache.http.impl.conn.BasicHttpClientConnectionManager
import org.apache.http.impl.conn.DefaultHttpResponseParserFactory
import org.apache.http.impl.conn.ManagedHttpClientConnectionFactory
import org.apache.http.impl.cookie.BasicClientCookie
import org.apache.http.impl.io.DefaultHttpRequestWriterFactory
import org.apache.http.ssl.SSLContextBuilder
import org.fs.mael.backend.http.config.HttpSettings
import org.fs.mael.backend.http.utils.HttpUtils
import org.fs.mael.core.Status
import org.fs.mael.core.UserFriendlyException
import org.fs.mael.core.backend.BackendDownloader
import org.fs.mael.core.entry.DownloadEntry
import org.fs.mael.core.entry.LogEntry
import org.fs.mael.core.event.EventManager
import org.fs.mael.core.transfer.TransferManager
import org.fs.mael.core.utils.CoreUtils._
import org.fs.mael.core.utils.IoUtils
import org.slf4s.Logging

import javax.net.ssl.SSLException

class HttpDownloader(
  override val eventMgr:    EventManager,
  override val transferMgr: TransferManager
) extends BackendDownloader(HttpBackend.Id) with Logging {

  private var threads: Seq[DownloadingThread] = Seq.empty

  override def startInner(de: DownloadEntry, timeoutMs: Int): Unit =
    this.synchronized {
      if (threads exists (t => t.de == de && t.isAlive && !t.isInterrupted)) {
        val activeThreads = threads.filter(t => t.isAlive && !t.isInterrupted)
        log.warn(s"Attempt to start an already-running entry: ${de.uri} (${de.id})" +
          s", active threads: ${activeThreads.size} (${activeThreads.map(_.getName).mkString(", ")})")
      } else {
        changeStatusAndFire(de, Status.Running)
        val newThread = new DownloadingThread(de, timeoutMs)
        threads = newThread +: threads
        newThread.start()
        log.info(s"Download started: ${de.uri} (${de.id}) as ${newThread.getName}")
      }
    }

  override def stopInner(de: DownloadEntry): Unit = {
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

  /** For test usage only! */
  def test_getThreads: Seq[Thread] = {
    this.synchronized {
      threads
    }
  }

  private def removeThread(t: Thread): Unit = {
    this.synchronized {
      threads = threads filter (_ != t)
    }
  }

  private def stopLogAndFire(de: DownloadEntry, threadOption: Option[Thread]): Unit = {
    changeStatusAndFire(de, Status.Stopped)
    addLogAndFire(de, LogEntry.info("Download stopped"))
    threadOption match {
      case None    => log.info(s"Download stopped: ${de.uri} (${de.id})")
      case Some(t) => log.info(s"Download stopped: ${de.uri} (${de.id}) by ${t.getName}")
    }
  }

  private def errorLogAndFire(de: DownloadEntry, msg: String): Unit = {
    changeStatusAndFire(de, Status.Error)
    addLogAndFire(de, LogEntry.error(msg))
    log.info(s"Download error - $msg: ${de.uri} (${de.id})")
  }

  private class DownloadingThread(val de: DownloadEntry, timeoutMs: Int)
    extends Thread(dlThreadGroup, dlThreadGroup.getName + "_" + de.id + "_" + Random.alphanumeric.take(10).mkString) {

    this.setDaemon(true)

    private val partial = de.downloadedSize > 0

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
        requireFriendly(de.location.exists, s"Can't create path ${de.location}")
        addLogAndFire(de, LogEntry.info("Starting download..."))

        if (partial) {
          assert(de.filenameOption.isDefined)
          requireFriendly(de.fileOption.get.exists(), "File is missing")
        }
        if (partial && !de.supportsResumingOption.getOrElse(true)) {
          de.sections.clear()
          addLogAndFire(de, LogEntry.info("Resuming is not supported, starting over"))
        }

        if (partial && de.sizeOption == Some(de.downloadedSize)) {
          // File already fully downloaded, probably a checksum error
        } else {
          contactServerAndDownload()
        }

        // If no exception is thrown
        checkIntegrityAndComplete(de)
        log.info(s"Download complete: ${de.uri} (${de.id})")
      } catch {
        case ex: UserFriendlyException =>
          errorLogAndFire(de, ex.getMessage)
        case ex: UnknownHostException =>
          errorLogAndFire(de, "Host cannot be resolved: " + ex.getMessage)
        case ex: SocketException =>
          errorLogAndFire(de, ex.getMessage)
        case ex: SocketTimeoutException =>
          errorLogAndFire(de, "Request timed out")
        case ex: SSLException =>
          errorLogAndFire(de, "SSL error: " + ex.getMessage)
        case ex: InterruptedException =>
          log.debug(s"Thread interrupted: ${this.getName}")
        case ex: Exception =>
          log.error("Unexpected error", ex)
          errorLogAndFire(de, "Unexpected error: " + ex.toString)
      }
    }

    private def contactServerAndDownload(): Unit = {
      val cookieStore = new BasicCookieStore()
      val connManager = createConnManager(timeoutMs)
      val httpClient = {
        val clientBuilder = HttpClients.custom()
          .setDefaultCookieStore(cookieStore)
          .setConnectionManager(connManager)
        clientBuilder.build()
      }

      val req = {
        val rb = RequestBuilder.get(de.uri)
        rb.addHeader(HttpHeaders.CONNECTION, "close")
        if (partial) {
          // Note that range upper-bound is inclusive
          rb.addHeader(HttpHeaders.RANGE, "bytes=" + de.downloadedSize + "-")
        }
        addCustomHeaders(rb, cookieStore)
        rb.build()
      }

      val res = httpClient.execute(req)
      try {
        val responseCode = res.getStatusLine.getStatusCode
        val entity = doChecked {
          if ((!partial && responseCode != HttpStatus.SC_OK) || (partial && responseCode != HttpStatus.SC_PARTIAL_CONTENT)) {
            failFriendly(s"Server responded with an error")
          }
          res.getEntity
        }

        val filename = de.filenameOption getOrElse {
          val filename = deduceFilename(res)
          doChecked {
            de.filenameOption = Some(filename)
            eventMgr.fireDetailsChanged(de)
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
                eventMgr.fireDetailsChanged(de)
              case Some(prevSize) if prevSize != reportedSize =>
                failFriendly("File size on server changed")
              case _ => // NOOP
            }
          }
        }

        doChecked {
          de.supportsResumingOption = Some(
            if (partial && responseCode == HttpStatus.SC_PARTIAL_CONTENT)
              true
            else
              Option(res.getFirstHeader(HttpHeaders.ACCEPT_RANGES)) map (_.getValue == "bytes") getOrElse false
          )
          eventMgr.fireDetailsChanged(de)
          if (!de.supportsResumingOption.get) {
            addLogAndFire(de, LogEntry.info("Server doesn't support resuming"))
          }
        }

        downloadEntity(req, entity)
      } finally {
        res.close()
        connManager.shutdown()
      }
    }

    private def addCustomHeaders(rb: RequestBuilder, cookieStore: CookieStore): Unit = {
      val localCfg = de.backendSpecificCfg
      val userAgentOption = localCfg(HttpSettings.UserAgent)
      userAgentOption foreach { userAgent =>
        rb.setHeader(HttpHeaders.USER_AGENT, userAgent)
      }
      val cookies = localCfg(HttpSettings.Cookies)
      cookies foreach {
        case (k, v) =>
          val cookie = new BasicClientCookie(k, v)
          cookie.setDomain(rb.getUri.getHost)
          cookieStore.addCookie(cookie)
      }
      val customHeaders = localCfg(HttpSettings.Headers)
      customHeaders foreach {
        case (k, v) => rb.addHeader(k, v)
      }
    }

    private def deduceFilename(res: HttpResponse): String = {
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
          headerParts.get("filename*") flatMap { filenameEnc =>
            try {
              Some(HttpUtils.decodeRfc5987ExtValue(filenameEnc))
            } catch {
              case ex: Exception =>
                addLogAndFire(de, LogEntry.error("Server responded with malformed filename*: " + ex))
                None
            }
          } orElse headerParts.get("filename")
        })
      } orElse {
        // Try to use the last part of URL path as filename
        de.uri.toURL.getPath.split("/").lastOption flatMap {
          case x if x.length > 0 => Some(URLDecoder.decode(x, Codec.UTF8.name))
          case _                 => None
        }
      } map { fn =>
        IoUtils.asValidFilename(fn)
      } getOrElse {
        // When everything else fails - generate a filename from UUID
        "file-" + de.id.toString.toUpperCase
      }
    }

    private def downloadEntity(req: HttpUriRequest, entity: HttpEntity): Unit = {
      val file = instantiateFile()
      try {
        de.sizeOption map file.setLength
        file.seek(de.downloadedSize)

        val bufferSize = 1 * 1024 // 1 KB

        val is = entity.getContent
        try {
          val buffer = Array.fill[Byte](bufferSize)(0x00)
          val sectionStartPos: Long = 0
          var len = transferMgr.read(is, buffer)
          while (len > 0) {
            file.write(buffer, 0, len)
            doChecked {
              de.sections += (sectionStartPos -> (file.getFilePointer - sectionStartPos))
              eventMgr.fireProgress(de)
              len = transferMgr.read(is, buffer)
            }
          }
        } finally {
          // Necessary to forcefully close input stream.
          // This is needed because closing the input stream with known Content-Length
          // attempts to download the rest of the entity.
          // Yes, it's as messed-up as it sounds.
          req.abort()
        }
      } finally {
        file.close()
      }
    }

    private def instantiateFile(): RandomAccessFile = {
      val f = de.fileOption.get
      (partial, f.exists) match {
        case (false, true)  => failFriendly(s"File already exists")
        case (true, exists) => assert(exists) // Was checked before
        case _              => // NOOP
      }
      f.createNewFile()
      requireFriendly(f.canWrite, s"Can't write to file ${f.getAbsolutePath}")
      new RandomAccessFile(f, "rw")
    }

    private def createConnManager(connTimeoutMs: Int): HttpClientConnectionManager = {
      val connFactory: HttpConnectionFactory[HttpRoute, ManagedHttpClientConnection] =
        new ManagedHttpClientConnectionFactory(
          new HttpMessageWriterLoggingFactory(
            DefaultHttpRequestWriterFactory.INSTANCE,
            msg => doChecked(addLogAndFire(de, LogEntry.request(msg)))
          ),
          new HttpMessageParserLoggingFactory(
            DefaultHttpResponseParserFactory.INSTANCE,
            msg => doChecked(addLogAndFire(de, LogEntry.response(msg)))
          )
        )
      val connMgr = new BasicHttpClientConnectionManager(createSocketFactoryRegistry(), connFactory, null, FakeDnsResolver)
      connMgr.setSocketConfig(
        SocketConfig.custom()
          .setSoTimeout(connTimeoutMs)
          .build()
      )
      connMgr
    }

    /**
     * Creates registry of `ConnectionSocketFactory` to be used in connection manager.
     *
     * Note that factories should perform DNS resolution on their own.
     */
    private def createSocketFactoryRegistry(): Registry[ConnectionSocketFactory] = {
      val proxy = de.backendSpecificCfg.resolve(HttpSettings.ConnectionProxy)
      def logUpdate(msg: String) = addLogAndFire(de, LogEntry.info(msg))
      RegistryBuilder.create[ConnectionSocketFactory]
        .register("http", new ProxyConnectionSocketFactory(proxy, logUpdate, PlainConnectionSocketFactory.getSocketFactory()))
        .register("https", new ProxyLayeredConnectionSocketFactory(proxy, logUpdate, createSslConnSocketFactory()))
        .build()
    }

    private def createSslConnSocketFactory(): SSLConnectionSocketFactory = {
      if (de.backendSpecificCfg(HttpSettings.DisableSslValidation)) {
        HttpDownloader.NonValidatingSslConnSocketFactory
      } else {
        val sf = SSLConnectionSocketFactory.getSocketFactory()
        HttpUtils.validateSslConnSocketFactory(sf)
        sf
      }
    }

    /** Execute an action only if thread isn't interrupted, in which case - throw {@code InterruptedException} */
    private def doChecked[T](action: => T): T = {
      if (this.isInterrupted) throw new InterruptedException
      action
    }
  }
}

object HttpDownloader {
  private lazy val NonValidatingSslConnSocketFactory: SSLConnectionSocketFactory = {
    val sslContext = new SSLContextBuilder()
      .loadTrustMaterial(null, (chain, authType) => true)
      .build()
    new SSLConnectionSocketFactory(sslContext, NoopHostnameVerifier.INSTANCE)
  }
}
