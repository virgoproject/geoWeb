package io.virgo.geoWeb;

import java.io.IOException;
import java.net.Socket;

class ConnectionRequestHandler implements Runnable{
	
	@Override
	public void run() {
		
		while(true) {
			
			try {
				Socket socket = GeoWeb.getInstance().getServer().accept();
				String address = socket.getInetAddress().getHostAddress() + ":" + socket.getPort();
				if(!GeoWeb.getInstance().peers.containsKey(address)) {
					new Thread(new Peer(socket, false)).start();
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
			
		}
		
	}

}
