# zio-quartz

Enterprise use cases for zio-tls-http server - m5.<br><br>

## Additional dependencies:

* ZIO-Magic
* Ping Unbound LDAP SDK

## Internal components used: 

* LDAP and HTTPS Connection pooling and caching layers. 
* LDAP access thru Unbound SDK with async ZIO bindings.
* Carefully crafted logging for connection pooling/caching to see different Layer of the same class.  For example: ResPool[HttpClient] vs ResPool[LDAPClient] 
( there is a version of conn pool with names, not used in the example )

<i>
Future plans are to provide OAUTH2 client filter example, connection limiter and fast in-memory data table. 
Web filters on zio-tls-http are comoosable with <>
</i>  
  
 ## ZIO-Quartz Layers:
  
 ### ResPool ZIO Layer - connection pooling, you will need to provide two functions: create and release. Can be used with any resources, JDBC, etc..
   
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
    
    
    
