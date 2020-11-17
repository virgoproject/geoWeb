package io.virgo.geoWeb;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.json.JSONObject;

import io.virgo.geoWeb.events.EventListener;
import io.virgo.geoWeb.events.SetupCompleteEvent;
import io.virgo.geoWeb.exceptions.PortUnavailableException;
import io.virgo.geoWeb.utils.AddressUtils;
import io.virgo.geoWeb.utils.PeerListManager;

/**
 * Virgo's Peer to Peer communication library
 * Main class
 */
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
	
	private String id;
	
	private ServerSocket server;
	private MessageHandler messageHandler;
	protected HashMap<String, Peer> peers = new HashMap<String, Peer>();
	protected HashMap<String, Peer> pendingPeers = new HashMap<String, Peer>();
	protected ArrayList<String> blockedPeers = new ArrayList<String>();
	private int peerCountTarget;
	private PeerListManager peerListManager;
	private EventListener eventsListener;
	
	private ThreadPoolExecutor messageThreadPool;
	public ArrayList<Thread> threads = new ArrayList<Thread>();
	public ArrayList<Timer> timers = new ArrayList<Timer>();
	
	/**
	 * create geoWeb instance from builder parameters
	 */
	private GeoWeb(Builder builder) throws IOException {
		instance = this;
		
		long setupStartTime = System.nanoTime();
		
		//set parameters from Builder
		this.port = builder.port;
		this.netId = builder.netId;
		this.messageHandler = builder.messageHandler;
		this.eventsListener = builder.eventsListener;
		this.peerCountTarget = builder.peerCountTarget;
		this.keepAlivePeriod = builder.keepAlivePeriod;
		this.keepAliveTimeout = builder.keepAliveTimeout;
		this.syncMessageTimeout = builder.syncMessageTimeout;
		this.messageThreadKeepAliveTime = builder.messageThreadKeepAliveTime;
		this.maxMessageThreadPoolSize = builder.maxMessageThreadPoolSize;
		this.hostname = builder.hostname;
		
		//generate a unique ID for this geoWeb session, will serve to know when we try to connect to ourselves
		id = UUID.randomUUID().toString();
		
		messageThreadPool = (ThreadPoolExecutor) Executors.newFixedThreadPool(maxMessageThreadPoolSize);
		messageThreadPool.setKeepAliveTime(messageThreadKeepAliveTime, TimeUnit.MILLISECONDS);
		
		peerListManager = new PeerListManager();
		
		server = new ServerSocket(port);
		
		Thread connectionRequestsThread = new Thread(new ConnectionRequestHandler());
		connectionRequestsThread.start();
		threads.add(connectionRequestsThread);
		
		Thread peersCountWatchdogThread = new Thread(new PeersCountWatchdog());
		peersCountWatchdogThread.start();
		threads.add(peersCountWatchdogThread);
		
		Thread eventsListenerThread = new Thread(eventsListener);
		eventsListenerThread.start();
		threads.add(eventsListenerThread);
		
		/**
		 * Send a message to all peers every x seconds and wait for their response to see if they are still alive
		 * if not disconnected from them
		 */
		Timer heartbeatTimer = new Timer();
		timers.add(heartbeatTimer);
		heartbeatTimer.scheduleAtFixedRate(new TimerTask() {//heartbeat

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
		
		eventsListener.notify( new SetupCompleteEvent(setupCompleteTime-setupStartTime) );
	}
	
	/**
	 * Disconnect peers and close all running threads
	 * GeoWeb will stop working after calling this function
	 */
	public void shutdown() {
		
		for(Thread thread : threads)
			thread.interrupt();
			
		
		for(Timer timer : timers)
			timer.cancel();
		
		for(Peer peer : peers.values())
			peer.end();
		
		try {
			server.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		messageThreadPool.shutdown();
	}
	
	/**
	 * Try to connect to a new peer
	 * 
	 * @param hostname the IP or domain name of the machine to connect to
	 * @param port the port of the machine to connect to
	 * @return true if connected, false otherwise
	 */
	public boolean connectTo(String hostname, int port) {
		
		System.out.println("trying to connect to " + hostname + ":" + port);
		
		if(blockedPeers.contains(hostname+":"+port))
			return false;
		
		System.out.println("connecting");
		
		try {
			String address = InetAddress.getByName(hostname).getHostAddress() + ":" + port;
			if(!peers.containsKey(address) && !pendingPeers.containsKey(address)) {
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
	
	/**
	 * @return the list of known active peers
	 */
	public ArrayList<String> getCurrentPeersAddresses() {
		return getCurrentPeersAddresses(new ArrayList<Peer>());
	}
	
	/**
	 * @param peersToIgnore A list of peers to exclude from result
	 * @return the list of known active peers
	 */
	public ArrayList<String> getCurrentPeersAddresses(List<Peer> peersToIgnore) {
		ArrayList<String> addresses = new ArrayList<String>();
		
		Map<String, Peer> peersMap = Collections.synchronizedMap(peers);//should avoid concurrent access
		
		for(Peer peer : peersMap.values()) {
			if(!peersToIgnore.contains(peer)) {
				addresses.add(peer.getEffectiveAddress());
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
	 * <br>
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
		return eventsListener;
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
	 * @return The ID of this geoWeb session
	 */
	public String getId() {
		return id;
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
	 * @param maxMessageThreadPoolSize Maximal number of threads GeoWeb will use to handle messages, must be > 0
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
	
	/**
	 * Check if given IP address and port correspond to this geoWeb instance
	 * @param host IP address to check
	 * @param port Port to check
	 * @return <b>true</b> if given IP address and port correspond to this geoWeb instance, otherwise <b>false</b>
	 */
	/**public boolean isSelf(String host, int port) {
		try {
			System.out.println("Is self "+host+":"+port+" ? " + AddressUtils.isThisMyIpAddress(InetAddress.getByName(host)) + " " + (port == getPort()));
			return AddressUtils.isThisMyIpAddress(InetAddress.getByName(host)) && port == getPort();
		} catch (UnknownHostException e) {
			System.out.println(e.getMessage());
			return false;
		}
	}*/
	
	/**
	 * New geoWeb instance builder
	 * 
	 * <p>
	 * Example:<br><br>
	 * {@code GeoWeb net = new GeoWeb.Builder()}<br>
	 * {@code .netID(2946073207412533257l)}<br>
	 * {@code .eventListener(new EventListener())}<br>
	 * {@code .messageHandler(new MessageHandler())}<br>
	 * {@code .port(1234)}<br>
	 * {@code .build();}
	 * <p>
	 * 
	 */
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
		private EventListener eventsListener = null;
		
		public GeoWeb build() throws IOException {
			
			if(netId == -1)
				throw new IllegalArgumentException("NetId must be defined");
			
			if(messageHandler == null)
				messageHandler = new MessageHandler();
			
			if(eventsListener == null) {
				eventsListener = new EventListener();
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
			this.eventsListener = eventListener;
		
			return this;
		}
		
	}
	
}
