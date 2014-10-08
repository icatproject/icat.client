package org.icatproject.icat.client;

/** An ICAT Exception with various types */
@SuppressWarnings("serial")
public class IcatException extends Exception {

	/** The type of the IcatException */
	public enum IcatExceptionType {
		/** An input paramter appears to be incorrect */
		BAD_PARAMETER,
		/** Some internal error has occured */
		INTERNAL,
		/** This is normally an authorization problem */
		INSUFFICIENT_PRIVILEGES,
		/** The requested object does not exist */
		NO_SUCH_OBJECT_FOUND,
		/** An object already exists with the same key fields */
		OBJECT_ALREADY_EXISTS,
		/** This is normally an authentication problem or the session has expired */
		SESSION,
		/** If the call is not approriate for the system in the current state */
		VALIDATION
	}

	private IcatExceptionType type;
	private int offset = -1;

	@Override
	public String toString() {
		return type + " " + super.toString();
	}

	IcatException(IcatExceptionType type, String msg) {
		super(msg);
		this.type = type;
	}

	IcatException(IcatExceptionType type, String msg, int offset) {
		this(type, msg);
		this.offset = offset;
	}

	/**
	 * Return the offset in "many" calls. If the offset has not been set this will return -1
	 * 
	 * @return the offset
	 */
	public int getOffset() {
		return offset;
	}

	/**
	 * Return the ICATExceptionType
	 * 
	 * @return the ICATExceptionType
	 */
	public IcatExceptionType getType() {
		return type;
	}

}
