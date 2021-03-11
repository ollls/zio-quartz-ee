package example

import zio.App
import zio.ZEnv
import zio.ZIO
import zhttp.TcpServer
import zhttp.HttpRouter
import zhttp.MyLogging.MyLogging
import zhttp.MyLogging
import zhttp.LogLevel
import zhttp.HttpRoutes
import zhttp.dsl._
import zhttp.Response
import zhttp.Method._

object ServerExample extends zio.App {

  def run(args: List[String]) = {


    val r =  HttpRoutes.of {
       case GET -> Root / "health" =>
        ZIO(Response.Ok.asTextBody("Health Check Ok"))
    }

    type MyEnv = MyLogging

    val myHttp = new TcpServer[MyEnv]

    val myHttpRouter = new HttpRouter[MyEnv]
    
    myHttpRouter.addAppRoute( r )

    myHttp.BINDING_SERVER_IP = "0.0.0.0"
    myHttp.KEEP_ALIVE = 2000
    myHttp.SERVER_PORT = 8080


    val logger_L = MyLogging.make(("console" -> LogLevel.Trace), ("access" -> LogLevel.Info))

    myHttp.run( myHttpRouter.route ).provideSomeLayer[ZEnv](logger_L).exitCode


  }
}
