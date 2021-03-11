package example

import zio.App
import zio.ZEnv
import zio.ZIO
import zio.json._

import zhttp.TcpServer
import zhttp.HttpRouter
import zhttp.MyLogging.MyLogging
import zhttp.MyLogging
import zhttp.LogLevel
import zhttp.HttpRoutes
import zhttp.dsl._
import zhttp.Response
import zhttp.Method._


object UserRecord {
  implicit val decoder: JsonDecoder[UserRecord] = DeriveJsonDecoder.gen[UserRecord]
  implicit val encoder: JsonEncoder[UserRecord] = DeriveJsonEncoder.gen[UserRecord]
}
case class UserRecord(val uid: String )

//Please see URL, for more examples/use cases.
//https://github.com/ollls/zio-tls-http/blob/dev/examples/start/src/main/scala/MyServer.scala

object ServerExample extends zio.App {

  def run(args: List[String]) = {


    val r =  HttpRoutes.of {
       case GET -> Root / "health" =>
        ZIO(Response.Ok.asTextBody("Health Check Ok"))

       case req @ POST -> Root / "test" =>
         for {
           rec <- ZIO( req.fromJSON[UserRecord] )
           _   <- MyLogging.info("my_application", "UID received: " + rec.uid )
         } yield( Response.Ok.asTextBody( "OK " + rec.uid ) )
    }



    type MyEnv = MyLogging

    val myHttp = new TcpServer[MyEnv]

    val myHttpRouter = new HttpRouter[MyEnv]
    
    myHttpRouter.addAppRoute( r )

    myHttp.BINDING_SERVER_IP = "0.0.0.0"
    myHttp.KEEP_ALIVE = 2000
    myHttp.SERVER_PORT = 8080


    val logger_L = MyLogging.make( ("console" -> LogLevel.Trace), 
                                   ("access" -> LogLevel.Info), 
                                   ( "my_application" -> LogLevel.Info) )

    myHttp.run( myHttpRouter.route ).provideSomeLayer[ZEnv](logger_L).exitCode


  }
}
