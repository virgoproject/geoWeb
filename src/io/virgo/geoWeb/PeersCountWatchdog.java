package io.virgo.geoWeb;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import io.virgo.geoWeb.utils.AddressUtils;
import io.virgo.geoWeb.utils.Miscellaneous;

/**
 * Runnable class trying to contact new peers if target peer count hasn't been reached
 * 
 * When we succeed to connect to a peer, it's score get +1, when we fail it gets -1 (max 1 modification per 10 minutes)
 * We loop trough possible peers from highest score to lowest score, then update scores and repeat
 */
class PeersCountWatchdog implements Runnable {

	private volatile HashMap<String, Integer> possiblePeers = new HashMap<String, Integer>(); 
	private volatile HashMap<String, Long> peersLastScoreUpdate = new HashMap<String, Long>(); 
	
	private File possiblePeersFile;

	PeersCountWatchdog(){
		
		possiblePeersFile = new File("peers.json");
		
		loadPeers();
		
		//message to broadcast to get new peers addresses
		JSONObject getaddrMessage = new JSONObject();
		getaddrMessage.put("command", "getaddr");
		
		//try to get new addresses every 10 minutes and save current list
		Timer addressBroadcastTimer = new Timer();
		GeoWeb.getInstance().timers.add(addressBroadcastTimer);
		addressBroadcastTimer.scheduleAtFixedRate(new TimerTask() {

			@Override
			public void run() {
				GeoWeb.getInstance().broadCast(getaddrMessage);
				
				savePeers();
			}
			
		}, 10000L, 600000L);
		
		Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {//save peers on program exit

			@Override
			public void run() {
				savePeers();
			}
			
		}));
		
	}
	
	@Override
	public void run() {
		
		ArrayList<String> peersByScore = getPeersByScore();
		int currentIndex = 0;
		
		while(!Thread.currentThread().isInterrupted()) {
			try {
				
				if(peersByScore.size() == 0) {
					peersByScore = getPeersByScore();
					Thread.sleep(1000);
					continue;
				}
				
				if(GeoWeb.getInstance().peers.size() >= GeoWeb.getInstance().getPeerCountTarget()) {
					Thread.sleep(1000);
					continue;
				}
				
				String[] addressArray = peersByScore.get(currentIndex).split(":");

				try {
					
					if(GeoWeb.getInstance().connectTo(addressArray[0], Integer.parseInt(addressArray[1])))
						updatePeerScore(peersByScore.get(currentIndex), 1);
					else
						updatePeerScore(peersByScore.get(currentIndex), -1);
					
				}catch(NumberFormatException e) {
					possiblePeers.remove(peersByScore.get(currentIndex));
				}
				
				currentIndex++;
				
				if(currentIndex >= peersByScore.size()) {
					currentIndex = 0;
					peersByScore = getPeersByScore();
				}
				
				Thread.sleep(1000);
				
			}catch(InterruptedException e){
		        Thread.currentThread().interrupt(); // propagate interrupt
		    }
		}
	}
	
	private void loadPeers() {
		
		try {//create file if don't exist (createNewFile method already checks for existence)
			possiblePeersFile.createNewFile();
		} catch (IOException e) {
			System.out.println("Error: can't write peers.json file, please check for permissions or free disk space");
			e.printStackTrace();
		}
		
		if(possiblePeersFile.exists()) {
			
			String peersString = Miscellaneous.fileToString("peers.json");
			
			try {
				JSONArray peersArray = new JSONArray(peersString);
				
				for(int i = 0; i < peersArray.length(); i++) {
					
					try {
						JSONObject possiblePeer = peersArray.getJSONObject(i);
						addPossiblePeer(possiblePeer.getString("host"), possiblePeer.getInt("score"));
					}catch(JSONException e) {
						//re-catch JSONException so we continue reading through array and maybe get valid peers
					}

					
				}
				
			}catch(JSONException e) {
				//we'll save peers in a valid format on shutdown
			}
			
		}
		
	}
	
	/**
	 * Save current possible peers list to peers.json
	 * Overrides current file value
	 */
	private void savePeers() {
		JSONArray peersArray = new JSONArray();
		
		for(String key : possiblePeers.keySet()) {
			
			JSONObject possiblePeer = new JSONObject();
			possiblePeer.put("host", key);
			possiblePeer.put("score", possiblePeers.get(key));
			
			peersArray.put(possiblePeer);
		}
		
		try {
			FileWriter writer = new FileWriter(possiblePeersFile, false);
			writer.write(peersArray.toString());
			writer.close();
		} catch (IOException e) {
			System.out.println("Couldn't write peers.json: " + e.getMessage());
		}
		
	}
	
	/**
	 * Add an address to the possible peers list
	 * @param address the address of the possible peer
	 * @param baseScore it's base score
	 */
	public void addPossiblePeer(String address, int baseScore) {
		if(AddressUtils.isValidHostnameAndPort(address) && !possiblePeers.containsKey(address))
			updatePeerScore(address, baseScore);
	}
	
	/**
	 * Update a possible peer's score or set it of none yet
	 * @param address The target possible peer's address
	 * @param modifier The score modifier
	 */
	public void updatePeerScore(String address, int modifier) {
		if(peersLastScoreUpdate.getOrDefault(address, 0l) > System.currentTimeMillis()-6000000)//limit update rate to 10min
			return;
			
		int newScore = possiblePeers.getOrDefault(address, 0)+modifier;
		possiblePeers.put(address, newScore);
		peersLastScoreUpdate.put(address, System.currentTimeMillis());
	}

	/**
	 * @return A list of known possible peers, sorted by their descending score
	 */
	public ArrayList<String> getPeersByScore() {
		List<Integer> values = new ArrayList<Integer>(possiblePeers.values());
		Collections.sort(values);
		
		List<String> keys = new ArrayList<String>(possiblePeers.keySet());
		
		Iterator<Integer> valuesIterator = values.iterator();
		
		ArrayList<String> sortedPeers = new ArrayList<String>();
		
		while(valuesIterator.hasNext()) {
			Iterator<String> keysIterator = keys.iterator();
			int score = valuesIterator.next();
			
			while(keysIterator.hasNext()) {
				String possiblePeer = keysIterator.next();
				
				if(score == possiblePeers.get(possiblePeer)) {
					sortedPeers.add(possiblePeer);
					keys.remove(possiblePeer);
					break;
				}
				
			}
			
		}
		
		return sortedPeers;
	}
	
}
