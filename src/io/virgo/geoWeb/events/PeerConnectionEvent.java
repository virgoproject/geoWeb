package io.virgo.geoWeb.events;

import io.virgo.geoWeb.Peer;

public class PeerConnectionEvent extends Event {
	
	private Peer peer;
	
	public PeerConnectionEvent(Peer peer) {
		super(EventType.PEER_CONNECTION);
		
		this.peer = peer;
	}

	public Peer getPeer() {
		return peer;
	}
	
}
