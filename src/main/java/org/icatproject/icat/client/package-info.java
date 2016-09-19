/**
 * RESTFul ICAT Interface.
 * <p>
 * To get started instantiate an {@link org.icatproject.icat.client.ICAT} with
 * the URL of an ICAT server and call the login method on that ICAT.
 * Subsequently make calls upon the {@link org.icatproject.icat.client.Session}.
 * <p>
 * For example:
 * <p>
 * <code> 
&nbsp;&nbsp;&nbsp;&nbsp;ICAT icat = new ICAT("https://example.com:8181"));<br><br>
&nbsp;&nbsp;&nbsp;&nbsp;Map&lt;String, String&gt; credentials = new HashMap&lt;&gt;();<br>
&nbsp;&nbsp;&nbsp;&nbsp;credentials.put("username", "fred");<br>
&nbsp;&nbsp;&nbsp;&nbsp;credentials.put("password", "secret");<br>
&nbsp;&nbsp;&nbsp;&nbsp;Session session = icat.login("db", credentials);<br><br>
&nbsp;&nbsp;&nbsp;&nbsp;System.out.println(session.getUserName());<br>
&nbsp;&nbsp;&nbsp;&nbsp;System.out.println(session.getRemainingMinutes());<br>
&nbsp;&nbsp;&nbsp;&nbsp;System.out.println(session.search("Facility");<br>
&nbsp;&nbsp;&nbsp;&nbsp;session.logout();<br>
</code>
 * <p>
 * The code makes us of javax.json which is part of Java EE 7 and is not in the
 * Java SE so you will need to include an extra jar. For example add to your
 * pom:
 * </p>
 * <code> 
&nbsp;&nbsp;&nbsp;&lt;dependency&gt; <br>
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&lt;groupId&gt;org.glassfish&lt;/groupId&gt;<br>
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&lt;artifactId&gt;javax.json&lt;/artifactId&gt;<br>
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&lt;version&gt;1.0.4&lt;/version&gt;<br>
&nbsp;&nbsp;&nbsp;&lt;/dependency&gt;<br>
</code>
 */
package org.icatproject.icat.client;
