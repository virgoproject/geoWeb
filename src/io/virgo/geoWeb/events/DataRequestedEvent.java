package io.virgo.geoWeb.events;

import io.virgo.geoWeb.Peer;
import io.virgo.geoWeb.DataRequest;
import io.virgo.virgoCryptoLib.Sha256;
import io.virgo.virgoCryptoLib.Sha256Hash;

/**
 * Event occurring when a peer is asking for data
 */
public class DataRequestedEvent extends Event {

	DataRequest request;
	Peer peer;
	Sha256Hash hash;
	
	public DataRequestedEvent(Sha256Hash hash, Peer peer) {
		super(EventType.DATA_REQUESTED);
		
		this.hash = hash;
		this.peer = peer;
		
	}

	/**
	 * Give the requested data to the peer
	 * @param data
	 */
	public void uploadData(byte[] data) {
		
		peer.sendData(data,
				hash.toBytes());
	}

	/**
	 * @return The requested data hash
	 */
	public Sha256Hash getHash() {
		return hash;
	}
	
}
