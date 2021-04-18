package quartz.clients

import com.unboundid.ldap.sdk.LDAPConnection
import com.unboundid.util.ssl.SSLUtil
import com.unboundid.util.ssl.TrustAllTrustManager

import com.unboundid.ldap.sdk.AsyncSearchResultListener
import com.unboundid.ldap.sdk.AsyncRequestID
import com.unboundid.ldap.sdk.SearchResult
import com.unboundid.ldap.sdk.SearchRequest
import com.unboundid.ldap.sdk.SearchResultEntry
import com.unboundid.ldap.sdk.SearchResultReference
import com.unboundid.ldap.sdk.SearchScope

import zio.Chunk
import zio.IO
import zio.ZIO
import zio.Runtime
import zio.ZRef

object AsyncLDAP {

  var listener: AsyncSearchResultListener = null
  var cb: IO[Exception, Option[SearchResultEntry]] => Unit = null

  val runtime = Runtime.default

}

class AsyncLDAP(host: String, port: Int, BindDN: String, BindPwd: String) {

  val HOST = host
  val PORT = port
  val BIND_DN = BindDN
  val PWD = BindPwd

  def ldap_con_ssl() = {
    val sslUtil = new SSLUtil(new TrustAllTrustManager());
    val sslSocketFactory = sslUtil.createSSLSocketFactory();
    val lc = new LDAPConnection(sslSocketFactory);

    lc.connect(HOST, PORT)
    lc.bind(BIND_DN, PWD)

    lc
  }

  def ldap_con_close(c: LDAPConnection) = {
    c.close()
  }

  /** Async search with Chunk
    */
  def a_search(
      c: LDAPConnection,
      baseDN: String,
      filter: String,
      attributes: String*
  ) =
    IO.effectAsync[Exception, Chunk[SearchResultEntry]](cb => {

      val listener = new AsyncSearchResultListener {
        var results = Chunk[SearchResultEntry]()
        /////////////////////////
        def searchResultReceived(
            reqId: AsyncRequestID,
            searchRes: SearchResult
        ) =
          cb(IO.effectTotal(results))
        /////////////////////////
        def searchEntryReturned(searchEntry: SearchResultEntry) =
          results = results ++ Chunk(searchEntry)
        ////////////////////////
        def searchReferenceReturned(searchReference: SearchResultReference) = {}
      }
      c.asyncSearch(
        new SearchRequest(
          listener,
          baseDN,
          SearchScope.SUB,
          filter,
          attributes: _*
        )
      )

    })

  //import zio.ZRef
  import zio.stream.ZStream
  import zio.ZQueue
  import zio.ZManaged

  /** Async search producing ZStream
    */
  def s_search(
      c: LDAPConnection,
      baseDN: String,
      filter: String,
      attributes: String*
  ) = {

    def eofCheck(last: Option[Option[SearchResultEntry]]) = last match {
      case None        => false
      case Some(value) => if (value.isDefined) false else true
    }

    val process: ZManaged[Any, Nothing, ZIO[Any, Option[Exception], Chunk[
      SearchResultEntry
    ]]] =
      for {
        eof <- ZRef.make(false).toManaged_
        queue <- ZQueue
          .unbounded[Option[SearchResultEntry]]
          .toManaged(_.shutdown)
        _ <- ZIO
          .effectTotal(new AsyncSearchResultListener {
            def searchResultReceived(
                reqId: AsyncRequestID,
                searchRes: SearchResult
            ) = {
              AsyncLDAP.runtime.unsafeRun(queue.offer(None))
            }

            def searchEntryReturned(searchEntry: SearchResultEntry) = {
              AsyncLDAP.runtime.unsafeRun(queue.offer(Some(searchEntry)))
            }

            def searchReferenceReturned(
                searchReference: SearchResultReference
            ) = {}
          })
          .map(listener =>
            c.asyncSearch(
              new SearchRequest(
                listener,
                baseDN,
                SearchScope.SUB,
                filter,
                attributes: _*
              )
            )
          )
          .toManaged_

        p = for {
          isEnd <- eof.get
          items <- if (isEnd == false) queue.takeAll else ZIO.fail(None)
          aboutToEnd <- ZIO.succeed(eofCheck(items.lastOption))
          _ <- eof.set(true).when(aboutToEnd)
          res = items.foldLeft(Chunk[SearchResultEntry]())((z, e) =>
            if (e.isDefined) z :+ e.get else z
          )

        } yield (res)
      } yield (p)

    ZStream(process)
  }

}
