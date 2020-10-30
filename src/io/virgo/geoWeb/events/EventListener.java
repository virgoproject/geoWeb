package io.virgo.geoWeb.events;

import java.util.concurrent.LinkedBlockingQueue;

/**
 * Runnable executing code binded to an event
 *
 */
public class EventListener implements Runnable {
	
	private LinkedBlockingQueue<Event> queue = new LinkedBlockingQueue<Event>();
	
	/**
	 * Add an event to execution queue
	 */
	public void notify(Event event) {
		queue.add(event);
	}
	
	//Overridable methods ran when an event occur
	public void onSetupComplete(SetupCompleteEvent event) {}
	public void onPeerConnection(PeerConnectionEvent event) {}
	public void onPeerDisconnection(PeerDisconnectionEvent event) {}
	public void onPeerHandshaked(PeerHandshakedEvent event) {}
	public void onDataRequested(DataRequestedEvent event) {}

	@Override
	public void run() {
		while(!Thread.currentThread().isInterrupted()) {
			try {
				//wait for an event to occur
				Event event = queue.take();
				
				//execute the associated method
				switch(event.getType()) {
				
				case SETUP_COMPLETE:
					onSetupComplete((SetupCompleteEvent) event);
					break;
				
				case PEER_CONNECTION:
					onPeerConnection((PeerConnectionEvent) event);
					break;
					
				case PEER_DISCONNECTION:
					onPeerDisconnection((PeerDisconnectionEvent) event);
					break;
					
				case PEER_HANDSHAKED:
					onPeerHandshaked((PeerHandshakedEvent) event);
					break;
					
				case DATA_REQUESTED:
					onDataRequested((DataRequestedEvent) event);
					break;
					
				}
				
			} catch (InterruptedException e) {
		        Thread.currentThread().interrupt(); // propagate interrupt
			}
		}
	}
	
}
