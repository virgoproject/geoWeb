package io.virgo.geoWeb.events;

import java.util.concurrent.LinkedBlockingQueue;

public class EventListener implements Runnable {
	
	private boolean interrupt = false;
	private LinkedBlockingQueue<Event> queue = new LinkedBlockingQueue<Event>();
	
	public void notify(Event event) {
		queue.add(event);
	}
	
	public void onSetupComplete(SetupCompleteEvent event) {}
	public void onPeerConnection(PeerConnectionEvent event) {}
	public void onPeerDisconnection(PeerDisconnectionEvent event) {}
	public void onPeerHandshaked(PeerHandshakedEvent event) {}
	

	@Override
	public void run() {
		while(!interrupt) {
			try {
				Event event = queue.take();
				
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
					
				}
				
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}
	
	public void interrupt() {
		interrupt = true;
	}
	
}
