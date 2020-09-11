package io.virgo.geoWeb;

import io.virgo.virgoCryptoLib.Sha256;
import io.virgo.virgoCryptoLib.Sha256Hash;

public class DataRequest {

	private ResponseCode status = ResponseCode.UNKNOWN_CODE;
	private Sha256Hash dataHash;
	
	byte[] data;
	
	int received = 0;
	int dataSize = 0;
	
	public DataRequest(Sha256Hash dataHash) {
		this.dataHash = dataHash;
	}


	void prepare(int dataSize) {
		data = new byte[dataSize];
		
		this.dataSize = dataSize;
		
		status = ResponseCode.ACCEPTED;
		
		onDownloadStarted();
	}


	void finish() {
		if(Sha256.getHash(data).equals(dataHash)) {
			status = ResponseCode.OK;
			onDownloadFinished(data);
		}else {
			status = ResponseCode.ERROR;
			onDownloadError();
		}
	}


	public Sha256Hash getHash() {
		return dataHash;
	}
	
	public ResponseCode getStatus() {
		return status;
	}
	
	public int getDownloadedSize() {
		return received;
	}
	
	
	public void onDownloadStarted() {
		
	}
	
	public byte[] onDownloadFinished(byte[] data) {
		return data;
	}
	
	public void onDownloadError() {
		
	}
	
}
