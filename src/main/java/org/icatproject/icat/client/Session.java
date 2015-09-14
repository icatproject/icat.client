package org.icatproject.icat.client;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.Date;
import java.util.List;

/**
 * A RESTful ICAT session.
 * <p>
 * The exportMetaData and ImportMetaData call make use of a special format to
 * represent ICAT data efficiently. The file may contain line starting with a #
 * sign. The first non-comment line contains the version number of the file
 * format with major and minor parts. Each entity type is preceded by a blank
 * line line followed by a one line entity descriptor and then a line for each
 * entity of that type.
 * <p>
 * For example:
 * 
 * <pre>
 * #  Version of file format
 * 1.0
 * 
 * Facility ( name:0, daysUntilRelease:1, createId:2, createTime:3)
 * "Test port facility", 90, "Zorro", 1920-05-16T16:58:26.12Z
 * 
 * InvestigationType (facility(name:0), name:1)
 * "Test port facility", "atype"
 * "Test port facility", "btype"
 * 
 * Investigation(facility(name:0), name:1, visitId:2, type(facility(name:0), name:3), title:4)
 * "Test port facility", "expt1", "one", "atype", "a title"
 * </pre>
 * <p>
 * The entity descriptor starts with the name of the entity type followed by a
 * comma separated list attribute of field names held inside parentheses. It is
 * not necessary to include those which you don't wish to set as any that are
 * not present and are allowed to be null will be set to null when importing. So
 * we see that this file will create a Facility with fields: name,
 * daysUntilRelease, createId and createTime. Following the field name is a
 * colon and an integer showing the offset to the data field in each of the next
 * set of rows. So a facility will be created with a name of
 * "Test port facility" and with 90 daysUntilRelease. All strings must be
 * enclosed in double quotes; to represent a a double quote within the string
 * then it must be escaped with a back slash: \". The following escape sequences
 * are available:
 * </p>
 * <ul>
 * <li>\t : tab</li>
 * <li>\r : carriage return</li>
 * <li>\f : form feed</li>
 * <li>\b : bell</li>
 * <li>\n : new line</li>
 * <li>\" : "</li>
 * <li>\' : ' (Not really needed)</li>
 * <li>\\ : \</li>
 * </ul>
 * <p>
 * True, false and null literals are not case sensitive. The last two fields of
 * the facility are createId and createTime. If you specify that you want all
 * attributes and you are a "root user" then the values of createId and
 * createTime will be respected otherwise the current time is used and the id is
 * that of the user doing the import. Timestamp literals follow ISO 8601 and
 * support fractional seconds and time zones. If the time zone is omitted it is
 * interpreted as local time.
 * <p>
 * Now consider the InvestigationType for which we need to specify the facility
 * to which it belongs and its name. The facility cannot be described by its id
 * because we don't know what it is. So instead we list in parentheses the field
 * names that define it. So name:0 is the name of the facility and name:1 is the
 * name of the InvestigationType.
 * <p>
 * The next line shows the convenience of this syntax. The investigation has a
 * facility (identified by its name:0) and the name:1 of the investigation and
 * the visitId but it also has a type which is identified a facility (identified
 * by its name:0) and by the name:3 of the type. Finally it has a title:4 field.
 * Note that name:0 is used twice as in this case the investigation belongs to
 * the same facility as its type. This works fine as long as we deal with entity
 * types which have key fields. This is shown in the next snippet from an import
 * file:
 * 
 * <pre>
 * DataCollection(?:0)
 * "a"
 * "b"
 * "c"
 * 
 * DataCollectionDatafile(datafile(dataset(investigation(facility(name:0), name:1, visitId:2), name:3), name:4), dataCollection(?:5))
 * "Test port facility", "expt1", "one", "ds1", "df1",  "a"
 * "Test port facility", "expt1", "one", "ds1", "df2",  "b"
 * 
 * Job(application(facility(name:0), name:1, version:2), inputDataCollection(?:3), outputDataCollection(?:4))
 * "Test port facility", "aprog", "1.2.3", "a", "b"
 * </pre>
 * 
 * Here we have the DataCollection which we imagine to be identified by the
 * anonymous variable "?". This section of the file will create three
 * DataCollection entries which we shall remember for the duration of the import
 * process as "a", "b" and "c".
 * <p>
 * DataCollectionDatafiles are then associated with DataCollections "a" and "b"
 * and a job is created with one DataCollection as input and one as output.
 * <p>
 * When performing export the same format is used however some values will be
 * repeated - for example the facility name will appear many times in most rows.
 */
public class Session {

	/** Control the attributes to be imported or exported */
	public enum Attributes {
		/** Include createId etc */
		ALL,

		/** Only import/export attributes which may normally be set by the user */
		USER
	}

	/** Control the action when a duplicate entry is encountered on import */
	public enum DuplicateAction {
		/** Check that new data matches the old */
		CHECK,

		/** Don't check just go to the next row */
		IGNORE,

		/** Replace old data with new */
		OVERWRITE,

		/** Throw an exception */
		THROW
	}

	private ICAT icat;
	private String sessionId;

	Session(ICAT icat, String sessionId) {
		this.icat = icat;
		this.sessionId = sessionId;
	}

	/**
	 * Create ICAT entities from a Json String.
	 * 
	 * Note that this call is experimental and should not be relied upon to
	 * continue in its present form.
	 * 
	 * @param entities
	 *            Json representation of ICAT entities and their related
	 *            entities
	 * 
	 * @return the ids of the top level entities created
	 * 
	 * @throws IcatException
	 *             For various ICAT errors
	 */
	public List<Long> create(String entities) throws IcatException {
		return icat.create(sessionId, entities);
	}

	/**
	 * Export all metadata from ICAT.
	 * 
	 * @param attributes
	 *            which attributes to export. If you don't plan to
	 *            importMetaData as a "root user" there is no point in using
	 *            {@link Attributes#ALL} and {@link Attributes#USER} is to be
	 *            preferred.
	 * 
	 * @return an OutputStream. The structure of the OutputStream is described
	 *         at {@link Session}
	 * 
	 * @throws IcatException
	 *             For various ICAT errors
	 */
	public InputStream exportMetaData(Attributes attributes) throws IcatException {
		return icat.exportMetaData(sessionId, null, attributes);
	}

	/**
	 * Export metadata from ICAT as specified in the query
	 * 
	 * @param query
	 *            a normal ICAT query which may have an INCLUDE clause. This is
	 *            used to define the metadata to export.
	 * @param attributes
	 *            which attributes to export. If you don't plan to
	 *            importMetaData as a "root user" there is no point in using
	 *            {@link Attributes#ALL} and {@link Attributes#USER} is to be
	 *            preferred.
	 * 
	 * @return an OutputStream. The structure of the OutputStream is described
	 *         at {@link Session}
	 * 
	 * @throws IcatException
	 *             For various ICAT errors
	 */
	public InputStream exportMetaData(String query, Attributes attributes) throws IcatException {
		return icat.exportMetaData(sessionId, query, attributes);
	}

	/**
	 * Return the time remaining in the session in minutes
	 * 
	 * @return the time remaining
	 * 
	 * @throws IcatException
	 *             For various ICAT errors
	 */
	public double getRemainingMinutes() throws IcatException {
		return icat.getRemainingMinutes(sessionId);
	}

	/**
	 * Return the sessionId
	 * 
	 * @return the sessionId
	 */
	public String getId() {
		return sessionId;
	}

	/**
	 * Return the user name corresponding to the session.
	 * 
	 * @return the user name
	 * 
	 * @throws IcatException
	 *             For various ICAT errors
	 */
	public String getUserName() throws IcatException {
		return icat.getUserName(sessionId);
	}

	/**
	 * Import metadata into ICAT for a file specified by a Path
	 * 
	 * @param path
	 *            the path of the import file. The structure of the import file
	 *            is described at {@link Session}
	 * @param duplicateAction
	 *            what to do when a duplicate is encountered
	 * @param attributes
	 *            which attributes to import. Only a "root user" can specify
	 *            {@link Attributes#ALL} to respect those fields specified in
	 *            the import file which are not settable by normal users:
	 *            createId, createTime, modId and modTime. This is to allow an
	 *            ICAT to be accurately exported and imported.
	 * 
	 * @throws IcatException
	 *             For various ICAT errors
	 */
	public void importMetaData(Path path, DuplicateAction duplicateAction, Attributes attributes) throws IcatException {
		icat.importMetaData(sessionId, path, duplicateAction, attributes);
	}

	/**
	 * Logout of the session after which the session cannot be re-used
	 * 
	 * @throws IcatException
	 *             For various ICAT errors
	 */
	public void logout() throws IcatException {
		icat.logout(sessionId);
	}

	/**
	 * Refresh the session and thereby reset the time remaining
	 * 
	 * @throws IcatException
	 *             For various ICAT errors
	 */
	public void refresh() throws IcatException {
		icat.refresh(sessionId);
	}

	/**
	 * Carry out an ICAT search. The data are returned as a Json string
	 * 
	 * Note that this call is experimental and should not be relied upon to
	 * continue in its present form.
	 * 
	 * @param query
	 *            a normal ICAT query with optional INCLUDE and LIMIT clauses.
	 * 
	 * @return the Json holding the results
	 * 
	 * @throws IcatException
	 *             For various ICAT errors
	 */
	public String search(String query) throws IcatException {
		return icat.search(sessionId, query);
	}

	/**
	 * Carry out an ICAT get. The data are returned as a Json string
	 * 
	 * Note that this call is experimental and should not be relied upon to
	 * continue in its present form.
	 * 
	 * @param query
	 *            a normal ICAT get query with an optional INCLUDE clause.
	 * @param id
	 *            the id of the entity to be returned
	 * 
	 * @return the Json holding the result
	 * 
	 * @throws IcatException
	 *             For various ICAT errors
	 */
	public String get(String query, long id) throws IcatException {
		return icat.get(sessionId, query, id);
	}

	/**
	 * Return a set of investigations satisfying the constraints
	 * 
	 * @param user
	 *            If not null must exactly match the name of a user related via
	 *            the investigation user to the investigation.
	 * @param text
	 *            If not null a text search (with ANDs ORs etc) for any text in
	 *            the investigation fields.
	 * @param lower
	 *            If not null investigation end date must be greater than or
	 *            equal to this.
	 * @param upper
	 *            If not null investigation start date must be less than or equal
	 *            to this.
	 * @param parameters
	 *            If not null all the parameters must match.
	 * @param samples
	 *            If not null all the specified samples, using a text search
	 *            (with ANDs ORs etc) must be related to the investigation.
	 * @param userFullName
	 *            If not null a text search is made against the full name of a
	 *            user related via the investigation user to the investigation.
	 * @param maxResults
	 *            The maximum number of results to return.
	 * 
	 * @return the Json holding the result.
	 * 
	 * @throws IcatException
	 *             For various ICAT errors.
	 */
	public String searchInvestigations(String user, String text, Date lower, Date upper,
			List<ParameterForLucene> parameters, List<String> samples, String userFullName, int maxResults)
			throws IcatException {
		return icat.searchInvestigations(sessionId, user, text, lower, upper, parameters, samples, userFullName,
				maxResults);
	}
	
	/**
	 * Return a set of datasets satisfying the constraints
	 * 
	 * @param user
	 *            If not null must exactly match the name of a user related via
	 *            the investigation user and the investigation to the data set.
	 * @param text
	 *            If not null a text search (with ANDs ORs etc) for any text in
	 *            the data set fields.
	 * @param lower
	 *            If not null data set end date must be greater than or
	 *            equal to this.
	 * @param upper
	 *            If not null data set start date must be less than or equal
	 *            to this.
	 * @param parameters
	 *            If not null all the parameters must match.
	 * @param maxResults
	 *            The maximum number of results to return.
	 * 
	 * @return the Json holding the result.
	 * 
	 * @throws IcatException
	 *             For various ICAT errors.
	 */
	public String searchDatasets(String user, String text, Date lower, Date upper, List<ParameterForLucene> parameters,
			int maxResults) throws IcatException {
		return icat.searchDatasets(sessionId, user, text, lower, upper, parameters, maxResults);
	}

	public void luceneClear() throws IcatException {
		icat.luceneClear(sessionId);
	}

	public void luceneCommit() throws IcatException {
		icat.luceneCommit(sessionId);
	}

	public void lucenePopulate(String entityName) throws IcatException {
		icat.lucenePopulate(sessionId, entityName);
	}

	public List<String> luceneGetPopulating() throws IcatException {
		return icat.luceneGetPopulating(sessionId);
	}

	public String searchDatafiles(String user, String text, Date lower, Date upper,
			List<ParameterForLucene> parameters, int maxResults) throws IcatException {
		return icat.searchDatafiles(sessionId, user, text, lower, upper, parameters,
				maxResults);
	}

}
