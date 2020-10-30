package io.virgo.geoWeb.events;

import io.virgo.geoWeb.Peer;

/**
 * Event occurring when a connection to a peer is established (not necessarily handshaked)
 */
public class PeerConnectionEvent extends Event {
	
	private Peer peer;
	
	public PeerConnectionEvent(Peer peer) {
		super(EventType.PEER_CONNECTION);
		
		this.peer = peer;
	}

	/**
	 * @return The peer object representing the connection
	 */
	public Peer getPeer() {
		return peer;
	}
	
}
