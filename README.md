# zio-quartz

Enterprise use cases for zio-tls-http server.<br><br>

## Additional dependencies:

* ZIO-Magic
* Ping Unbound LDAP SDK

## Internal components used: 

* LDAP and HTTPS Connection pooling and caching layers. 
* LDAP access thru Unbound SDK with async ZIO bindings.
* Carefully crafted logging for connection pooling/caching to see different Layer of the same class.  For example: ResPool[HttpClient] vs ResPool[LDAPClient]

<i>
Future plans are to provide OAUTH2 client filter example, connection limiter and fast in-memory data table. 
Web filters on zio-tls-http are comoosable with <>
</i>  
