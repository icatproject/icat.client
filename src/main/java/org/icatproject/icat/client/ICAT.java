package org.icatproject.icat.client;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.json.Json;
import javax.json.JsonException;
import javax.json.JsonReader;
import javax.json.JsonString;
import javax.json.JsonValue;
import javax.json.stream.JsonGenerator;
import javax.json.stream.JsonParser;
import javax.json.stream.JsonParser.Event;
import javax.json.stream.JsonParsingException;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.StatusLine;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.entity.mime.content.InputStreamBody;
import org.apache.http.entity.mime.content.StringBody;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.apache.lucene.document.DateTools;
import org.apache.lucene.document.DateTools.Resolution;
import org.icatproject.icat.client.IcatException.IcatExceptionType;
import org.icatproject.icat.client.Session.Attributes;
import org.icatproject.icat.client.Session.DuplicateAction;

/** Represents a RESTful ICAT instance from which sessions may be obtained. */
public class ICAT {

	private static final String basePath = "/icat";

	private URI uri;

	/**
	 * Create a RESTful ICAT instance connected to the server at the specified
	 * URI
	 * 
	 * @param urlString
	 *            The URI of a server in the form https://example.com:443.
	 * 
	 * @throws URISyntaxException
	 *             If the urlString is not a valid URI
	 */
	public ICAT(String urlString) throws URISyntaxException {
		this.uri = new URI(urlString);
	}

	private void checkStatus(HttpResponse response) throws IcatException, IOException {
		StatusLine status = response.getStatusLine();
		if (status == null) {
			throw new IcatException(IcatExceptionType.INTERNAL, "Status line returned is empty");
		}
		int rc = status.getStatusCode();
		if (rc / 100 != 2) {
			HttpEntity entity = response.getEntity();
			String error;
			if (entity == null) {
				throw new IcatException(IcatExceptionType.INTERNAL, "No explanation provided");
			} else {
				error = EntityUtils.toString(entity);
			}
			try (JsonParser parser = Json.createParser(new ByteArrayInputStream(error.getBytes()))) {
				String code = null;
				String message = null;
				String key = "";
				int offset = -1;
				while (parser.hasNext()) {
					JsonParser.Event event = parser.next();
					if (event == Event.KEY_NAME) {
						key = parser.getString();
					} else if (event == Event.VALUE_STRING) {
						if (key.equals("code")) {
							code = parser.getString();
						} else if (key.equals("message")) {
							message = parser.getString();
						}
					} else if (event == Event.VALUE_NUMBER) {
						if (key.equals("offset")) {
							offset = parser.getInt();
						}
					}
				}

				if (code == null || message == null) {
					throw new IcatException(IcatExceptionType.INTERNAL, error);
				}
				throw new IcatException(IcatExceptionType.valueOf(code), message, offset);
			} catch (JsonParsingException e) {
				throw new IcatException(IcatExceptionType.INTERNAL, error);
			}
		}

	}

	List<Long> write(String sessionId, String entities) throws IcatException {
		URI uri = getUri(getUriBuilder("entityManager"));
		List<NameValuePair> formparams = new ArrayList<>();
		formparams.add(new BasicNameValuePair("sessionId", sessionId));
		formparams.add(new BasicNameValuePair("entities", entities));
		try (CloseableHttpClient httpclient = HttpClients.createDefault()) {
			HttpPost httpPost = new HttpPost(uri);
			httpPost.setEntity(new UrlEncodedFormEntity(formparams));
			List<Long> result = new ArrayList<>();
			try (CloseableHttpResponse response = httpclient.execute(httpPost)) {
				checkStatus(response);
				try (JsonParser parser = Json.createParser(new ByteArrayInputStream(getString(response).getBytes()))) {
					JsonParser.Event event = parser.next();
					if (event != Event.START_ARRAY) {
						throw new IcatException(IcatExceptionType.INTERNAL, "Not a valid JSON array of longs");
					}
					while (parser.hasNext()) {
						event = parser.next();
						if (event == Event.VALUE_NUMBER) {
							result.add(parser.getLong());
						} else if (event == Event.END_ARRAY) {
							return result;
						}
					}
					throw new IcatException(IcatExceptionType.INTERNAL, "Not a valid JSON array of longs");
				}
			}
		} catch (IOException e) {
			throw new IcatException(IcatExceptionType.INTERNAL, e.getClass() + " " + e.getMessage());
		}
	}

	void delete(String sessionId, String entities) throws IcatException {
		URIBuilder uriBuilder = getUriBuilder("entityManager");
		uriBuilder.setParameter("sessionId", sessionId);
		uriBuilder.setParameter("entities", entities);
		URI uri = getUri(uriBuilder);
		try (CloseableHttpClient httpclient = HttpClients.createDefault()) {
			HttpDelete httpDelete = new HttpDelete(uri);
			try (CloseableHttpResponse response = httpclient.execute(httpDelete)) {
				expectNothing(response);
			}
		} catch (IOException e) {
			throw new IcatException(IcatExceptionType.INTERNAL, e.getClass() + " " + e.getMessage());
		}
	}

	private void expectNothing(CloseableHttpResponse response) throws IcatException, IOException {
		checkStatus(response);
		HttpEntity entity = response.getEntity();
		if (entity != null) {
			String error = EntityUtils.toString(entity);
			if (!error.isEmpty()) {
				try (JsonParser parser = Json.createParser(new ByteArrayInputStream(error.getBytes()))) {
					throw new IcatException(IcatExceptionType.INTERNAL, "No http entity expected in response " + error);
				}
			}
		}
	}

	InputStream exportMetaData(String sessionId, String query, Attributes attributes) throws IcatException {

		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		JsonGenerator gen = Json.createGenerator(baos);
		gen.writeStartObject().write("sessionId", sessionId);
		if (query != null) {
			gen.write("query", query);
		}
		gen.write("attributes", attributes.name().toLowerCase()).writeEnd().close();

		URIBuilder uriBuilder = getUriBuilder("port");
		uriBuilder.setParameter("json", baos.toString());
		URI uri = getUri(uriBuilder);

		CloseableHttpResponse response = null;
		CloseableHttpClient httpclient = null;
		HttpGet httpGet = new HttpGet(uri);

		boolean closeNeeded = true;
		try {
			httpclient = HttpClients.createDefault();
			response = httpclient.execute(httpGet);
			checkStatus(response);
			closeNeeded = false;
			return new HttpInputStream(httpclient, response);
		} catch (IOException e) {
			throw new IcatException(IcatExceptionType.INTERNAL, e.getClass() + " " + e.getMessage());
		} finally {
			if (closeNeeded && httpclient != null) {
				try {
					if (response != null) {
						try {
							response.close();
						} catch (Exception e) {
							// Ignore it
						}
					}
					httpclient.close();
				} catch (IOException e) {
					// Ignore it
				}
			}
		}

	}

	String get(String sessionId, String query, long id) throws IcatException {
		URIBuilder uriBuilder = getUriBuilder("entityManager");
		uriBuilder.setParameter("sessionId", sessionId);
		uriBuilder.setParameter("query", query);
		uriBuilder.setParameter("id", Long.toString(id));
		URI uri = getUri(uriBuilder);

		try (CloseableHttpClient httpclient = HttpClients.createDefault()) {
			HttpGet httpGet = new HttpGet(uri);
			try (CloseableHttpResponse response = httpclient.execute(httpGet)) {
				return getString(response);
			}
		} catch (IOException e) {
			throw new IcatException(IcatExceptionType.INTERNAL, e.getClass() + " " + e.getMessage());
		}
	}

	private String getStringFromJson(String input, String sought) throws IcatException {
		try (JsonParser parser = Json.createParser(new ByteArrayInputStream(input.getBytes()))) {
			String key = "";
			while (parser.hasNext()) {
				JsonParser.Event event = parser.next();
				if (event == Event.KEY_NAME) {
					key = parser.getString();
				} else if (event == Event.VALUE_STRING || event == Event.VALUE_NUMBER) {
					if (key.equals(sought)) {
						return parser.getString();
					}
				}
			}
			throw new IcatException(IcatExceptionType.INTERNAL, "No " + sought + " in " + input);
		}
	}

	private long getLongFromJson(String input, String sought) throws IcatException {
		try (JsonParser parser = Json.createParser(new ByteArrayInputStream(input.getBytes()))) {
			String key = "";
			while (parser.hasNext()) {
				JsonParser.Event event = parser.next();
				if (event == Event.KEY_NAME) {
					key = parser.getString();
				} else if (event == Event.VALUE_NUMBER) {
					if (key.equals(sought)) {
						return parser.getLong();
					}
				}
			}
			throw new IcatException(IcatExceptionType.INTERNAL, "No " + sought + " in " + input);
		}
	}

	private boolean getBooleanFromJson(String input, String sought) throws IcatException {
		try (JsonParser parser = Json.createParser(new ByteArrayInputStream(input.getBytes()))) {
			String key = "";
			while (parser.hasNext()) {
				JsonParser.Event event = parser.next();
				if (event == Event.KEY_NAME) {
					key = parser.getString();
				} else if (event == Event.VALUE_TRUE) {
					if (key.equals(sought)) {
						return true;
					}
				} else if (event == Event.VALUE_FALSE) {
					if (key.equals(sought)) {
						return false;
					}
				}
			}
			throw new IcatException(IcatExceptionType.INTERNAL, "No " + sought + " in " + input);
		}
	}

	double getRemainingMinutes(String sessionId) throws IcatException {
		URI uri = getUri(getUriBuilder("session/" + sessionId));
		try (CloseableHttpClient httpclient = HttpClients.createDefault()) {
			HttpGet httpGet = new HttpGet(uri);
			try (CloseableHttpResponse response = httpclient.execute(httpGet)) {
				String responseString = getString(response);
				return Double.parseDouble(getStringFromJson(responseString, "remainingMinutes"));
			}
		} catch (IOException | NumberFormatException e) {
			throw new IcatException(IcatExceptionType.INTERNAL, e.getClass() + " " + e.getMessage());
		}
	}

	private String getString(CloseableHttpResponse response) throws IcatException, IOException {
		checkStatus(response);
		HttpEntity entity = response.getEntity();
		if (entity == null) {
			throw new IcatException(IcatExceptionType.INTERNAL, "No http entity returned in response");
		}
		return EntityUtils.toString(entity);
	}

	private URI getUri(URIBuilder uriBuilder) throws IcatException {
		try {
			URI uri = uriBuilder.build();
			if (uri.toString().length() > 2048) {
				throw new IcatException(IcatExceptionType.BAD_PARAMETER,
						"Generated URI is of length " + uri.toString().length() + " which exceeds 2048");
			}
			return uri;
		} catch (URISyntaxException e) {
			throw new IcatException(IcatExceptionType.INTERNAL, e.getClass() + " " + e.getMessage());
		}
	}

	private URIBuilder getUriBuilder(String path) {
		return new URIBuilder(uri).setPath(basePath + "/" + path);
	}

	String getUserName(String sessionId) throws IcatException {
		URI uri = getUri(getUriBuilder("session/" + sessionId));
		try (CloseableHttpClient httpclient = HttpClients.createDefault()) {
			HttpGet httpGet = new HttpGet(uri);
			try (CloseableHttpResponse response = httpclient.execute(httpGet)) {
				String responseString = getString(response);
				return getStringFromJson(responseString, "userName");
			}
		} catch (IOException e) {
			throw new IcatException(IcatExceptionType.INTERNAL, e.getClass() + " " + e.getMessage());
		}
	}

	void importMetaData(String sessionId, Path path, DuplicateAction duplicate, Attributes attributes)
			throws IcatException {

		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		JsonGenerator gen = Json.createGenerator(baos);
		gen.writeStartObject().write("sessionId", sessionId).write("duplicate", duplicate.name().toLowerCase())
				.write("attributes", attributes.name().toLowerCase()).writeEnd().close();

		URI uri = getUri(getUriBuilder("port"));

		try (CloseableHttpClient httpclient = HttpClients.createDefault()) {
			InputStream stream = new BufferedInputStream(Files.newInputStream(path));

			HttpEntity httpEntity = MultipartEntityBuilder.create()
					.addPart("json", new StringBody(baos.toString(), ContentType.TEXT_PLAIN))
					.addPart("file", new InputStreamBody(stream, ContentType.APPLICATION_OCTET_STREAM, "")).build();
			HttpPost httpPost = new HttpPost(uri);
			httpPost.setEntity(httpEntity);
			try (CloseableHttpResponse response = httpclient.execute(httpPost)) {
				expectNothing(response);
			}
		} catch (IOException e) {
			throw new IcatException(IcatExceptionType.INTERNAL, e.getClass() + " " + e.getMessage());
		}

	}

	/**
	 * See whether or not someone is logged in.
	 * 
	 * @param userName
	 *            which must include mnemonic if the authenticator plugin is
	 *            configured to return them.
	 * 
	 * @return true if at least one session exists else false.
	 * 
	 * @throws IcatException
	 *             For various ICAT errors
	 */
	public boolean isLoggedIn(String userName) throws IcatException {
		URI uri = getUri(getUriBuilder("user/" + userName));
		try (CloseableHttpClient httpclient = HttpClients.createDefault()) {
			HttpGet httpGet = new HttpGet(uri);
			try (CloseableHttpResponse response = httpclient.execute(httpGet)) {
				String responseString = getString(response);
				return getBooleanFromJson(responseString, "loggedIn");
			}
		} catch (IOException e) {
			throw new IcatException(IcatExceptionType.INTERNAL, e.getClass() + " " + e.getMessage());
		}
	}

	/**
	 * Login to a RESTful ICAT instance and return a Session
	 * 
	 * @param plugin
	 *            The mnemonic of the authentication plugin
	 * @param credentials
	 *            A map holding credential key/value pairs
	 * @return A RESTful ICAT Session
	 * 
	 * @throws IcatException
	 *             For various ICAT errors
	 */
	public Session login(String plugin, Map<String, String> credentials) throws IcatException {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		JsonGenerator gen = Json.createGenerator(baos);
		gen.writeStartObject().write("plugin", plugin).writeStartArray("credentials");

		for (Entry<String, String> entry : credentials.entrySet()) {
			gen.writeStartObject().write(entry.getKey(), entry.getValue()).writeEnd();
		}
		gen.writeEnd().writeEnd().close();

		URI uri = getUri(getUriBuilder("session"));
		List<NameValuePair> formparams = new ArrayList<>();
		formparams.add(new BasicNameValuePair("json", baos.toString()));
		try (CloseableHttpClient httpclient = HttpClients.createDefault()) {
			HttpPost httpPost = new HttpPost(uri);
			httpPost.setEntity(new UrlEncodedFormEntity(formparams));
			try (CloseableHttpResponse response = httpclient.execute(httpPost)) {
				String responseString = getString(response);
				String sessionId = getStringFromJson(responseString, "sessionId");
				return new Session(this, sessionId);
			}
		} catch (IOException e) {
			throw new IcatException(IcatExceptionType.INTERNAL, e.getClass() + " " + e.getMessage());
		}
	}

	/**
	 * Obtain a session knowing a sessionId.
	 * 
	 * No check is made on the validity of the sessionId.
	 * 
	 * @param sessionId
	 *            the sessionId to hold in the session.
	 * 
	 * @return the new session
	 */
	public Session getSession(String sessionId) {
		return new Session(this, sessionId);
	}

	void logout(String sessionId) throws IcatException {
		URI uri = getUri(getUriBuilder("session/" + sessionId));
		try (CloseableHttpClient httpclient = HttpClients.createDefault()) {
			HttpDelete httpDelete = new HttpDelete(uri);
			try (CloseableHttpResponse response = httpclient.execute(httpDelete)) {
				expectNothing(response);
			}
		} catch (IOException e) {
			throw new IcatException(IcatExceptionType.INTERNAL, e.getClass() + " " + e.getMessage());
		}

	}

	void refresh(String sessionId) throws IcatException {
		URI uri = getUri(getUriBuilder("session/" + sessionId));
		try (CloseableHttpClient httpclient = HttpClients.createDefault()) {
			HttpPut httpPut = new HttpPut(uri);
			try (CloseableHttpResponse response = httpclient.execute(httpPut)) {
				expectNothing(response);
			}
		} catch (IOException e) {
			throw new IcatException(IcatExceptionType.INTERNAL, e.getClass() + " " + e.getMessage());
		}
	}

	String search(String sessionId, String query) throws IcatException {
		URIBuilder uriBuilder = getUriBuilder("entityManager");
		uriBuilder.setParameter("sessionId", sessionId);
		uriBuilder.setParameter("query", query);
		URI uri = getUri(uriBuilder);

		try (CloseableHttpClient httpclient = HttpClients.createDefault()) {
			HttpGet httpGet = new HttpGet(uri);
			try (CloseableHttpResponse response = httpclient.execute(httpGet)) {
				return getString(response);
			}
		} catch (IOException e) {
			throw new IcatException(IcatExceptionType.INTERNAL, e.getClass() + " " + e.getMessage());
		}

	}

	/**
	 * Return the version of the ICAT server
	 * 
	 * @return the version of the ICAT server
	 * 
	 * @throws IcatException
	 *             For various ICAT errors
	 */
	@Deprecated
	public String getApiVersion() throws IcatException {
		URI uri = getUri(getUriBuilder("version"));
		try (CloseableHttpClient httpclient = HttpClients.createDefault()) {
			HttpGet httpGet = new HttpGet(uri);
			try (CloseableHttpResponse response = httpclient.execute(httpGet)) {
				String responseString = getString(response);
				return getStringFromJson(responseString, "version");
			}
		} catch (IOException e) {
			throw new IcatException(IcatExceptionType.INTERNAL, e.getClass() + " " + e.getMessage());
		}
	}

	/**
	 * Return the version of the ICAT server
	 * 
	 * @return the version of the ICAT server
	 * 
	 * @throws IcatException
	 *             For various ICAT errors
	 */
	public String getVersion() throws IcatException {
		URI uri = getUri(getUriBuilder("version"));
		try (CloseableHttpClient httpclient = HttpClients.createDefault()) {
			HttpGet httpGet = new HttpGet(uri);
			try (CloseableHttpResponse response = httpclient.execute(httpGet)) {
				String responseString = getString(response);
				return getStringFromJson(responseString, "version");
			}
		} catch (IOException e) {
			throw new IcatException(IcatExceptionType.INTERNAL, e.getClass() + " " + e.getMessage());
		}
	}

	String searchInvestigations(String sessionId, String user, String text, Date lower, Date upper,
			List<ParameterForLucene> parameters, List<String> samples, String userFullName, int maxResults)
			throws IcatException {
		URIBuilder uriBuilder = getUriBuilder("lucene/data");
		uriBuilder.setParameter("sessionId", sessionId);
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		try (JsonGenerator gen = Json.createGenerator(baos)) {
			gen.writeStartObject();
			gen.write("target", "Investigation");
			if (user != null) {
				gen.write("user", user);
			}
			if (text != null) {
				gen.write("text", text);
			}
			if (lower != null) {
				// TODO Remove DateTools as it is from a Lucene library!
				gen.write("lower", DateTools.dateToString(lower, Resolution.MINUTE));
			}
			if (upper != null) {
				gen.write("upper", DateTools.dateToString(upper, Resolution.MINUTE));
			}
			if (parameters != null && !parameters.isEmpty()) {
				writeParameters(gen, parameters);
			}
			if (samples != null && !samples.isEmpty()) {
				gen.writeStartArray("samples");
				for (String sample : samples) {
					gen.write(sample);
				}
				gen.writeEnd();
			}
			if (userFullName != null) {
				gen.write("userFullName", userFullName);
			}
			gen.writeEnd();
		}

		uriBuilder.setParameter("query", baos.toString());
		uriBuilder.setParameter("maxCount", Integer.toString(maxResults));
		URI uri = getUri(uriBuilder);

		try (CloseableHttpClient httpclient = HttpClients.createDefault()) {
			HttpGet httpGet = new HttpGet(uri);
			try (CloseableHttpResponse response = httpclient.execute(httpGet)) {
				return getString(response);
			}
		} catch (IOException e) {
			throw new IcatException(IcatExceptionType.INTERNAL, e.getClass() + " " + e.getMessage());

		}
	}

	String searchDatasets(String sessionId, String user, String text, Date lower, Date upper,
			List<ParameterForLucene> parameters, int maxResults) throws IcatException {
		URIBuilder uriBuilder = getUriBuilder("lucene/data");
		uriBuilder.setParameter("sessionId", sessionId);
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		try (JsonGenerator gen = Json.createGenerator(baos)) {
			gen.writeStartObject();
			gen.write("target", "Dataset");
			if (user != null) {
				gen.write("user", user);
			}
			if (text != null) {
				gen.write("text", text);
			}
			if (lower != null) {
				gen.write("lower", DateTools.dateToString(lower, Resolution.MINUTE));
			}
			if (upper != null) {
				gen.write("upper", DateTools.dateToString(upper, Resolution.MINUTE));
			}
			if (parameters != null && !parameters.isEmpty()) {
				writeParameters(gen, parameters);
			}
			gen.writeEnd();
		}

		uriBuilder.setParameter("query", baos.toString());
		uriBuilder.setParameter("maxCount", Integer.toString(maxResults));
		URI uri = getUri(uriBuilder);

		try (CloseableHttpClient httpclient = HttpClients.createDefault()) {
			HttpGet httpGet = new HttpGet(uri);
			try (CloseableHttpResponse response = httpclient.execute(httpGet)) {
				return getString(response);
			}
		} catch (IOException e) {
			throw new IcatException(IcatExceptionType.INTERNAL, e.getClass() + " " + e.getMessage());

		}
	}

	private void writeParameters(JsonGenerator gen, List<ParameterForLucene> parameters) {
		gen.writeStartArray("parameters");
		for (ParameterForLucene parameter : parameters) {
			gen.writeStartObject();
			if (parameter.getName() != null) {
				gen.write("name", parameter.getName());
			}
			if (parameter.getUnits() != null) {
				gen.write("units", parameter.getUnits());
			}
			if (parameter.getStringValue() != null) {
				gen.write("stringValue", parameter.getStringValue());
			} else if (parameter.getLowerDateValue() != null && parameter.getUpperDateValue() != null) {
				gen.write("lowerDateValue", DateTools.dateToString(parameter.getLowerDateValue(), Resolution.MINUTE));
				gen.write("upperDateValue", DateTools.dateToString(parameter.getUpperDateValue(), Resolution.MINUTE));
			} else if (parameter.getLowerNumericValue() != null && parameter.getUpperNumericValue() != null) {
				gen.write("lowerNumericValue", parameter.getLowerNumericValue());
				gen.write("upperNumericValue", parameter.getUpperNumericValue());
			}
			gen.writeEnd();
		}
		gen.writeEnd();

	}

	/**
	 * Return a json string of properties.
	 * 
	 * For example (where no white space will be included):
	 * 
	 * <pre>
	 * {"maxEntities":10000,
	 * "lifetimeMinutes":120,
	 * "authenticators":[
	 * {"mnemonic":"db","description":{"keys":[{"name":"username"},{"name":"password","hide":true}]}},
	 * {"mnemonic":"anon","description":{"keys":[]},"admin":true,"friendly":"Anonymous"}
	 * ]}
	 * </pre>
	 * 
	 * which shows the values of maxEntitites and lifetimeMinutes as as well as
	 * the two available authenticators. The first which has a mnemonic of db in
	 * the icat.properties file has two keys, the second of which, the password,
	 * should not be shown. The second authenticator, anon, should only be
	 * exposed to admin users and has a friendly name of "Anonymous". It is the
	 * responsibility of application writers to use these fields as desired.
	 * 
	 * @return the json string
	 * 
	 * @throws IcatException
	 *             For various ICAT errors
	 */
	public String getProperties() throws IcatException {
		URI uri = getUri(getUriBuilder("properties"));
		try (CloseableHttpClient httpclient = HttpClients.createDefault()) {
			HttpGet httpGet = new HttpGet(uri);
			try (CloseableHttpResponse response = httpclient.execute(httpGet)) {
				return getString(response);
			}
		} catch (IOException e) {
			throw new IcatException(IcatExceptionType.INTERNAL, e.getClass() + " " + e.getMessage());
		}
	}

	void luceneClear(String sessionId) throws IcatException {
		URIBuilder uriBuilder = getUriBuilder("lucene/db");
		uriBuilder.setParameter("sessionId", sessionId);
		URI uri = getUri(uriBuilder);

		try (CloseableHttpClient httpclient = HttpClients.createDefault()) {
			HttpDelete httpDelete = new HttpDelete(uri);
			try (CloseableHttpResponse response = httpclient.execute(httpDelete)) {
				expectNothing(response);
			}
		} catch (IOException e) {
			throw new IcatException(IcatExceptionType.INTERNAL, e.getClass() + " " + e.getMessage());
		}
	}

	void luceneCommit(String sessionId) throws IcatException {
		URI uri = getUri(getUriBuilder("lucene/db"));
		List<NameValuePair> formparams = new ArrayList<>();
		formparams.add(new BasicNameValuePair("sessionId", sessionId));

		try (CloseableHttpClient httpclient = HttpClients.createDefault()) {
			HttpPost httpPost = new HttpPost(uri);
			httpPost.setEntity(new UrlEncodedFormEntity(formparams));
			try (CloseableHttpResponse response = httpclient.execute(httpPost)) {
				expectNothing(response);
			}
		} catch (IOException e) {
			throw new IcatException(IcatExceptionType.INTERNAL, e.getClass() + " " + e.getMessage());
		}
	}

	List<String> luceneGetPopulating(String sessionId) throws IcatException {
		URIBuilder uriBuilder = getUriBuilder("lucene/db");
		uriBuilder.setParameter("sessionId", sessionId);
		URI uri = getUri(uriBuilder);
		try (CloseableHttpClient httpclient = HttpClients.createDefault()) {
			HttpGet httpGet = new HttpGet(uri);
			try (CloseableHttpResponse response = httpclient.execute(httpGet)) {
				String result = getString(response);
				List<String> rvalues = new ArrayList<>();
				try (JsonReader jsonReader = Json.createReader(new StringReader(result))) {
					for (JsonValue jv : jsonReader.readArray()) {
						JsonString o = (JsonString) jv;
						rvalues.add(o.getString());
					}
					return rvalues;
				}
			}
		} catch (IOException | JsonException e) {
			throw new IcatException(IcatExceptionType.INTERNAL, e.getClass() + " " + e.getMessage());
		}
	}

	void lucenePopulate(String sessionId, String entityName, long minid) throws IcatException {
		URI uri = getUri(getUriBuilder("lucene/db/" + entityName + "/" + minid));
		List<NameValuePair> formparams = new ArrayList<>();
		formparams.add(new BasicNameValuePair("sessionId", sessionId));

		try (CloseableHttpClient httpclient = HttpClients.createDefault()) {
			HttpPost httpPost = new HttpPost(uri);
			httpPost.setEntity(new UrlEncodedFormEntity(formparams));
			try (CloseableHttpResponse response = httpclient.execute(httpPost)) {
				expectNothing(response);
			}
		} catch (IOException e) {
			throw new IcatException(IcatExceptionType.INTERNAL, e.getClass() + " " + e.getMessage());
		}
	}

	String searchDatafiles(String sessionId, String user, String text, Date lower, Date upper,
			List<ParameterForLucene> parameters, int maxResults) throws IcatException {
		URIBuilder uriBuilder = getUriBuilder("lucene/data");
		uriBuilder.setParameter("sessionId", sessionId);
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		try (JsonGenerator gen = Json.createGenerator(baos)) {
			gen.writeStartObject();
			gen.write("target", "Datafile");
			if (user != null) {
				gen.write("user", user);
			}
			if (text != null) {
				gen.write("text", text);
			}
			if (lower != null) {
				gen.write("lower", DateTools.dateToString(lower, Resolution.MINUTE));
			}
			if (upper != null) {
				gen.write("upper", DateTools.dateToString(upper, Resolution.MINUTE));
			}
			if (parameters != null && !parameters.isEmpty()) {
				writeParameters(gen, parameters);
			}
			gen.writeEnd();
		}

		uriBuilder.setParameter("query", baos.toString());
		uriBuilder.setParameter("maxCount", Integer.toString(maxResults));
		URI uri = getUri(uriBuilder);

		try (CloseableHttpClient httpclient = HttpClients.createDefault()) {
			HttpGet httpGet = new HttpGet(uri);
			try (CloseableHttpResponse response = httpclient.execute(httpGet)) {
				return getString(response);
			}
		} catch (IOException e) {
			throw new IcatException(IcatExceptionType.INTERNAL, e.getClass() + " " + e.getMessage());
		}
	}

	long cloneEntity(String sessionId, String name, long id, Map<String, String> keys) throws IcatException {
		URI uri = getUri(getUriBuilder("cloner"));
		List<NameValuePair> formparams = new ArrayList<>();
		formparams.add(new BasicNameValuePair("sessionId", sessionId));
		formparams.add(new BasicNameValuePair("name", name));
		formparams.add(new BasicNameValuePair("id", Long.toString(id)));
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		try (JsonGenerator gen = Json.createGenerator(baos)) {
			gen.writeStartObject();
			for (Entry<String, String> entry : keys.entrySet()) {
				gen.write(entry.getKey(), entry.getValue());
			}
			gen.writeEnd();
		}
		formparams.add(new BasicNameValuePair("keys", baos.toString()));

		try (CloseableHttpClient httpclient = HttpClients.createDefault()) {
			HttpPost httpPost = new HttpPost(uri);
			httpPost.setEntity(new UrlEncodedFormEntity(formparams));
			try (CloseableHttpResponse response = httpclient.execute(httpPost)) {
				String responseString = getString(response);
				return getLongFromJson(responseString, "id");
			}
		} catch (IOException e) {
			throw new IcatException(IcatExceptionType.INTERNAL, e.getClass() + " " + e.getMessage());
		}

	}

	public void waitMillis(String sessionId, long ms) throws IcatException {
		URI uri = getUri(getUriBuilder("waitMillis"));
		List<NameValuePair> formparams = new ArrayList<>();
		formparams.add(new BasicNameValuePair("sessionId", sessionId));
		formparams.add(new BasicNameValuePair("ms", Long.toString(ms)));
		try (CloseableHttpClient httpclient = HttpClients.createDefault()) {
			HttpPost httpPost = new HttpPost(uri);
			httpPost.setEntity(new UrlEncodedFormEntity(formparams));
			try (CloseableHttpResponse response = httpclient.execute(httpPost)) {
				expectNothing(response);
			}
		} catch (IOException e) {
			throw new IcatException(IcatExceptionType.INTERNAL, e.getClass() + " " + e.getMessage());
		}
	}

	public String list(String sessionId, String path) throws IcatException {
		URIBuilder uriBuilder = getUriBuilder("list");
		uriBuilder.setParameter("sessionId", sessionId);
		uriBuilder.setParameter("path", path);
		URI uri = getUri(uriBuilder);
		try (CloseableHttpClient httpclient = HttpClients.createDefault()) {
			HttpGet httpGet = new HttpGet(uri);
			try (CloseableHttpResponse response = httpclient.execute(httpGet)) {
				return getString(response);
			}
		} catch (IOException e) {
			throw new IcatException(IcatExceptionType.INTERNAL, e.getClass() + " " + e.getMessage());
		}
	}

}
