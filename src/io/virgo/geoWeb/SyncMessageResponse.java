package io.virgo.geoWeb;

import org.json.JSONObject;

public class SyncMessageResponse {

	private ResponseCode respCode;
	private JSONObject response = null;
	
	public SyncMessageResponse(ResponseCode badRespCode) {
		this.respCode = badRespCode;
	}

	public SyncMessageResponse(ResponseCode respCode, JSONObject response) {
		this.respCode = respCode;
		this.response = response;
	}
	
	public ResponseCode getResponseCode() {
		return respCode;
	}
	
	public JSONObject getResponse() {
		return response;
	}
	
}
