package io.virgo.geoWeb;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.json.JSONObject;

import io.virgo.geoWeb.events.EventListener;
import io.virgo.geoWeb.events.SetupCompleteEvent;
import io.virgo.geoWeb.exceptions.PortUnavailableException;
import io.virgo.geoWeb.utils.AddressUtils;
import io.virgo.geoWeb.utils.PeerListManager;

public class GeoWeb {
	
	private static GeoWeb instance;
	
	private int port;
	private long netId;
	private long keepAlivePeriod;
	private long keepAliveTimeout;
	private long syncMessageTimeout;
	private long messageThreadKeepAliveTime;
	private int maxMessageThreadPoolSize;
	private String hostname;
	
	private ServerSocket server;
	private MessageHandler messageHandler;
	protected HashMap<String, Peer> peers = new HashMap<String, Peer>();
	private int peerCountTarget;
	private PeerListManager peerListManager;
	private EventListener eventListener;
	private ThreadPoolExecutor messageThreadPool;
	
	/**
	 * Creates a new geoWeb instance
	 * 
	 * @param port the port to use for communication
	 * @param netId a unique number wich identify your app network
	 * @param messageHandler a messageHandler instance (or a class instance that extends it)
	 * @param peerCountTarget the number of peers you tend to have
	 * 
	 * @throws PortUnavailableException The given port is used
	 * @throws IllegalArgumentException The given port is invalid (out of range) or messageHandler is null
	 * @throws IOException can't create a server instance
	 */
	private GeoWeb(Builder builder) throws IOException {
		instance = this;
		
		long setupStartTime = System.nanoTime();
		
		this.port = builder.port;
		this.netId = builder.netId;
		this.messageHandler = builder.messageHandler;
		this.eventListener = builder.eventListener;
		this.peerCountTarget = builder.peerCountTarget;
		this.keepAlivePeriod = builder.keepAlivePeriod;
		this.keepAliveTimeout = builder.keepAliveTimeout;
		this.syncMessageTimeout = builder.syncMessageTimeout;
		this.messageThreadKeepAliveTime = builder.messageThreadKeepAliveTime;
		this.maxMessageThreadPoolSize = builder.maxMessageThreadPoolSize;
		this.hostname = builder.hostname;
		
		messageThreadPool = (ThreadPoolExecutor) Executors.newFixedThreadPool(maxMessageThreadPoolSize);
		messageThreadPool.setKeepAliveTime(messageThreadKeepAliveTime, TimeUnit.MILLISECONDS);
		
		peerListManager = new PeerListManager();
		
		server = new ServerSocket(port);
		
		(new Thread(new ConnectionRequestHandler())).start();
		(new Thread(new PeersCountWatchdog())).start();
		(new Thread(eventListener)).start();
		
		new Timer().scheduleAtFixedRate(new TimerTask() {//heartbeat

			@Override
			public void run() {
				
				JSONObject pingMessage = new JSONObject();
				pingMessage.put("command", "ping");
				broadCast(pingMessage);
				
				new Timer().schedule(new TimerTask() {

					@Override
					public void run() {
						
						Map<String, Peer> peersMap = Collections.synchronizedMap(peers);
						
						for(Peer peer : peersMap.values()) {
						
							if(peer.respondedToHeartbeat && peer.handshaked) {
								peer.respondedToHeartbeat = false;
							} else {
								peer.end();
							}
							
						}
						
					}
					
				}, keepAliveTimeout);
			}
			
		}, 10000L, keepAlivePeriod);
		
		long setupCompleteTime = System.nanoTime();
		
		eventListener.notify( new SetupCompleteEvent(setupCompleteTime-setupStartTime) );
	}
	
	/**
	 * Try to connect to a new peer
	 * 
	 * @param hostname the IP or domain name of the machine to connect to
	 * @param port the port of the machine to connect to
	 * @return true if connected, false otherwise
	 */
	public boolean connectTo(String hostname, int port) {
		if(isSelf(hostname, port))
			return false;
			
		System.out.println("ss"+peers.size());
		
		try {
			String address = InetAddress.getByName(hostname).getHostAddress() + ":" + port;
			if(!peers.containsKey(address)) {
				Socket socket = new Socket(InetAddress.getByName(hostname), port);
				new Thread(new Peer(socket, true)).start();
				return true;
			}
			return false;
		} catch (IOException e) {
			getPeerListManager().retrograde(hostname+":"+port);
			return false;
		}
		
	}
	
	public ArrayList<String> getCurrentPeersAddresses() {
		return getCurrentPeersAddresses(new ArrayList<Peer>());
	}
	
	/**
	 * @return the list of known active peers addresses
	 */
	public ArrayList<String> getCurrentPeersAddresses(List<Peer> peersToIgnore) {
		ArrayList<String> addresses = new ArrayList<String>();
		
		Map<String, Peer> peersMap = Collections.synchronizedMap(peers);//should avoid concurrent access
		
		for(Peer peer : peersMap.values()) {
			if(!peersToIgnore.contains(peer)) {
				addresses.add(peer.getAddress());
			}
		}
		
		return addresses;
	}
	
	public ArrayList<Peer> getPeers() {
		return new ArrayList<Peer>(Collections.synchronizedMap(peers).values());
	}
	
	/**
	 * Send a message to all connected peers
	 * 
	 * @param message The message to send
	 * 
	 * The Json object must contain a string parameter called 'command' (witch is the subject of your message)
	 * otherwise it will be ignored by peers
	 */
	public void broadCast(JSONObject message) {
		
		Collection<Peer> peersList = Collections.synchronizedMap(peers).values();//should avoid concurrent access
		broadCast(message, peersList);
	}
	
	/**
	 * Send a message to all connected peers except given one
	 * 
	 * @param message The message to send
	 * @param peerToIgnore the peers to ignore
	 * 
	 * The Json object must contain a string parameter called 'command' (witch is the subject of your message)
	 * otherwise it will be ignored by peers
	 */
	public void broadCast(JSONObject message, List<Peer> peersToIgnore) {
		
		Collection<Peer> peersList = Collections.synchronizedMap(peers).values();
		peersList.removeAll(peersToIgnore);
		
		broadCast(message, peersList);
	}
	
	/**
	 * Send a message to target connected peers
	 * 
	 * @param message The message to send
	 * @param targetPeers the peers to send a message to
	 * 
	 * The Json object must contain a string parameter called 'command' (witch is the subject of your message)
	 * otherwise it will be ignored by peers
	 */
	public void broadCast(JSONObject message, Collection<Peer> targetPeers) {
		
		for(Peer peer : targetPeers) {
			if(peer.canBroadcast && !peer.isClosed()) {
				peer.sendMessage(message);
			}
		}
		
	}
	
	protected void dispatchMessageTask(MessageTask messageTask) {
		messageThreadPool.submit(messageTask);
	}
	
	public static GeoWeb getInstance() {
		return instance;
	}
	
	/**
	 * 
	 * @return the port the server is listening on
	 */
	public int getPort() {
		return port;
	}
	
	public EventListener getEventListener() {
		return eventListener;
	}
	
	public long getSyncMessageTimeout() {
		return syncMessageTimeout;
	}

	/**
	 * 
	 * @param syncMessageTimeout Must be >= 100
	 */
	public void setSyncMessageTimeout(long syncMessageTimeout) {
		if(syncMessageTimeout < 100)
			throw new IllegalArgumentException("sync message response timeout must be >= 100");
		
		this.syncMessageTimeout = syncMessageTimeout;
	}
	
	/**
	 * @return the identifier of the network GeoWeb is part of
	 */
	public long getNetId() {
		return netId;
	}

	public MessageHandler getMessageHandler() {
		return messageHandler;
	}

	public PeerListManager getPeerListManager() {
		return peerListManager;
	}

	public int getPeerCountTarget() {
		return peerCountTarget;
	}

	/**
	 * 
	 * @param peerCountTarget must be > 0
	 */
	public void setPeerCountTarget(int peerCountTarget) {
		if(peerCountTarget < 1)
			throw new IllegalArgumentException("peers count target must be positive");
		
		this.peerCountTarget = peerCountTarget;		
	}
	
	public ServerSocket getServer() {
		return server;
	}
	
	/**
	 * 
	 * @return the period on which GeoWeb check if it's peers are still responding via a ping message
	 */
	public long getKeepAlivePeriod() {
		return keepAlivePeriod;
	}
	
	/**
	 * 
	 * @return the time a peer has to respond to a ping message before being considered dead
	 */
	public long getKeepAliveTimeout() {
		return keepAliveTimeout;
	}
	
	/**
	 * sets the time a peer has to respond to a ping message before being considered dead
	 * @param keepAliveTimeout must be >= 100
	 */
	public void setKeepAliveTimeout(long keepAliveTimeout) {
		if(keepAliveTimeout < 100)
			throw new IllegalArgumentException("keep alive response timeout must be >= 100");
	
		this.keepAliveTimeout = keepAliveTimeout;
	}
	
	public long getMessageThreadkeepAliveTime() {
		return messageThreadKeepAliveTime;
	}
	
	/**
	 * 
	 * @param messageThreadKeepAliveTime must be >= 1000
	 */
	public void setMessageThreadKeepAliveTime(long messageThreadKeepAliveTime) {
		if(messageThreadKeepAliveTime < 1000)
			throw new IllegalArgumentException("messageThreadKeepAliveTime must be >= 1000");
		
		this.messageThreadKeepAliveTime = messageThreadKeepAliveTime;
		
		messageThreadPool.setKeepAliveTime(messageThreadKeepAliveTime, TimeUnit.MILLISECONDS);
	}
	
	public int getMaxMessageThreadPoolSize() {
		return maxMessageThreadPoolSize;
	}

	/**
	 * 
	 * @param maxMessageThreadPoolSize must be > 0
	 */
	public void setMaxMessageThreadPoolSize(int maxMessageThreadPoolSize) {
		if(maxMessageThreadPoolSize < 1)
			throw new IllegalArgumentException("maxMessageThreadPoolSize must be > 0");
		
		this.maxMessageThreadPoolSize = maxMessageThreadPoolSize;
		
		messageThreadPool.setMaximumPoolSize(maxMessageThreadPoolSize);
	}
	
	public String getHostname() {
		return hostname;
	}
	
	public void setHostname(String hostname) {
		if(!AddressUtils.isValidHostname(hostname))
			throw new IllegalArgumentException("Invalid hostname");
		
		this.hostname = hostname;
	}
	
	public boolean isSelf(String host, int port) {
		try {
			return AddressUtils.isThisMyIpAddress(InetAddress.getByName(host)) && port == getPort();
		} catch (UnknownHostException e) {
			return false;
		}
	}	
	
	public static class Builder {
		
		private int port = 25565;
		private long netId = -1;
		private int peerCountTarget = 8;
		private long keepAlivePeriod = 600000L;
		private long keepAliveTimeout = 5000L;
		private long syncMessageTimeout = 60000L;
		private long messageThreadKeepAliveTime = 60000L;
		private int maxMessageThreadPoolSize = 10;
		private String hostname = "";
		private MessageHandler messageHandler = null;
		private EventListener eventListener = null;
		
		public GeoWeb build() throws IOException {
			
			if(netId == -1)
				throw new IllegalArgumentException("NetId must be defined");
			
			if(messageHandler == null)
				messageHandler = new MessageHandler();
			
			if(eventListener == null) {
				eventListener = new EventListener();
			}
			
			return new GeoWeb(this);
		}
		
		public Builder port(int port) throws PortUnavailableException {
			if(!AddressUtils.isPortAvailable(port))
				throw new PortUnavailableException(port);
			
			this.port = port;
			
			return this;
		}
		
		public Builder netID(long netId) {
			if(netId < 1)
				throw new IllegalArgumentException("NetID must be > 0");
			
			this.netId = netId;
			
			return this;
		}
		
		public Builder peerCountTarget(int peerCountTarget) {
			if(peerCountTarget < 1)
				throw new IllegalArgumentException("peers count target must be > 0");
			
			this.peerCountTarget = peerCountTarget;
		
			return this;
		}
		
		public Builder keepAlivePeriod(long keepAlivePeriod) {
			if(keepAlivePeriod < 5000)
				throw new IllegalArgumentException("keep alive period must be >= 5000");
			this.keepAlivePeriod = keepAlivePeriod;
		
			return this;
		}
		
		public Builder keepAliveTimeout(long keepAliveTimeout) {
			if(keepAliveTimeout < 100)
				throw new IllegalArgumentException("keep alive response timeout must be >= 100");
		
			this.keepAliveTimeout = keepAliveTimeout;
		
			return this;
		}
		
		public Builder syncMessageTimeout(long syncMessageTimeout) {
			if(syncMessageTimeout < 100)
				throw new IllegalArgumentException("sync message response timeout must be >= 100");
			
			this.syncMessageTimeout = syncMessageTimeout;
		
			return this;
		}
		
		public Builder messageThreadKeepAliveTime(long messageThreadKeepAliveTime) {
			if(messageThreadKeepAliveTime < 1000)
				throw new IllegalArgumentException("messageThreadKeepAliveTime must be >= 1000");
			
			this.messageThreadKeepAliveTime = messageThreadKeepAliveTime;
			
			return this;
		}
		
		public Builder maxMessageThreadPoolSize(int maxMessageThreadPoolSize) {
			if(maxMessageThreadPoolSize < 1)
				throw new IllegalArgumentException("maxMessageThreadPoolSize must be > 0");
			
			this.maxMessageThreadPoolSize = maxMessageThreadPoolSize;
			
			return this;
		}
		
		public Builder hostname(String hostname) {
			if(!AddressUtils.isValidHostname(hostname))
				throw new IllegalArgumentException("Invalid hostname");
			
			this.hostname = hostname;
			
			return this;
		}
		
		public Builder messageHandler(MessageHandler messageHandler) {			
			this.messageHandler = messageHandler;
		
			return this;
		}
		
		public Builder eventListener(EventListener eventListener) {
			this.eventListener = eventListener;
		
			return this;
		}
		
	}
	
}
