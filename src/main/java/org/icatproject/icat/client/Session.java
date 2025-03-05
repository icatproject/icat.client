package org.icatproject.icat.client;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.Date;
import java.util.List;
import java.util.Map;

import jakarta.json.JsonArray;

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

		/**
		 * Only import/export attributes which may normally be set by the user
		 */
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
	 * Write (create or update) ICAT entities from a Json String.
	 * 
	 * @param entities
	 *                 Json representation of ICAT entities and their related
	 *                 entities. If there is only one, the outer "[" "]" may be
	 *                 omitted.
	 * 
	 * @return the ids of the top level entities created
	 * 
	 * @throws IcatException
	 *                       For various ICAT errors
	 */
	public List<Long> write(String entities) throws IcatException {
		return icat.write(sessionId, entities);
	}

	/**
	 * Create ICAT entities from a Json String.
	 * 
	 * @deprecated Replace by {@link #write(String)}
	 * @param entities
	 *                 Json representation of ICAT entities and their related
	 *                 entities. If there is only one, the outer "[" "]" may be
	 *                 omitted.
	 * 
	 * @return the ids of the top level entities created
	 * 
	 * @throws IcatException
	 *                       For various ICAT errors
	 */
	@Deprecated
	public List<Long> create(String entities) throws IcatException {
		return icat.write(sessionId, entities);
	}

	/**
	 * Delete ICAT entities specified by a Json String.
	 * 
	 * @param entities
	 *                 Json representation of ICAT entities. If there is only one
	 *                 the outer "[" "]" may be omitted.
	 * 
	 * @throws IcatException
	 *                       For various ICAT errors
	 */
	public void delete(String entities) throws IcatException {
		icat.delete(sessionId, entities);
	}

	/**
	 * Export all metadata from ICAT.
	 * 
	 * @param attributes
	 *                   which attributes to export. If you don't plan to
	 *                   importMetaData as a "root user" there is no point in using
	 *                   {@link Attributes#ALL} and {@link Attributes#USER} is to be
	 *                   preferred.
	 * 
	 * @return an OutputStream. The structure of the OutputStream is described
	 *         at {@link Session}
	 * 
	 * @throws IcatException
	 *                       For various ICAT errors
	 */
	public InputStream exportMetaData(Attributes attributes) throws IcatException {
		return icat.exportMetaData(sessionId, null, attributes);
	}

	/**
	 * Export metadata from ICAT as specified in the query
	 * 
	 * @param query
	 *                   a normal ICAT query which may have an INCLUDE clause. This
	 *                   is used to define the metadata to export.
	 * @param attributes
	 *                   which attributes to export. If you don't plan to
	 *                   importMetaData as a "root user" there is no point in using
	 *                   {@link Attributes#ALL} and {@link Attributes#USER} is to be
	 *                   preferred.
	 * 
	 * @return an OutputStream. The structure of the OutputStream is described
	 *         at {@link Session}
	 * 
	 * @throws IcatException
	 *                       For various ICAT errors
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
	 *                       For various ICAT errors
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
	 *                       For various ICAT errors
	 */
	public String getUserName() throws IcatException {
		return icat.getUserName(sessionId);
	}

	/**
	 * Import metadata into ICAT for a file specified by a Path
	 * 
	 * @param path
	 *                        the path of the import file. The structure of the
	 *                        import file is described at {@link Session}
	 * @param duplicateAction
	 *                        what to do when a duplicate is encountered
	 * @param attributes
	 *                        which attributes to import. Only a "root user" can
	 *                        specify {@link Attributes#ALL} to respect those fields
	 *                        specified in the import file which are not settable by
	 *                        normal
	 *                        users: createId, createTime, modId and modTime. This
	 *                        is to
	 *                        allow an ICAT to be accurately exported and imported.
	 * 
	 * @throws IcatException
	 *                       For various ICAT errors
	 */
	public void importMetaData(Path path, DuplicateAction duplicateAction, Attributes attributes) throws IcatException {
		icat.importMetaData(sessionId, path, duplicateAction, attributes);
	}

	/**
	 * Logout of the session after which the session cannot be re-used
	 * 
	 * @throws IcatException
	 *                       For various ICAT errors
	 */
	public void logout() throws IcatException {
		icat.logout(sessionId);
	}

	/**
	 * Refresh the session by resetting the time remaining
	 * 
	 * @throws IcatException
	 *                       For various ICAT errors
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
	 *              a normal ICAT query with optional INCLUDE and LIMIT clauses.
	 * 
	 * @return the Json holding the results
	 * 
	 * @throws IcatException
	 *                       For various ICAT errors
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
	 *              a normal ICAT get query with an optional INCLUDE clause.
	 * @param id
	 *              the id of the entity to be returned
	 * 
	 * @return the Json holding the result
	 * 
	 * @throws IcatException
	 *                       For various ICAT errors
	 */
	public String get(String query, long id) throws IcatException {
		return icat.get(sessionId, query, id);
	}

	/**
	 * Return a set of investigations satisfying the constraints
	 * 
	 * @deprecated in favour of {@link #searchInvestigations(String, String, Date, Date, List, String, int, String, JsonArray)}, which allows an upper limit
	 *             on population to be set and makes deletion of existing documents
	 *             optional.
	 * 
	 * @param user
	 *                     If not null must exactly match the name of a user related
	 *                     via
	 *                     the investigation user to the investigation.
	 * @param text
	 *                     If not null a text search (with ANDs ORs etc) for any
	 *                     text in
	 *                     the investigation fields. This is understood by the
	 *                     <a href=
	 *                     "https://lucene.apache.org/core/4_10_2/queryparser/org/apache/lucene/queryparser/classic/package-summary.html#package_description"
	 *                     >lucene parser</a> but avoid trying to use fields.
	 * @param lower
	 *                     If not null investigation end date must be greater than
	 *                     or
	 *                     equal to this.
	 * @param upper
	 *                     If not null investigation start date must be less than or
	 *                     equal to this.
	 * @param parameters
	 *                     If not null all the parameters must match.
	 * @param samples
	 *                     If not null all the specified samples, using a text
	 *                     search
	 *                     (with ANDs ORs etc) must be related to the investigation.
	 *                     This
	 *                     is understood by the <a href=
	 *                     "https://lucene.apache.org/core/4_10_2/queryparser/org/apache/lucene/queryparser/classic/package-summary.html#package_description"
	 *                     >lucene parser</a> but avoid trying to use fields.
	 * @param userFullName
	 *                     If not null a text search is made against the full name
	 *                     of a
	 *                     user related via the investigation user to the
	 *                     investigation.
	 *                     This is understood by the <a href=
	 *                     "https://lucene.apache.org/core/4_10_2/queryparser/org/apache/lucene/queryparser/classic/package-summary.html#package_description"
	 *                     >lucene parser</a> but avoid trying to use fields.
	 * @param maxResults
	 *                     The maximum number of results to return.
	 * 
	 * @return the Json holding the result.
	 * 
	 * @throws IcatException
	 *                       For various ICAT errors.
	 */
	@Deprecated
	public String searchInvestigations(String user, String text, Date lower, Date upper,
			List<ParameterForLucene> parameters, List<String> samples, String userFullName, int maxResults)
			throws IcatException {
		return icat.searchInvestigations(sessionId, user, text, lower, upper, parameters, samples, userFullName,
				maxResults);
	}

	/**
	 * Return a set of indexed documents representing Investigations that satisfy
	 * the search constraints
	 * 
	 * @param user
	 *                     If not null must exactly match the name of a user related
	 *                     via the investigation user to the investigation.
	 * @param text
	 *                     If not null a text search (with ANDs ORs etc) for any
	 *                     text in the investigation fields. This is understood by
	 *                     the <a href=
	 *                     "https://lucene.apache.org/core/4_10_2/queryparser/org/apache/lucene/queryparser/classic/package-summary.html#package_description"
	 *                     >lucene parser</a> but avoid trying to use fields.
	 * @param lower
	 *                     If not null investigation end date must be greater than
	 *                     or equal to this.
	 * @param upper
	 *                     If not null investigation start date must be less than or
	 *                     equal to this.
	 * @param parameters
	 *                     If not null all the parameters must match.
	 * @param samples
	 *                     If not null all the specified samples, using a text
	 *                     search (with ANDs ORs etc) must be related to the
	 *                     investigation.
	 *                     This is understood by the <a href=
	 *                     "https://lucene.apache.org/core/4_10_2/queryparser/org/apache/lucene/queryparser/classic/package-summary.html#package_description"
	 *                     >lucene parser</a> but avoid trying to use fields.
	 * @param userFullName
	 *                     If not null a text search is made against the full name
	 *                     of a user related via the investigation user to the
	 *                     investigation. This is understood by the <a href=
	 *                     "https://lucene.apache.org/core/4_10_2/queryparser/org/apache/lucene/queryparser/classic/package-summary.html#package_description"
	 *                     >lucene parser</a> but avoid trying to use fields.
	 * @param searchAfter
	 *                     String representing the last document of a previous
	 *                     search, so that results from this search will only
	 *                     include results from after the document
	 * @param maxCount
	 *                     The maximum number of results to return.
	 * @param sort
	 *                     String representing a JSON object which contains key
	 *                     value pairs of the field(s) to sort on and their
	 *                     direction
	 * @param facets
	 *                     String representing a JsonArray of JsonObjects. Each
	 *                     should define the "target" entity name, and optionally
	 *                     another JsonArray of "dimensions", which are specific
	 *                     fields to facet. If absent, then all applicable fields
	 *                     will be faceted.
	 * 
	 * @return the Json holding the result.
	 * 
	 * @throws IcatException
	 *                       For various ICAT errors.
	 */
	public String searchInvestigations(String user, String text, Date lower, Date upper,
			List<ParameterForLucene> parameters, String userFullName, String searchAfter, int maxCount, String sort,
			JsonArray facets) throws IcatException {
		return icat.searchInvestigations(sessionId, user, text, lower, upper, parameters, userFullName,
				searchAfter, maxCount, sort, facets);
	}

	/**
	 * Return a set of datasets satisfying the constraints
	 * 
	 * @deprecated in favour of {@link #searchDatasets(String, String, Date, Date, List, String, int, String, JsonArray)}, which allows an upper limit
	 *             on population to be set and makes deletion of existing documents
	 *             optional.
	 * 
	 * @param user
	 *                   If not null must exactly match the name of a user related
	 *                   via the investigation user and the investigation to the
	 *                   data set.
	 * @param text
	 *                   If not null a text search (with ANDs ORs etc) for any text
	 *                   in the data set fields. This is understood by the <a href=
	 *                   "https://lucene.apache.org/core/4_10_2/queryparser/org/apache/lucene/queryparser/classic/package-summary.html#package_description"
	 *                   >lucene parser</a> but avoid trying to use fields.
	 * @param lower
	 *                   If not null data set end date must be greater than or equal
	 *                   to this.
	 * @param upper
	 *                   If not null data set start date must be less than or equal
	 *                   to this.
	 * @param parameters
	 *                   If not null all the parameters must match.
	 * @param maxResults
	 *                   The maximum number of results to return.
	 * 
	 * @return the Json holding the result.
	 * 
	 * @throws IcatException
	 *                       For various ICAT errors.
	 */
	@Deprecated
	public String searchDatasets(String user, String text, Date lower, Date upper, List<ParameterForLucene> parameters,
			int maxResults) throws IcatException {
		return icat.searchDatasets(sessionId, user, text, lower, upper, parameters, maxResults);
	}

	/**
	 * Return a set of indexed documents representing Datasets that satisfy the
	 * search constraints
	 * 
	 * @param user
	 *                    If not null must exactly match the name of a user related
	 *                    via the investigation user and the investigation to the
	 *                    data set.
	 * @param text
	 *                    If not null a text search (with ANDs ORs etc) for any text
	 *                    in the data set fields. This is understood by the <a href=
	 *                    "https://lucene.apache.org/core/4_10_2/queryparser/org/apache/lucene/queryparser/classic/package-summary.html#package_description"
	 *                    >lucene parser</a> but avoid trying to use fields.
	 * @param lower
	 *                    If not null data set end date must be greater than or
	 *                    equal to this.
	 * @param upper
	 *                    If not null data set start date must be less than or equal
	 *                    to this.
	 * @param parameters
	 *                    If not null all the parameters must match.
	 * @param searchAfter
	 *                    String representing the last document of a previous
	 *                    search, so that results from this search will only include
	 *                    results from after the document
	 * @param maxCount
	 *                    The maximum number of results to return.
	 * @param sort
	 *                    String representing a JSON object which contains key value
	 *                    pairs of the field(s) to sort on and their direction
	 * @param facets
	 *                    String representing a JsonArray of JsonObjects. Each
	 *                    should define the "target" entity name, and optionally
	 *                    another JsonArray of "dimensions", which are specific
	 *                    fields to facet. If absent, then all applicable fields
	 *                    will be faceted.
	 * 
	 * @return the Json holding the result.
	 * 
	 * @throws IcatException
	 *                       For various ICAT errors.
	 */
	public String searchDatasets(String user, String text, Date lower, Date upper, List<ParameterForLucene> parameters,
			String searchAfter, int maxCount, String sort, JsonArray facets) throws IcatException {
		return icat.searchDatasets(sessionId, user, text, lower, upper, parameters, searchAfter, maxCount, sort,
				facets);
	}

	/**
	 * Clear the lucene populating list
	 * 
	 * @throws IcatException
	 *                       For various ICAT errors.
	 */
	public void luceneClear() throws IcatException {
		icat.luceneClear(sessionId);
	}

	/**
	 * Force a commit of the lucene database
	 * 
	 * @throws IcatException
	 *                       For various ICAT errors.
	 */
	public void luceneCommit() throws IcatException {
		icat.luceneCommit(sessionId);
	}

	/**
	 * Clear and repopulate lucene documents for the specified entityName.
	 * 
	 * @deprecated in favour of {@link #searchPopulate}, which allows an upper limit
	 *             on population to be set and makes deletion of existing documents
	 *             optional.
	 * 
	 * @param entityName the name of the entity
	 * @param minId      Process entities with id values greater than (NOT equal to)
	 *                   this value
	 * 
	 * @throws IcatException For various ICAT errors.
	 */
	@Deprecated
	public void lucenePopulate(String entityName, long minId) throws IcatException {
		icat.lucenePopulate(sessionId, entityName, minId, null, true);
	}

	/**
	 * Populates search engine documents for the specified entityName.
	 * 
	 * Optionally, this will also delete all existing documents of entityName. This
	 * should only be used when repopulating from scratch is needed.
	 * 
	 * @param entityName the name of the entity
	 * @param minId      Process entities with id values greater than (NOT equal to)
	 *                   this value
	 * @param maxId      Process entities up to and including with id up to and
	 *                   including this value
	 * @param delete     If true, then all existing documents of this type will be
	 *                   deleted before adding new ones.
	 * @throws IcatException For various ICAT errors.
	 */
	public void searchPopulate(String entityName, long minId, long maxId, boolean delete) throws IcatException {
		icat.lucenePopulate(sessionId, entityName, minId, maxId, delete);
	}

	/**
	 * Return a list of class names for which population is going on
	 * 
	 * @return list of class names
	 * 
	 * @throws IcatException
	 *                       For various ICAT errors.
	 */
	public List<String> luceneGetPopulating() throws IcatException {
		return icat.luceneGetPopulating(sessionId);
	}

	/**
	 * Return a set of data files satisfying the constraints
	 * 
	 * @deprecated in favour of {@link #searchDatafiles(String, String, Date, Date, List, String, int, String, JsonArray)}, which allows an upper limit
	 *             on population to be set and makes deletion of existing documents
	 *             optional.
	 * 
	 * @param user
	 *                   If not null must exactly match the name of a user related
	 *                   via
	 *                   the investigation user and the investigation to the data
	 *                   set.
	 * @param text
	 *                   If not null a text search (with ANDs ORs etc) for any text
	 *                   in the data file fields. This is understood by the <a href=
	 *                   "https://lucene.apache.org/core/4_10_2/queryparser/org/apache/lucene/queryparser/classic/package-summary.html#package_description"
	 *                   >lucene parser</a> but avoid trying to use fields.
	 * @param lower
	 *                   If not null data file date must be greater than or equal to
	 *                   this.
	 * @param upper
	 *                   If not null data file date must be less than or equal to
	 *                   this.
	 * @param parameters
	 *                   If not null all the parameters must match.
	 * @param maxResults
	 *                   The maximum number of results to return.
	 * 
	 * @return the Json holding the result.
	 * 
	 * @throws IcatException
	 *                       For various ICAT errors.
	 */
	@Deprecated
	public String searchDatafiles(String user, String text, Date lower, Date upper, List<ParameterForLucene> parameters,
			int maxResults) throws IcatException {
		return icat.searchDatafiles(sessionId, user, text, lower, upper, parameters, maxResults);
	}

	/**
	 * Return a set of indexed documents representing Datafiles that satisfy the
	 * search constraints
	 * 
	 * @param user
	 *                    If not null must exactly match the name of a user related
	 *                    via the investigation user and the investigation to the
	 *                    data set.
	 * @param text
	 *                    If not null a text search (with ANDs ORs etc) for any text
	 *                    in the data file fields. This is understood by the
	 *                    <a href=
	 *                    "https://lucene.apache.org/core/4_10_2/queryparser/org/apache/lucene/queryparser/classic/package-summary.html#package_description"
	 *                    >lucene parser</a> but avoid trying to use fields.
	 * @param lower
	 *                    If not null data file date must be greater than or equal
	 *                    to this.
	 * @param upper
	 *                    If not null data file date must be less than or equal to
	 *                    this.
	 * @param parameters
	 *                    If not null all the parameters must match.
	 * @param searchAfter
	 *                    String representing the last document of a previous
	 *                    search, so that results from this search will only include
	 *                    results from after the document
	 * @param maxCount
	 *                    The maximum number of results to return.
	 * @param sort
	 *                    String representing a JSON object which contains key value
	 *                    pairs of the field(s) to sort on and their direction
	 * @param facets
	 *                    String representing a JsonArray of JsonObjects. Each
	 *                    should define the "target" entity name, and optionally
	 *                    another JsonArray of "dimensions", which are specific
	 *                    fields to facet. If absent, then all applicable fields
	 *                    will be faceted.
	 * 
	 * @return the Json holding the result.
	 * 
	 * @throws IcatException
	 *                       For various ICAT errors.
	 */
	public String searchDatafiles(String user, String text, Date lower, Date upper, List<ParameterForLucene> parameters,
			String searchAfter, int maxCount, String sort, JsonArray facets) throws IcatException {
		return icat.searchDatafiles(sessionId, user, text, lower, upper, parameters, searchAfter, maxCount, sort,
				facets);
	}

	/**
	 * Clone an entity and return the id of the clone
	 * 
	 * @param name
	 *             the name of the type of entity
	 * @param id
	 *             the id value of the entity to be cloned
	 * @param keys
	 *             a map of field names and values to be different in the clone
	 * 
	 * @return the id of the clone
	 * 
	 * @throws IcatException
	 *                       For various ICAT errors.
	 */
	public long cloneEntity(String name, long id, Map<String, String> keys) throws IcatException {
		return icat.cloneEntity(sessionId, name, id, keys);
	}

	/**
	 * Wait for the specified number of milliseconds and return.
	 * 
	 * This is only used in testing
	 * 
	 * @param ms
	 *           number of milliseconds to wait
	 * 
	 * @throws IcatException
	 *                       For various ICAT errors.
	 */
	public void waitMillis(long ms) throws IcatException {
		icat.waitMillis(sessionId, ms);
	}

	/**
	 * @param path input path to find contents of
	 * @return json describing the contents
	 * @throws IcatException
	 */
	public String list(String path) throws IcatException {
		return icat.list(sessionId, path);
	}

}
