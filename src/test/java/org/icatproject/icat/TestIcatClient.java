package org.icatproject.icat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import javax.json.Json;
import javax.json.JsonNumber;
import javax.json.JsonValue;
import javax.json.stream.JsonGenerator;

import org.icatproject.icat.client.ICAT;
import org.icatproject.icat.client.IcatException;
import org.icatproject.icat.client.IcatException.IcatExceptionType;
import org.icatproject.icat.client.Session;
import org.junit.Before;
import org.junit.Test;

/* 
 * Note that this is not in the same package as the code being tested as these tests are to be 
 * run on a deployed icat.server as a sanity check. The REST API has comprehensive tests in the icat.server
 */

public class TestIcatClient {

	private ICAT icat;
	private Session session;

	@Before
	public void setup() throws Exception {
		icat = new ICAT(System.getProperty("serverUrl"));
		Map<String, String> credentials = new HashMap<>();
		credentials.put("username", "root");
		credentials.put("password", "password");
		session = icat.login("db", credentials);

		for (String et : Arrays.asList("Facility", "DataCollection", "Study", "Rule", "User", "Grouping",
				"PublicStep")) {
			for (JsonValue o : Json
					.createReader(new ByteArrayInputStream(session.search("SELECT x.id from " + et + " x").getBytes()))
					.readArray()) {
				long xid = ((JsonNumber) o).longValueExact();

				ByteArrayOutputStream baos = new ByteArrayOutputStream();
				try (JsonGenerator jw = Json.createGenerator(baos)) {
					jw.writeStartObject().writeStartObject(et).write("id", xid).writeEnd().writeEnd();
				}
				session.delete(baos.toString());
			}
		}

		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		try (JsonGenerator jw = Json.createGenerator(baos)) {

			jw.writeStartObject().writeStartObject("Facility").write("name", "Test Facility").writeEnd().writeEnd();

		}
		session.write(baos.toString()).get(0);

	}

	@Test
	public void testGet() throws Exception {
		long fid = Json.createReader(new ByteArrayInputStream(session.search("Facility.id").getBytes())).readArray()
				.getJsonNumber(0).longValueExact();

		Json.createReader(new ByteArrayInputStream(session.get("Facility", fid).getBytes())).readObject()
				.getJsonObject("Facility");
	}

	@Test
	public void testSession() throws Exception {
		ICAT icat = new ICAT(System.getProperty("serverUrl"));
		assertFalse(icat.isLoggedIn("mnemonic/rubbish"));
		assertFalse(icat.isLoggedIn("rubbish"));
		Map<String, String> credentials = new HashMap<>();
		credentials.put("username", "notroot");
		credentials.put("password", "password");
		Session session = icat.login("db", credentials);
		assertEquals("db/notroot", session.getUserName());
		double remainingMinutes = session.getRemainingMinutes();
		assertTrue(remainingMinutes > 119 && remainingMinutes < 120);
		assertTrue(icat.isLoggedIn("db/notroot"));
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

	}

	@Test
	public void testInfo() throws Exception {
		assertTrue(icat.getVersion().startsWith("5.0."));
	}

}
