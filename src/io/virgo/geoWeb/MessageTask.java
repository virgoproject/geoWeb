package io.virgo.geoWeb;

/**
 * Runnable representing a received message, will process it using the message handler class
 */
public class MessageTask implements Runnable {

	private String message;
	private Peer peer;
	
	public MessageTask(String message, Peer peer) {
		this.message = message;
		this.peer = peer;
	}

	@Override
	public void run() {
		GeoWeb.getInstance().getMessageHandler().superOnMessage(message, peer);
	}
	
}
