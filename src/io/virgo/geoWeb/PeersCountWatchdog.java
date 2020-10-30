package io.virgo.geoWeb;

import java.util.Timer;
import java.util.TimerTask;

import org.json.JSONObject;

/**
 * Runnable class trying to contact new peers if target peer count hasn't been reached
 * 
 * Possible peers are divided into 3 lists: current, recent and old peers.
 * We first try to contact every peers in current list, then recent then old
 * If we fail to connect to a peer then it is retrograded to the next list
 * (ex: if we can't connect to a peer in 'current peers' list then we move it to 'recent peers' list)
 * 
 * sleeps 10 minutes once list end reached then repeat 
 */
class PeersCountWatchdog implements Runnable {

	int currentList = 0;
	int currentIndex = 0;
	
	PeersCountWatchdog(){
		
		//message to broadcast to get new peers addresses
		JSONObject getaddrMessage = new JSONObject();
		getaddrMessage.put("command", "getaddr");
		
		//try to get new addresses every 10 minutes if desired peers count not reached
		Timer addressBroadcastTimer = new Timer();
		GeoWeb.getInstance().timers.add(addressBroadcastTimer);
		addressBroadcastTimer.scheduleAtFixedRate(new TimerTask() {

			@Override
			public void run() {
				if(GeoWeb.getInstance().peers.size() < GeoWeb.getInstance().getPeerCountTarget()) {
					GeoWeb.getInstance().broadCast(getaddrMessage);
				}
			}
			
		}, 10000L, 600000L);
	}
	
	@Override
	public void run() {

		while(!Thread.currentThread().isInterrupted()) {
			try {
				
				if(GeoWeb.getInstance().peers.size() < GeoWeb.getInstance().getPeerCountTarget()) {
					switch(currentList) {
					case 0:
						String[] currentPeers = GeoWeb.getInstance().getPeerListManager().getCurrentPeers();
	 					
						if(currentPeers.length > currentIndex) {
							String[] addressArray = currentPeers[currentIndex].split(":");
							GeoWeb.getInstance().connectTo(addressArray[0], Integer.parseInt(addressArray[1]));
							
							currentIndex++;
						}else {
							currentList = 1;
							currentIndex = 0;
						}
						
						break;
					case 1:
						String[] recentPeers = GeoWeb.getInstance().getPeerListManager().getRecentPeers();
						
						if(recentPeers.length > currentIndex) {
							String[] addressArray = recentPeers[currentIndex].split(":");
							GeoWeb.getInstance().connectTo(addressArray[0], Integer.parseInt(addressArray[1]));
							
							currentIndex++;
						} else {
							currentList = 2;
							currentIndex = 0;
						}
						
						break;
					case 2:
						String[] oldPeers = GeoWeb.getInstance().getPeerListManager().getOldPeers();
						
						if(oldPeers.length > currentIndex) {
							String[] addressArray = oldPeers[currentIndex].split(":");
							GeoWeb.getInstance().connectTo(addressArray[0], Integer.parseInt(addressArray[1]));
							
							currentIndex++;
						} else {
							currentList = 0;
							currentIndex = 0;
							
							Thread.sleep(600000L);
							
						}
						
						break;
					}
				}
				
			}catch(InterruptedException e){
		        Thread.currentThread().interrupt(); // propagate interrupt
		    }
		}
	}

}
