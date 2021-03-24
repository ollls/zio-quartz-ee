# zio-quartz

Enterprise use cases for zio-tls-http server - m5.<br><br>

## Additional dependencies:

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
 
 ## LRU Locks, semaphore table.
 
 Cache will work with millions of entries with all your CPU cores busy, due to LRU tracking there will be a sempahore sync thru semaphore table, between regular and LRU table.
 Each record locked indivudualy with semaphore key calculated from main mem cache table:  
 
    semaphore_key <- ZIO(key.hashCode % 1024)  
    
 This will limit semaphore table, agreed that 64 or 128 is enough, this is just to lessen a chance of any collisions.   
  
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
    
    TBD ...
    
