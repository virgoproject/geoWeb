package io.virgo.geoWeb;

public enum ResponseCode {
	
	OK(200, "OK"),
	BAD_REQUEST(400, "Bad request"),
	NOT_FOUND(404, "Not found"),
	REQUEST_TIMEOUT(408, "Request timeout"),
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
	
	public static ResponseCode fromCode(int code) {
		switch(code) {
		case 200:
			return OK;
		case 400:
			return BAD_REQUEST;
		case 404:
			return NOT_FOUND;
		case 408:
			return REQUEST_TIMEOUT;
			default:
				return UNKNOWN_CODE;
		}
	}
}
