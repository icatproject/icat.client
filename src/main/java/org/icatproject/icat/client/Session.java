package org.icatproject.icat.client;

import java.io.InputStream;
import java.nio.file.Path;

/**
 * A RESTful ICAT session.
 * <p>
 * The exportMetaData and ImportMetaData call make use of a special format to represent ICAT data
 * efficiently. The file may contain line starting with a # sign. The first non-comment line
 * contains the version number of the file format with major and minor parts. Each entity type is
 * preceded by a blank line line followed by a one line entity descriptor and then a line for each
 * entity of that type.
 * <p>
 * For example:
 * <p>
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
 * The entity descriptor starts with the name of the entity type followed by a comma separated list
 * attribute of field names held inside parentheses. It is not necessary to include those which you
 * don't wish to set as any that are not present and are allowed to be null will be set to null when
 * importing. So we see that this file will create a Facility with fields: name, daysUntilRelease,
 * createId and createTime. Following the field name is a colon and an integer showing the offset to
 * the data field in each of the next set of rows. So a facility will be created with a name of
 * "Test port facility" and with 90 daysUntilRelease. All strings must be enclosed in double quotes
 * and to represent a double quote within the string then two double quotes must be used. True,
 * false and null literals are not case sensitive. The last two fields of the facility are createId
 * and createTime. If you specify that you want all attributes and you are a "root user" then the
 * values of createId and createTime will be respected otherwise the current time is used and the id
 * is that of the user doing the import. Timestamps literals follow ISO 8601 and support fractional
 * seconds and time zones. If the time zone is omitted it is interpreted as local time.
 * <p>
 * Now consider the InvestigationType for which we need to specify the facility to which it belongs
 * and its name. The facility cannot be descibed by its id because we don't know what it is. So
 * instead we list in parentheses the field names that define it. So name:0 is the name of the
 * facility and name:1 is the name of the InvestigationType.
 * <p>
 * The next line shows the convenience of this syntax. The investigation has a facility (identified
 * by its name:0) and the name:1 of the investigation and the visitId but it also has a type which
 * is identified a facility (identified by its name:0) and by the name:3 of the type. Finally it has
 * a title:4 field. Note that name:0 is used twice as in this case the inverstigation belongs to the
 * same facility as its type. This works fine as long as we deal with entity types which have key
 * fields. This is shown in the next snippet from an import file:
 * <p>
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
 * Here we have the DataCollection which we imagine to be indentified by the anonymous variable "?".
 * This section of the file will create three DataCollection entries which we shall remember for the
 * duration of the import process as "a", "b" and "c".
 * <p>
 * DataCollectionDatafiles are then associated with DataCollections "a" and "b" and a job is created
 * with one DataCollection as input and one as output.
 * <p>
 * When performing export the same format is used however some values will be repeated - for example
 * the facility name will appear many times in most rows.
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
		/** Throw an expection */
		THROW,

		/** Don't check just go to the next row */
		IGNORE,

		/** Check that new data matches the old */
		CHECK,

		/** Replace old data with new */
		OVERWRITE
	}

	private String sessionId;
	private ICAT icat;

	Session(ICAT icat, String sessionId) {
		this.icat = icat;
		this.sessionId = sessionId;
	}

	/**
	 * Return the user name corresponding to the session.
	 * 
	 * @return the user name
	 * 
	 * @throws IcatException
	 */
	public String getUserName() throws IcatException {
		return icat.getUserName(sessionId);
	}

	/**
	 * Return the time remaining in the session in minutes
	 * 
	 * @return the time remaining
	 * 
	 * @throws IcatException
	 */
	public double getRemainingMinutes() throws IcatException {
		return icat.getRemainingMinutes(sessionId);
	}

	/**
	 * Logout of the session after which the session cannot be re-used
	 * 
	 * @throws IcatException
	 */
	public void logout() throws IcatException {
		icat.logout(sessionId);
	}

	/**
	 * Refresh the session and thereby reset the time remaining
	 * 
	 * @throws IcatException
	 */
	public void refresh() throws IcatException {
		icat.refresh(sessionId);
	}

	/**
	 * Import metadata into ICAT for a file specified by a Path
	 * 
	 * @param path
	 *            the path of the import file. The structure of the import file is described at
	 *            {@link Session}
	 * @param duplicateAction
	 *            what to do when a duplicate is encountered
	 * @param attributes
	 *            which attributes to import. Only a "root user" can specify {@link Attributes#ALL}
	 *            to respect those fields specified in the import file which are not settable by
	 *            normal users: createId, createTime, modId and modTime. This is to allow an ICAT to
	 *            be accurately exported and imported.
	 * 
	 * @throws IcatException
	 */
	public void importMetaData(Path path, DuplicateAction duplicateAction, Attributes attributes)
			throws IcatException {
		icat.importMetaData(sessionId, path, duplicateAction, attributes);
	}

	/**
	 * Export all metadata from ICAT.
	 * 
	 * @param attributes
	 *            which attributes to export. If you don't plan to importMetaData as a "root user"
	 *            there is no point in using {@link Attributes#ALL} and {@link Attributes#USER} is
	 *            to be preferred.
	 * 
	 * @return an OutputStream. The structure of the OutputStream is described at {@link Session}
	 * 
	 * @throws IcatException
	 */
	public InputStream exportMetaData(Attributes attributes) throws IcatException {
		return icat.exportMetaData(sessionId, null, attributes);
	}

	/**
	 * Export metadata from ICAT as specified in the query
	 * 
	 * @param query
	 *            a normal ICAT query which may have an INCLUDE clause. This is used to define the
	 *            metadata to export.
	 * @param attributes
	 *            which attributes to export. If you don't plan to importMetaData as a "root user"
	 *            there is no point in using {@link Attributes#ALL} and {@link Attributes#USER} is
	 *            to be preferred.
	 * 
	 * @return an OutputStream. The structure of the OutputStream is described at {@link Session}
	 * 
	 * @throws IcatException
	 */
	public InputStream exportMetaData(String query, Attributes attributes) throws IcatException {
		return icat.exportMetaData(sessionId, query, attributes);
	}

	/**
	 * Carry out an ICAT search the data are returned as a Json string
	 * 
	 * Note that this call is very experimental and should not be relied upon to continue in its
	 * present form.
	 * 
	 * @param query
	 *            a normal ICAT query with optional INCLUDE and LIMIT clauses.
	 * 
	 * @return the Json holding the results
	 * 
	 * @throws IcatException
	 */
	public String search(String query) throws IcatException {
		return icat.search(sessionId, query);
	}

	/**
	 * Create an ICAT entity from a Json String.
	 * 
	 * Note that this call is very experimental and should not be relied upon to continue in its
	 * present form.
	 * 
	 * @param bean
	 *            Json represntation of an ICAT entity and its related entities
	 * 
	 * @return the id of the top level entity
	 * 
	 * @throws IcatException
	 */
	public long create(String bean) throws IcatException {
		return icat.create(sessionId, bean);
	}

}
