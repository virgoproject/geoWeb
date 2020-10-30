package io.virgo.geoWeb;

import org.json.JSONObject;

/**
 * Object representing a sync message response, basically a {@link ResponseCode}
 * and the response itself as a {@link JSONObject}
 */
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
	
	/**
	 * @return Message response code
	 */
	public ResponseCode getResponseCode() {
		return respCode;
	}
	
	/**
	 * @return Message response as a {@link JSONObject}
	 */
	public JSONObject getResponse() {
		return response;
	}
	
}
