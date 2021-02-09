package io.virgo.geoWeb;

import java.util.ArrayList;
import java.util.Arrays;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import io.virgo.geoWeb.events.DataRequestedEvent;
import io.virgo.geoWeb.events.PeerHandshakedEvent;
import io.virgo.geoWeb.utils.AddressUtils;
import io.virgo.virgoCryptoLib.Converter;
import io.virgo.virgoCryptoLib.Sha256Hash;

public class MessageHandler {

	//non-overrideable on-message logic
	final void superOnMessage(String message, Peer peer) {
		
		if(GeoWeb.getInstance().debugEnabled())
			System.out.println(message);
		
		try {
			
			JSONObject messageJson = new JSONObject(message);
			
			//Handshake logic
			if(messageJson.getString("command").equals("handshake")) {
				if(peer.handshaked)
					return;
				
				//Check if peer is part of the same network by comparing netIds
				if(!messageJson.has("netId") || messageJson.getLong("netId") != GeoWeb.getInstance().getNetId()) {
					peer.end();
					return;
				}
				
				//remove peer from pendingPeers before effectiveAddress port changes
				GeoWeb.getInstance().pendingPeers.remove(peer.getEffectiveAddress());
				
				if(messageJson.has("hostname"))
					peer.setHostname(messageJson.getString("hostname"));
				
				if(messageJson.has("port"))
					peer.setPort(messageJson.getInt("port"));
				
				//check if peer has the same session ID as us, if so end connection because we're probably try to connect to ourselves
				if(messageJson.has("id")) {
					peer.id = messageJson.getString("id");
					if(peer.getId().equals(GeoWeb.getInstance().getId()) || GeoWeb.getInstance().peersById.containsKey(peer.getId())) {
						GeoWeb.getInstance().blockedPeers.add(peer.getEffectiveAddress());
						peer.end();
						return;
					}
				}else {
					peer.end();
					return;
				}
				
				//update peer score
				GeoWeb.getInstance().getPeersCountWatchDog().updatePeerScore(peer.getAddress(), 1);
				
				peer.handshaked = true;
				
				//start to send messages to peer
				peer.startOutputWriter();
				
				peer.respondedToHeartbeat = true;//in case a heartbeat as been sent during setup
				
				//Exclude peer from broadcast if it wants to
				if(messageJson.has("acceptsBroadcast") && !messageJson.getBoolean("acceptsBroadcast"))
					peer.canBroadcast = false;	
				
				//Send handshake if not done yet
				if(!peer.sentHandshake)
					peer.sendHandshake();
				
				//add peer to list of ready ones
				GeoWeb.getInstance().peers.put(peer.getEffectiveAddress(), peer);
				GeoWeb.getInstance().peersById.put(peer.getId(), peer);
				
				GeoWeb.getInstance().getEventListener().notify(new PeerHandshakedEvent(peer));
				
			}else if(peer.handshakeDone()) {
			
				switch(messageJson.getString("command")) {
	
				case "getaddr"://peer is asking for current peer list
					ArrayList<String> peers = GeoWeb.getInstance().getCurrentPeersAddresses();
					peers.remove(peer.getEffectiveAddress());
					if(!peers.isEmpty()) {
						JSONObject response = new JSONObject();	
						response.put("command", "addr");
						response.put("addresses", new JSONArray(peers));
						
						peer.respondToMessage(response, messageJson);				
					}
					break;
					
				/*
				* Peer gives us a list of the peers it knows,
				* add them to list and try to connect to them if peer count target hasn't been reached
				*/
				case "addr":
					JSONArray addresses = messageJson.getJSONArray("addresses");
					for(int i = 0; i < addresses.length(); i++) {
						String peerAddress = addresses.getString(i);
						if(AddressUtils.isValidHostnameAndPort(peerAddress)) {
							String[] peerAddressArray = peerAddress.split(":");
							if(GeoWeb.getInstance().peers.size() < GeoWeb.getInstance().getPeerCountTarget() && !GeoWeb.getInstance().peers.containsKey(peerAddress) && !GeoWeb.getInstance().pendingPeers.containsKey(peerAddress))
								GeoWeb.getInstance().connectTo(peerAddressArray[0], Integer.parseInt(peerAddressArray[1]));
							
							GeoWeb.getInstance().getPeersCountWatchDog().addPossiblePeer(peerAddress, 0);
						}
						
						if(messageJson.has("share") && messageJson.getBoolean("share"))
							GeoWeb.getInstance().broadCast(messageJson, Arrays.asList(peer));
						
					}
					break;
					
				//Heartbeat, simply respond to ping with pong
				case "ping":
					JSONObject pongMessage = new JSONObject();
					pongMessage.put("command", "pong");
					peer.respondToMessage(pongMessage, messageJson);
					break;
				//Peer responded to our heartbeat
				case "pong":
					peer.respondedToHeartbeat = true;
					break;
				//Peer now accepts broadcasting
				case "acceptBroadcast":
					boolean value = messageJson.getBoolean("value");
					peer.canBroadcast = value;
					break;
				//Peer is requesting data
				case "requestData":
					Sha256Hash dataHash = new Sha256Hash(Converter.hexToBytes(messageJson.getString("hash")));
					GeoWeb.getInstance().getEventListener().notify(new DataRequestedEvent(dataHash, peer));
					break;
				}
				
				//Check if message has a respUid, if so it's a sync message: Fill SyncMessageresponse with message
				if(messageJson.has("respUid"))
					peer.reqResponses.put(messageJson.getString("respUid"), messageJson);
				
				//Execute overridable logic
				onMessage(messageJson, peer);
			}
		} catch(JSONException | IllegalArgumentException e) {
			
		}
		
	}
	
	/**
	 * overrideable method called after main logic for custom handler
	 */
	public void onMessage(JSONObject messageJson, Peer peer) {
		
	}
	
}
