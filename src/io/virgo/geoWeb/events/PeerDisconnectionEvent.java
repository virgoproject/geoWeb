package io.virgo.geoWeb.events;

import io.virgo.geoWeb.Peer;

/**
 * Event occurring when a connection to a peer is being closed
 */
public class PeerDisconnectionEvent extends Event {
	
	private Peer peer;
	
	public PeerDisconnectionEvent(Peer peer) {
		super(EventType.PEER_DISCONNECTION);
		
		this.peer = peer;
	}
	
	/**
	 * @return The peer object representing the connection
	 */
	public Peer getPeer() {
		return peer;
	}

}
