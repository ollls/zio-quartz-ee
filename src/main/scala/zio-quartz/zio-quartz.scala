package quartz

import zhttp.TLSServer
import zhttp.clients._
import zhttp.HttpRoutes
import zhttp.dsl._
import zhttp.MyLogging._
import zhttp.MyLogging
import zhttp.LogLevel
import zhttp.HttpRouter
import zhttp.StatusCode
import zhttp.clients.ResPool.ResPool

import zhttp.clients.HttpConnection
import zhttp.clients.ClientRequest
import zhttp.clients.ClientResponse

import zhttp.Response
import zhttp.Method._

import zio.App
import zio.ZIO
import zio.ZEnv
import zio.Chunk
import zio.ZLayer
import zio.json._

import com.unboundid.ldap.sdk.LDAPConnection
import com.unboundid.ldap.sdk.SearchResultEntry
import zhttp.clients.ResPoolCache
import zhttp.clients.ResPoolCache.ResPoolCache

import quartz.clients._
import zio.magic._
import zhttp.Request

object QuartzServer extends zio.App {

  def NotNull(s: String): String = if (s == null) "" else s

  val ATTRIBUTES = Seq(
    "uid",
    "cn",
    "mobile",
    "mail",
    "telephonenumber"
  )

  object UserInfo2 {
    def apply(e: SearchResultEntry) = {
      val uid = NotNull(e.getAttributeValue("uid"))
      val cn = NotNull(e.getAttributeValue("cn"))
      val mobile = NotNull(e.getAttributeValue("mobile"))
      val email = NotNull(e.getAttributeValue("mail"))
      val telephoneNumber = NotNull(e.getAttributeValue("telephonenumber"))

      new UserInfo2(
        uid,
        cn,
        mobile,
        telephoneNumber,
        email
      )
    }

    implicit val decoder: JsonDecoder[UserInfo2] =
      DeriveJsonDecoder.gen[UserInfo2]
    implicit val encoder: JsonEncoder[UserInfo2] =
      DeriveJsonEncoder.gen[UserInfo2]
  }

  case class UserInfo2(
      val uid: String,
      val cn: String,
      val mobile: String,
      val telephoneNumber: String,
      val email: String
  )

  object GroupInfo {
    implicit val decoder: JsonDecoder[GroupInfo] =
      DeriveJsonDecoder.gen[GroupInfo]
    implicit val encoder: JsonEncoder[GroupInfo] =
      DeriveJsonEncoder.gen[GroupInfo]

  }

  case class GroupInfo(cn: String)

  def run(args: List[String]) = {

    val LDAP_BASE = "o=company.com"
    val LDAP_GROUP_BASE = "ou=Groups,o=company.com"
    val LDAP_BIND_DN = "uid=sysadm1,ou=Apps,o=company.com"
    val LDAP_BIND_PWD = "password"
    val LDAP_HOST = "hostname"
    val LDAP_PORT = 2636

    val edg_ext_users_route = HttpRoutes.of {

      case GET -> Root / "service" / "users2" / StringVar(uid) =>
        for {

          res <- ResPoolCache.get[String, UserInfo2, HttpConnection](uid)

        } yield (res match {
          case Some(v) => Response.Ok().asJsonBody(v)
          case None    => Response.Error(StatusCode.NotFound)
        })

      case GET -> Root / "service" / "users" / StringVar(uid) / "groups" =>
        for {
          res <- ResPoolCache.get[String, Chunk[GroupInfo], LDAPConnection](uid)
          _ <- MyLogging.trace(
            "ldap",
            s"Groups: for uid=$uid, netries=" + res.getOrElse(Chunk.empty).size
          )

        } yield (res match {
          case Some(v) => Response.Ok().asJsonBody(v)
          case None    => Response.Error(StatusCode.NotFound)
        })

      case GET -> Root / "service" / "users" / StringVar(uid) =>
        for {
          res <- ResPoolCache
            .get[String, Chunk[UserInfo2], LDAPConnection](uid)
          _ <- MyLogging.trace(
            "ldap",
            s"Search: for uid=$uid, netries=" + res.getOrElse(Chunk.empty).size
          )

        } yield (res match {
          case Some(entry) => Response.Ok().asJsonBody(entry(0))
          case None        => Response.Error(StatusCode.NotFound)
        })

      case GET -> Root / "service" / "stat" =>
        ResPoolCache
          .info[String, Chunk[UserInfo2], LDAPConnection]
          .map(Response.Ok().asTextBody(_))

      case GET -> Root / "service" / "stat2" =>
        ResPoolCache
          .info[String, Chunk[GroupInfo], LDAPConnection]
          .map(Response.Ok().asTextBody(_))

      case GET -> Root / "service" / "health" =>
        ZIO(Response.Ok().asTextBody("Health Check Ok"))

    }

    type ResPoolUserCache =
      ResPoolCache[String, Chunk[UserInfo2], LDAPConnection]
    type ResPoolGroupCache =
      ResPoolCache[String, Chunk[GroupInfo], LDAPConnection]

    type MyEnv = MyLogging
      with ResPool[LDAPConnection]
      with ResPoolUserCache
      with ResPoolGroupCache
      with ResPool[HttpConnection]
      with ResPoolCache[String, UserInfo2, HttpConnection]

    val quartz_http = new TLSServer[MyEnv](8443)
    val myHttpRouter = new HttpRouter[MyEnv](edg_ext_users_route)

    val ldap_con1 =
      new AsyncLDAP(LDAP_HOST, LDAP_PORT, LDAP_BIND_DN, LDAP_BIND_PWD)

    //Layers
    val logger =
      MyLogging.make(
        ("console" -> LogLevel.Trace),
        ("access" -> LogLevel.Info),
        ("ldap" -> LogLevel.Trace)
      )

    val ldap_con_pool =
      ResPool.make[LDAPConnection](
        timeToLiveMs = 20 * 1000,
        ldap_con1.ldap_con_ssl,
        ldap_con1.ldap_con_close
      )

    val ldap_mem_cache_uid = ResPoolCache.make(
      timeToLiveMs = 300 * 1000,
      limit = 100000,
      (c: LDAPConnection, uid: String) => {
        for {
          res <- ldap_con1.a_search(c, LDAP_BASE, s"uid=$uid", ATTRIBUTES: _*)
          res2 <- ZIO(res.map(UserInfo2(_)))
          out <- if (res.size > 0) ZIO.some(res2) else ZIO.none
        } yield (out)

      }
    )

    val ldap_mem_cache_groups = ResPoolCache.make(
      timeToLiveMs = 300 * 1000,
      limit = 100000,
      (c: LDAPConnection, uid: String) => {
        for {
          res <- ldap_con1.a_search(c, LDAP_BASE, s"uid=$uid", ATTRIBUTES: _*)
          optional_dn <- ZIO(res.headOption.map(_.getDN()))
          optional_res <- ZIO.foreach(optional_dn)(dn =>
            ldap_con1.a_search(c, LDAP_GROUP_BASE, s"uniquemember=$dn", "cn")
          )
          optional_cns <- ZIO.foreach(optional_res)(sr =>
            ZIO(sr.map(c => GroupInfo(c.getAttributeValue("cn"))))
          )
        } yield (optional_cns)
      }
    )

    val http_con_pool = ResPool.makeM[HttpConnection](
      timeToLiveMs = 1000,
      () =>
        HttpConnection
          .connect("https://localhost:8443", "keystore.jks", "password"),
      _.close
    )

    val http_mem_cache = ResPoolCache.make(
      timeToLiveMs = 100 * 1000,
      limit = 100000,
      (c: HttpConnection, uid: String) => {
        for {
          response <- c.send(ClientRequest(GET, s"/service/users/$uid"))
          res <-
            if (response.code.isSuccess) //will be fixed in m5
              ZIO.some(response.asObjfromJSON[UserInfo2])
            else ZIO.none

        } yield (res)
      }
    )

    /* without ZIO magic
    val log       = ZEnv.live >>> logger
    val ldap_con  = ldap_con_pool

    val http_con     =   ZEnv.live >>> logger ++ ZEnv.live  >>> http_con_pool
    val ldap_mem_uid =  (ZEnv.live >>> logger ++ ldap_con_pool ++ ZEnv.live ) >>> ldap_mem_cache_uid
    val ldap_mem_grp =  (ZEnv.live >>> logger ++ ldap_con_pool ++ ZEnv.live ) >>> ldap_mem_cache_groups
    val http_mem_uid =  (ZEnv.live >>> logger ++ ZEnv.live ++ logger >>> http_con_pool ++ ZEnv.live ++ logger ) >>> http_mem_cache

    val res = ZEnv.live ++ log ++ ldap_mem_uid ++ ldap_mem_grp ++ http_mem_uid ++ ldap_con_pool ++ http_con

    quartz_http.run( myHttpRouter.route ).provideLayer( res ).exitCode
     */

    quartz_http
      .run(myHttpRouter.route)
      .provideMagicLayer(
        ZEnv.live,
        logger,
        ldap_con_pool,
        ldap_mem_cache_uid,
        ldap_mem_cache_groups,
        http_con_pool,
        http_mem_cache
      )
      .exitCode

  }
}
