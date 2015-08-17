package org.icatproject.icat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;
import java.util.HashMap;
import java.util.Map;

import javax.json.Json;
import javax.json.JsonObject;

import org.icatproject.icat.client.ICAT;
import org.icatproject.icat.client.IcatException;
import org.icatproject.icat.client.IcatException.IcatExceptionType;
import org.icatproject.icat.client.Session;
import org.junit.Test;

/* Note that this is not in the same package as the code being tested as these tests are to be 
 * run on a deployed icat.server as a sanity check. */
public class TestRS {

	@Test
	public void testSession() throws Exception {
		System.out.println(System.getProperty("serverUrl"));
		ICAT icat = new ICAT(System.getProperty("serverUrl"));
		Map<String, String> credentials = new HashMap<>();
		credentials.put("username", "notroot");
		credentials.put("password", "password");
		Session session = icat.login("db", credentials);
		assertEquals("db/notroot", session.getUserName());
		double remainingMinutes = session.getRemainingMinutes();
		assertTrue(remainingMinutes > 119 && remainingMinutes < 120);
		session.logout();
		try {
			session.getRemainingMinutes();
			fail();
		} catch (IcatException e) {
			assertEquals(IcatExceptionType.SESSION, e.getType());
		}
		session = icat.login("db", credentials);
		Thread.sleep(1000);
		remainingMinutes = session.getRemainingMinutes();
		session.refresh();
		assertTrue(session.getRemainingMinutes() > remainingMinutes);

		System.out.println(session.search("Facility.id"));

		long fid = Json.createReader(new ByteArrayInputStream(session.search("Facility.id").getBytes())).readArray()
				.getJsonNumber(0).longValueExact();

		JsonObject fac = Json.createReader(new ByteArrayInputStream(session.get("Facility", fid).getBytes()))
				.readObject().getJsonObject("Facility");

		System.out.println(icat.getApiVersion() + " " + fac.getString("name"));

		System.out.println("Properties + " + icat.getProperties());

	}

}
