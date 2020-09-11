package io.virgo.geoWeb.events;

import io.virgo.geoWeb.Peer;
import io.virgo.geoWeb.DataRequest;
import io.virgo.virgoCryptoLib.Sha256;
import io.virgo.virgoCryptoLib.Sha256Hash;

public class DataRequestedEvent extends Event {

	DataRequest request;
	Peer peer;
	Sha256Hash hash;
	
	public DataRequestedEvent(Sha256Hash hash, Peer peer) {
		super(EventType.DATA_REQUESTED);
		
		this.hash = hash;
		this.peer = peer;
		
	}

	public void uploadData(byte[] data) {
		if(!Sha256.getHash(data).equals(request.getHash()))
			throw new IllegalArgumentException("Given data doesn't fit requested one");
		
		peer.sendData(data, hash.toBytes());
	}

	public Sha256Hash getHash() {
		return hash;
	}
	
}
