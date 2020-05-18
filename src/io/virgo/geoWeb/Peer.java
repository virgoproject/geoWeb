package io.virgo.geoWeb;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.SocketException;
import java.util.HashMap;
import java.util.UUID;
import org.json.JSONObject;

import io.virgo.geoWeb.events.PeerConnectionEvent;
import io.virgo.geoWeb.events.PeerDisconnectionEvent;
import io.virgo.geoWeb.utils.AddressUtils;

public class Peer implements Runnable{

	private Socket socket;
	protected boolean sentHandshake = false;
	protected boolean handshaked = false;
	protected boolean canBroadcast = true;
	private boolean listen = true;
	protected boolean respondedToHeartbeat;
	private String hostname;
	private int port;
	private PrintWriter out;
	
	protected HashMap<String, JSONObject> reqResponses = new HashMap<String, JSONObject>();
	
	Peer(Socket socket, boolean initHandshake){
		this.socket = socket;
		this.hostname = socket.getInetAddress().getHostName();
		this.port = socket.getPort();
		
		try {
			socket.setReceiveBufferSize(2048);
			socket.setSendBufferSize(2048);
		} catch (SocketException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		
		GeoWeb.getInstance().peers.put(getEffectiveAddress(), this);
		
		try {
			out = new PrintWriter(socket.getOutputStream());
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		if(initHandshake) {
			sendHandshake();
		}
		
		GeoWeb.getInstance().getEventListener().notify(new PeerConnectionEvent(this));
	}
	
	@Override
	public void run() {
		
		try {
			BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
			StringBuilder sb = new StringBuilder();
			while(listen) {
				
				int ch;
				while((ch = in.read()) != -1) {
					if(ch == 10) {//char is NEWLINE
						String message = sb.toString();
						GeoWeb.getInstance().dispatchMessageTask(new MessageTask(message,this));
						sb.setLength(0);
					} else {
						sb.append((char) ch);
					}
				}
				
			}
			
			in.close();
			
		} catch (IOException e) {
			System.out.println(e.getMessage());
			end();
		}
		System.out.println("peer exited");
	}

	public String getEffectiveAddress() {
		return socket.getInetAddress().getHostAddress() + ":" + socket.getPort();
	}
	
	public boolean handshakeDone() {
		return handshaked;
	}
	
	/**
	 * Send an async message to the peer, so without waiting for it's response
	 * This method don't freeze the thread, ideal to use in an UI thread
	 * If you need a direct response to your message use {@link #sendSyncMessage(JSONObject) sendSyncMessage} in a thread that can be paused without negative effects
	 * on user experience
	 * 
	 * @param message the message to send, in form of a JSON object
	 */
	public synchronized void sendMessage(JSONObject message) {
		
		
		out.println(message.toString());
		out.flush();
			
	}
	
	protected void sendHandshake() {
		JSONObject netIdMessage = new JSONObject();
		netIdMessage.put("command", "handshake");
		netIdMessage.put("id", GeoWeb.getInstance().getNetId());
		
		if(!GeoWeb.getInstance().getHostname().equals(""))
			netIdMessage.put("hostname", GeoWeb.getInstance().getHostname());
		
		netIdMessage.put("port", GeoWeb.getInstance().getPort());
		
		sendMessage(netIdMessage);
	}
	
	/**
	 * Send a message to the peer and return it's response
	 * This method pause the thread from which it's invoked, so you shouldn't use it in an UI or other critical thread
	 * 
	 * @param message the message to send, in form of a JSON object
	 * @return {@link SyncMessageResponse} object, containing the message that the peer returned and a {@link SyncMessageResponseCode} object
	 */
	public SyncMessageResponse sendSyncMessage(JSONObject message) {
		String messageUid = UUID.randomUUID().toString();
		
		if(message.has("reqUid"))
			throw new IllegalArgumentException("the given message is using the reserved parameter reqUid. Message: "+message.toString());
		
		message.put("reqUid", messageUid);
		sendMessage(message);
		
		long timeoutTime = System.currentTimeMillis() + GeoWeb.getInstance().getSyncMessageTimeout();
		while(!reqResponses.containsKey(messageUid)) {
			if(System.currentTimeMillis() >= timeoutTime)
				return new SyncMessageResponse(ResponseCode.REQUEST_TIMEOUT);
			try {
				Thread.sleep(10);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		
		JSONObject responseJSON = reqResponses.get(messageUid);
		if(responseJSON.has("reqRespCode"))
			return new SyncMessageResponse(ResponseCode.fromCode(responseJSON.getInt("reqRespCode")), responseJSON);
		
		return new SyncMessageResponse(ResponseCode.UNKNOWN_CODE, responseJSON);
	}
	
	public void respondToMessage(JSONObject response, JSONObject responseTo, ResponseCode code) {
		response.put("reqRespCode", code.getCode());
		respondToMessage(response, responseTo);
	}
	
	public void respondToMessage(JSONObject response, JSONObject responseTo) {
		if(responseTo.has("reqUid"))
			response.put("respUid", responseTo.get("reqUid"));
		
		sendMessage(response);
	}
	
	public boolean isClosed() {
		return socket.isClosed();
	}
	
	public String getAddress() {
		return getHostname() + ":" + getPort();
	}
	
	public String getHostname() {
		return hostname;
	}
	
	public void setHostname(String hostname) {
		if(AddressUtils.isValidHostname(hostname))
			this.hostname = hostname;
	}
	
	public void setPort(int port) {
		if(AddressUtils.isValidPort(port))
			this.port = port;
	}
	
	public int getPort() {
		return port;
	}
	
	public void end() {
		listen = false;
		out.close();
		
		System.out.println(GeoWeb.getInstance().peers.size());
		GeoWeb.getInstance().peers.remove(getEffectiveAddress());
		System.out.println(GeoWeb.getInstance().peers.size());

		try {
			socket.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		GeoWeb.getInstance().getEventListener().notify(new PeerDisconnectionEvent(this));
	}
	
}
