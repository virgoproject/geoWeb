package io.virgo.geoWeb;

import java.util.Timer;
import java.util.TimerTask;

import org.json.JSONObject;

class PeersCountWatchdog implements Runnable {

	//TODO faire mieux que ça
	
	int currentList = 0;
	int currentIndex = 0;
	
	PeersCountWatchdog(){
		
		JSONObject getaddrMessage = new JSONObject();
		getaddrMessage.put("command", "getaddr");
		
		Timer addressBroadcastTimer = new Timer();
		GeoWeb.getInstance().timers.add(addressBroadcastTimer);
		addressBroadcastTimer.scheduleAtFixedRate(new TimerTask() {//try to get new addresses every 10 minutes if desired peers count not reached

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
