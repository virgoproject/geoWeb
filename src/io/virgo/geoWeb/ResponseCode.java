package io.virgo.geoWeb;

/**
 * Enum of response codes used in GeoWeb, based on HTTP ones
 */
public enum ResponseCode {
	
	OK(200, "OK"),
	ACCEPTED(202, "Accepted"),
	BAD_REQUEST(400, "Bad request"),
	NOT_FOUND(404, "Not found"),
	REQUEST_TIMEOUT(408, "Request timeout"),
	ERROR(500, "Error"),
	UNKNOWN_CODE(0, "Unknown code");
	
	private int code;
	private String desc;
	
	ResponseCode(int code, String desc) {
		this.code = code;
		this.desc = desc;
	}
	
	public int getCode() {
		return code;
	}
	
	public String getDesc() {
		return desc;
	}
	
	/**
	 * Get ENUM response code from numeral representation
	 * @param code the code to get as an ENUM
	 * @return the corresponding ENUM
	 */
	public static ResponseCode fromCode(int code) {
		switch(code) {
		case 200:
			return OK;
		case 202:
			return ACCEPTED;
		case 400:
			return BAD_REQUEST;
		case 404:
			return NOT_FOUND;
		case 408:
			return REQUEST_TIMEOUT;
		case 500:
			return ERROR;
			default:
				return UNKNOWN_CODE;
		}
	}
}
