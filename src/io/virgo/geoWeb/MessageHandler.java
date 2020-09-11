package io.virgo.geoWeb;

import java.util.ArrayList;
import java.util.Arrays;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import io.virgo.geoWeb.events.DataRequestedEvent;
import io.virgo.geoWeb.events.PeerConnectionEvent;
import io.virgo.geoWeb.events.PeerHandshakedEvent;
import io.virgo.geoWeb.utils.AddressUtils;
import io.virgo.virgoCryptoLib.Converter;
import io.virgo.virgoCryptoLib.Sha256Hash;

public class MessageHandler {

	//non-overrideable on-message logic
	final void superOnMessage(String message, Peer peer) {
		
		try {
			
			JSONObject messageJson = new JSONObject(message);
			
			if(messageJson.getString("command").equals("handshake")) {
				if(peer.handshaked)
					return;
				
				if(!messageJson.has("id") || messageJson.getLong("id") != GeoWeb.getInstance().getNetId()) {
					peer.end();
					return;
				}
				
				if(messageJson.has("hostname"))
					peer.setHostname(messageJson.getString("hostname"));
				
				if(messageJson.has("port"))
					peer.setPort(messageJson.getInt("port"));
				
				GeoWeb.getInstance().getPeerListManager().addPeer(peer.getAddress(), 0);
				peer.handshaked = true;
				peer.respondedToHeartbeat = true;//in case a heartbeat as been sent during setup
				
				if(messageJson.has("acceptsBroadcast") && !messageJson.getBoolean("acceptsBroadcast"))
					peer.canBroadcast = false;	
				
				if(!peer.sentHandshake)
					peer.sendHandshake();
				
				GeoWeb.getInstance().getEventListener().notify(new PeerHandshakedEvent(peer));
				
			}else if(peer.handshakeDone()) {
			
				switch(messageJson.getString("command")) {
	
				case "getaddr"://peer is asking for current peer list
					ArrayList<String> peers = GeoWeb.getInstance().getCurrentPeersAddresses();
					if(!peers.isEmpty()) {
						JSONObject response = new JSONObject();	
						response.put("command", "addr");
						response.put("addresses", new JSONArray(peers));
						
						peer.respondToMessage(response, messageJson);				
					}
					break;
				case "addr":
					JSONArray addresses = messageJson.getJSONArray("addresses");
					for(int i = 0; i < addresses.length(); i++) {
						String peerAddress = addresses.getString(i);
						if(AddressUtils.isValidHostnameAndPort(peerAddress)) {
							String[] peerAddressArray = peerAddress.split(":");
							if(!GeoWeb.getInstance().isSelf(peerAddressArray[0], Integer.parseInt(peerAddressArray[1]))){
								
								if(GeoWeb.getInstance().peers.size() < GeoWeb.getInstance().getPeerCountTarget() && !GeoWeb.getInstance().peers.containsKey(peerAddress)) {
									GeoWeb.getInstance().connectTo(peerAddressArray[0], Integer.parseInt(peerAddressArray[1]));
									GeoWeb.getInstance().getPeerListManager().addPeer(peerAddress, 0);
								} else {
									GeoWeb.getInstance().getPeerListManager().addPeer(peerAddress, 1);
								}
								
							}
						}
						
						if(messageJson.has("share") && messageJson.getBoolean("share"))
							GeoWeb.getInstance().broadCast(messageJson, Arrays.asList(peer));
						
					}
					break;
				case "ping":
					JSONObject pongMessage = new JSONObject();
					pongMessage.put("command", "pong");
					peer.respondToMessage(pongMessage, messageJson);
					break;
				case "pong":
					peer.respondedToHeartbeat = true;
					break;
				case "acceptBroadcast":
					boolean value = messageJson.getBoolean("value");
					peer.canBroadcast = value;
					break;
				case "requestData":
					Sha256Hash dataHash = new Sha256Hash(Converter.hexToBytes(messageJson.getString("hash")));
					GeoWeb.getInstance().getEventListener().notify(new DataRequestedEvent(dataHash, peer));
					break;
				}
				
				if(messageJson.has("respUid"))
					peer.reqResponses.put(messageJson.getString("respUid"), messageJson);
					
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
