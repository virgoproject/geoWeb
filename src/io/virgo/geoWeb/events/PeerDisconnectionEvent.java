package io.virgo.geoWeb.events;

import io.virgo.geoWeb.Peer;

public class PeerDisconnectionEvent extends Event {
	
	private Peer peer;
	
	public PeerDisconnectionEvent(Peer peer) {
		super(EventType.PEER_DISCONNECTION);
		
		this.peer = peer;
	}
	
	public Peer getPeer() {
		return peer;
	}

}
