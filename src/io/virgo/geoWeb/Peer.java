package io.virgo.geoWeb;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.BufferUnderflowException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.UUID;
import org.json.JSONObject;

import io.virgo.geoWeb.events.PeerConnectionEvent;
import io.virgo.geoWeb.events.PeerDisconnectionEvent;
import io.virgo.geoWeb.utils.AddressUtils;
import io.virgo.geoWeb.utils.Miscellaneous;
import io.virgo.virgoCryptoLib.Converter;
import io.virgo.virgoCryptoLib.Sha256Hash;

/**
 * Runnable representing connection to a peer
 */
public class Peer implements Runnable{

	private Socket socket;
	protected boolean sentHandshake = false;
	protected boolean handshaked = false;
	protected boolean canBroadcast = true;
	private boolean listen = true;
	protected boolean respondedToHeartbeat;
	private String hostname;
	private int port;
	private OutputStream out;
	
	private static byte[] JSON_MSG_IDENTIFIER = new byte[] {(byte) 0x05};
	private static byte[] DATA_MSG_IDENTIFIER = new byte[] {(byte) 0x02};
	
	protected HashMap<String, JSONObject> reqResponses = new HashMap<String, JSONObject>();
	HashMap<Sha256Hash, DataRequest> requestedData = new HashMap<Sha256Hash, DataRequest>();
	
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

		try {
			out = socket.getOutputStream();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		if(initHandshake) {
			sendHandshake();
		}
		
		GeoWeb.getInstance().pendingPeers.put(getEffectiveAddress(), this);
		
		GeoWeb.getInstance().getEventListener().notify(new PeerConnectionEvent(this));
	}
	
	/**
	 * Listen to input stream and try to parse messages from it
	 * First Byte of a message is it's type, either JSON or Data request
	 * Four next bytes are the body length, or how much bytes we need to read next to reach end of message
	 * 
	 */
	@Override
	public void run() {
		
		try {
			InputStream in = socket.getInputStream();
			
			while(listen) {
				
				byte[] msgType = in.readNBytes(1);
				
				byte[] msgLengthBytes = in.readNBytes(4);
				
				int msgLength = ByteBuffer.wrap(msgLengthBytes).getInt();
				
				//Message is JSON, read then create messageTask and dispatch it to threadPool
				if(Arrays.equals(msgType, JSON_MSG_IDENTIFIER)) {
					
					byte[] data = new byte[msgLength];
					
					int received = 0;
					
					int readedBytes = 0;
					
					while(readedBytes >= 0) {
						readedBytes = in.read(data, received, msgLength-received);
						
						received += readedBytes;
						
						if(received >= msgLength)
							break;
					}
					
					String dataString = new String(data);
					
					GeoWeb.getInstance().dispatchMessageTask(new MessageTask(dataString, this));
				
					/* Message is a data request, next 32 bytes are the data hash
					 * if the hash doesn't correspond to any requested data throw an Exception and terminate connection
					 * Otherwise read the rest of the message and populate corresponding DataRequest
					 */
				}else if(msgType.equals(DATA_MSG_IDENTIFIER)) {
					
					Sha256Hash dataHash = new Sha256Hash(in.readNBytes(32));
					
					if(!requestedData.containsKey(dataHash))
						throw new IOException("remote sent non requested data");
					
					DataRequest recipient = requestedData.get(dataHash);
					
					try {
						recipient.prepare(msgLength);
						
						int readedBytes = 0;
						
						while(readedBytes >= 0) {
							readedBytes = in.read(recipient.data, recipient.received, msgLength-recipient.received);
							
							recipient.received += readedBytes;
							
							if(recipient.received >= msgLength)
								break;
						}
						
						recipient.finish();
					}catch(IOException e) {
						recipient.finish();
					}
					

				}
				
			}
			
			in.close();
			
		} catch (IOException | BufferUnderflowException e) {}
		
		end();
	}

	public String getEffectiveAddress() {
		return socket.getInetAddress().getHostAddress() + ":" + getPort();
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
		
		byte[] msgBytes = message.toString().getBytes();
		
		try {
			out.write(JSON_MSG_IDENTIFIER);
			out.write(Miscellaneous.intToBytes(msgBytes.length));
			out.write(msgBytes);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
			
	}
	
	/**
	 * Output data to peer to respond to data request, please use DataRequestedEvent.uploadData(byte[] data) instead
	 */
	public void sendData(byte[] data, byte[] hash) {
		try {
			out.write(DATA_MSG_IDENTIFIER);
			out.write(Miscellaneous.intToBytes(data.length));
			out.write(hash);
			out.write(data);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	/**
	 * Send handshake to peer to finish setup
	 */
	protected void sendHandshake() {
		JSONObject netIdMessage = new JSONObject();
		netIdMessage.put("command", "handshake");
		netIdMessage.put("netId", GeoWeb.getInstance().getNetId());
		netIdMessage.put("id", GeoWeb.getInstance().getId());
		
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
	
	/**
	 * Respond to a SyncMessage
	 * @param response your response as a JSONObject
	 * @param responseTo the message you want to respond to
	 * @param code The response Code
	 */
	public void respondToMessage(JSONObject response, JSONObject responseTo, ResponseCode code) {
		response.put("reqRespCode", code.getCode());
		respondToMessage(response, responseTo);
	}
	
	
	/**
	 * Respond to a SyncMessage
	 * @param response your response as a JSONObject
	 * @param responseTo the message you want to respond to
	 */
	public void respondToMessage(JSONObject response, JSONObject responseTo) {
		if(responseTo.has("reqUid"))
			response.put("respUid", responseTo.get("reqUid"));
		
		sendMessage(response);
	}
	
	/**
	 * Send a data request to peer
	 * @param dataReq the data you want
	 */
	public void requestData(DataRequest dataReq) {
		JSONObject reqMessage = new JSONObject();
		reqMessage.put("command", "requestData");
		reqMessage.put("hash", Converter.bytesToHex(dataReq.getHash().toBytes()));
		
		sendMessage(reqMessage);
		
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
	
	/**
	 * End connection to peer
	 */
	public void end() {
		
		listen = false;
		try {
			out.close();
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		
		GeoWeb.getInstance().peers.remove(getEffectiveAddress());
		GeoWeb.getInstance().pendingPeers.remove(getEffectiveAddress());
		
		try {
			socket.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		GeoWeb.getInstance().getEventListener().notify(new PeerDisconnectionEvent(this));
	}
	
}
