package io.virgo.geoWeb.events;

import io.virgo.geoWeb.Peer;

public class PeerHandshakedEvent extends Event {
	
	private Peer peer;
	
	public PeerHandshakedEvent(Peer peer) {
		super(EventType.PEER_HANDSHAKED);
		
		this.peer = peer;
	}
	
	public Peer getPeer() {
		return peer;
	}
	
}
