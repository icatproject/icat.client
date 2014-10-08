/**
RESTFul ICAT Interface.
<p>
To get started instantiate an {@link org.icatproject.icat.client.ICAT} with the URL of an ICAT server 
and call the login method on that ICAT. Subsequently 
make calls upon the {@link org.icatproject.icat.client.Session}.
<p>
For example:
<p>
<code> 
&nbsp;&nbsp;&nbsp;&nbsp;ICAT icat = new ICAT("https://example.com:8181"));<br><br>
&nbsp;&nbsp;&nbsp;&nbsp;Map<String, String> credentials = new HashMap<>();<br>
&nbsp;&nbsp;&nbsp;&nbsp;credentials.put("username", "fred");<br>
&nbsp;&nbsp;&nbsp;&nbsp;credentials.put("password", "secret");<br>
&nbsp;&nbsp;&nbsp;&nbsp;Session session = icat.login("db", credentials);<br><br>
&nbsp;&nbsp;&nbsp;&nbsp;System.out.println(session.getUserName());<br>
&nbsp;&nbsp;&nbsp;&nbsp;System.out.println(session.getRemainingMinutes());<br>
&nbsp;&nbsp;&nbsp;&nbsp;System.out.println(session.search("Facility")<br>
&nbsp;&nbsp;&nbsp;&nbsp;session.logout();<br>
</code>
 */
package org.icatproject.icat.client;

