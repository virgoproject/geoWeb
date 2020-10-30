package io.virgo.geoWeb.events;

import io.virgo.geoWeb.Peer;

/**
 * Event occurring when a peer has been successfully handshaked and is ready to use
 */
public class PeerHandshakedEvent extends Event {
	
	private Peer peer;
	
	public PeerHandshakedEvent(Peer peer) {
		super(EventType.PEER_HANDSHAKED);
		
		this.peer = peer;
	}
	
	/**
	 * @return the concerned peer object
	 */
	public Peer getPeer() {
		return peer;
	}
	
}
