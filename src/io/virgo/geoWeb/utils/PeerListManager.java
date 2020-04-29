package io.virgo.geoWeb.utils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;

public class PeerListManager {

	private File currentPeersFile;
	private File recentPeersFile;
	private File oldPeersFile;
	
	private ArrayList<String> currentPeers = new ArrayList<String>();
	private ArrayList<String> recentPeers = new ArrayList<String>();
	private ArrayList<String> oldPeers = new ArrayList<String>();
	
	public PeerListManager() {
		
		currentPeersFile = new File("currentPeers.txt");
		recentPeersFile = new File("recentPeers.txt");
		oldPeersFile = new File("oldPeers.txt");
		
		try {//create files if don't exist (createNewFile method already check for existence)
			currentPeersFile.createNewFile();
			recentPeersFile.createNewFile();
			oldPeersFile.createNewFile();
		} catch (IOException e) {
			System.out.println("Error: can't write file, please check for permissions or free disk space");
			e.printStackTrace();
		}
		
		
		//load addresses into arraylists
		try (BufferedReader br = new BufferedReader(new FileReader(currentPeersFile))) {//current
		    String address;
		    while ((address = br.readLine()) != null) {
		       
		    	if(AddressUtils.isValidHostnameAndPort(address)) {
		    		currentPeers.add(address);
		    	}
		    	
		    }
		} catch (FileNotFoundException e) {
			//should never happen 
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		try (BufferedReader br = new BufferedReader(new FileReader(recentPeersFile))) {//recent
		    String address;
		    while ((address = br.readLine()) != null) {
		       
		    	if(AddressUtils.isValidHostnameAndPort(address)) {
		    		recentPeers.add(address);
		    	}
		    	
		    }
		} catch (FileNotFoundException e) {
			//should never happen 
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		try (BufferedReader br = new BufferedReader(new FileReader(oldPeersFile))) {//old
		    String address;
		    while ((address = br.readLine()) != null) {
		       
		    	if(AddressUtils.isValidHostnameAndPort(address)) {
		    		oldPeers.add(address);
		    	}
		    	
		    }
		} catch (FileNotFoundException e) {
			//should never happen 
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {//save peers on program exit

			@Override
			public void run() {
				
				try {//save current peers
					currentPeersFile.delete();
					currentPeersFile.createNewFile();
					
					FileWriter writer = new FileWriter(currentPeersFile); 
					for(String peer : currentPeers) {
					  writer.write(peer);
					}
					writer.close();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				
				try {//save recent peers
					recentPeersFile.delete();
					recentPeersFile.createNewFile();
					
					FileWriter writer = new FileWriter(recentPeersFile); 
					for(String peer : recentPeers) {
					  writer.write(peer);
					}
					writer.close();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				
				try {//save old peers
					oldPeersFile.delete();
					oldPeersFile.createNewFile();
					
					FileWriter writer = new FileWriter(oldPeersFile); 
					for(String peer : currentPeers) {
					  writer.write(peer);
					}
					writer.close();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				
			}
			
		}));
		
		//TODO Save lists every hour
	}
	
	/**
	 * Add peer to choosed list and remove it from others if exist
	 * 
	 * Note: the address validity should be checked first
	 * 
	 * @param address The address:port to add
	 * @param listId The list where the peer should be added (0 = current peers, 1 = recent peers, 2 = old peers)
	 */
	public void addPeer(String address, int listId) {
		
		switch(listId) {
			case 0:
				if(!currentPeers.contains(address)) {
					currentPeers.add(address);
					recentPeers.remove(address);
					oldPeers.remove(address);
				}
				break;
			case 1:
				if(!recentPeers.contains(address)) {
					recentPeers.add(address);
					currentPeers.remove(address);
					oldPeers.remove(address);
				}
				break;
			case 2:
				if(!oldPeers.contains(address)) {
					oldPeers.add(address);
					recentPeers.remove(address);
					currentPeers.remove(address);
				}
				break;
				default:
					if(!oldPeers.contains(address)) {
						oldPeers.add(address);
						recentPeers.remove(address);
						currentPeers.remove(address);
					}
		}
		
	}
	
	/**
	 * Move a peer address one list down, do nothing if already in list 2 or don't exist 
	 * 
	 * @param address the address to retrograde
	 */
	public void retrograde(String address) {
		
		if(currentPeers.contains(address)) {
			currentPeers.remove(address);
			recentPeers.add(address);
			return;
		}
		
		if(recentPeers.contains(address)) {
			recentPeers.remove(address);
			oldPeers.add(address);
		}
		
	}
	
	public String[] getCurrentPeers() {
		return currentPeers.toArray(new String[currentPeers.size()]);
	}
	
	public String[] getRecentPeers() {
		return recentPeers.toArray(new String[recentPeers.size()]);
	}
	
	public String[] getOldPeers() {
		return oldPeers.toArray(new String[oldPeers.size()]);
	}
	
}