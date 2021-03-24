# zio-quartz

Enterprise use cases for zio-tls-http server - m5.<br><br>

## Additional dependencies:

The project philosophy is to limit the use third party libs, if anything can be done on regular JDK it will be done on JDK.

* ZIO-Magic
* Ping Unbound LDAP SDK

## Internal components used: 

* LDAP and HTTPS Connection pooling and caching layers, high perfomance, lock-free, massively parallel. 
* Async LDAP access thru Unbound SDK with async ZIO bindings.
* Async, Efectfull(ZIO) TLS HTTPClient.
* Carefully crafted logging for connection pooling/caching to see different Layer of the same class.  For example: ResPool[HttpClient] vs ResPool[LDAPClient] 
( there is a version of conn pool with names, not used in the example )

<i>
Future plans are to provide OAUTH2 client filter example, connection limiter and fast in-memory data table. 
Web filters on zio-tls-http are comoosable with <>
</i>  
  
 ## High memory scenarios.
 
    export SBT_OPTS="-Xmx5G" 
    
 Very rough estimate 200-500 MB per million of entries. To avoid perf issues please make sure you have enough memory for your cache/caches.
 Cache poisioning with 100% LRU eviction will slow down TPS about 60%. Currently, only one fiber doing evictions. This is not an issue for any real scenario.
 
 ## LRU Locks, semaphore table.
 
 Cache will work with millions of entries with all your CPU cores busy, due to LRU tracking there will be a semaphore sync thru semaphore table, between regular and LRU table.
 Each record locked individually with semaphore key calculated( maped ) from main mem cache table:  
 
    semaphore_key <- ZIO(key.hashCode % 1024)  
    
 This will limit semaphore table, agreed that 64 or 128 is enough, this is just to lessen a chance of any key collisions.   
  
 ## ZIO-Quartz Layers:
  
 ### Resource Pool ZIO Layer - connection pooling, you will need to provide two functions: create and release. Can be used with any resources, JDBC, etc..
 
    
    object ResPool
   
    def makeM[R](
      timeToLiveMs: Int,
      createResource: () => ZIO[ZEnv, Exception, R],
      closeResource: (R) => ZIO[ZEnv, Exception, Unit)
      (implicit tagged: Tag[R] )
  
    def make[R](
      timeToLiveMs: Int, 
      createResource: () => R,
      closeResource: (R) => Unit)
      (implicit tagged: Tag[R])
    
    trait Service[R] {
      def acquire: ZIO[zio.ZEnv with MyLogging, Throwable, R]
      def release(res: R): ZIO[ZEnv with MyLogging, Throwable, Unit]
    }
    
 ### Resource Pool Cache ZIO Layer.
 
 TBD
    
 ### Dummy layers to test cache with random number genertor ( for those who interested )   
    
    //Layers
    val logger_L = MyLogging.make(("console" -> LogLevel.Trace), ("access" -> LogLevel.Info))
    val dummyConPool_L = ResPool.make[Unit](timeToLiveMs = 20 * 1000, () => (), (Unit) => ())
    val cache_L =
      ResPoolCache.make(timeToLiveMs = 10 * 1000, limit = 4000001, (u: Unit, number: String) 
                   => ZIO.succeed( if ( false ) None else Some(number ) ))

    //all layers visibe
    edgz_Http
      .run(myHttpRouter.route)
      .provideSomeLayer[ZEnv with MyLogging with ResPool[Unit]](cache_L)
      .provideSomeLayer[ZEnv with MyLogging](dummyConPool_L)
      .provideSomeLayer[ZEnv](logger_L)
      .exitCode
  }

    
