package io.virgo.geoWeb;

import java.io.ByteArrayInputStream;

import io.virgo.virgoCryptoLib.Sha256;
import io.virgo.virgoCryptoLib.Sha256Hash;

/**
 * Object representing a data request
 */
public class DataRequest {

	private ResponseCode status = ResponseCode.UNKNOWN_CODE;
	private Sha256Hash dataHash;
	
	byte[] data;
	
	int received = 0;
	int dataSize = 0;
	
	/**
	 * @param dataHash the hash of the data requested
	 */
	public DataRequest(Sha256Hash dataHash) {
		this.dataHash = dataHash;
	}


	/**
	 * Called when peer accepted to give data,
	 * initiate an array of given size to receive data 
	 * 
	 * @param dataSize the expected size of data
	 */
	void prepare(int dataSize) {
		data = new byte[dataSize];
		
		this.dataSize = dataSize;
		
		status = ResponseCode.ACCEPTED;
		
		onDownloadStarted();
	}


	/**
	 * Called when download finished, check if received data correspond to
	 * expectations and trigger onDownloadFinished event
	 * or onDownloadError if data is bad
	 */
	void finish() {
		if(Sha256.getHash(data).equals(dataHash)) {
			status = ResponseCode.OK;
			onDownloadFinished(data);
		}else {
			status = ResponseCode.ERROR;
			onDownloadError();
		}
	}

	/**
	 * @return Expected data hash
	 */
	public Sha256Hash getHash() {
		return dataHash;
	}
	
	
	public long getDataSize() {
		return dataSize;
	}
	
	public ByteArrayInputStream getDataInputStream() {
		return new ByteArrayInputStream(data);
	}
	
	/**
	 * @return Request status
	 */
	public ResponseCode getStatus() {
		return status;
	}
	
	/**
	 * @return downloaded data size in bytes
	 */
	public int getDownloadedSize() {
		return received;
	}
	
	/**
	 * Overridable event called when data download starts
	 */
	public void onDownloadStarted() {
		
	}
	
	/**
	 * Overridable event called when download finished
	 * @param data the requested data
	 * @return the requested data
	 */
	public byte[] onDownloadFinished(byte[] data) {
		return data;
	}
	
	/**
	 * Overridable event called when an error occur during download,
	 * Most probably wrong data received
	 */
	public void onDownloadError() {
		
	}
	
}
