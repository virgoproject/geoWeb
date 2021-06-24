package io.virgo.geoWeb.utils;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.ServerSocket;
import java.util.regex.Pattern;

public class AddressUtils {

	/**
	 * Check if an hostname is valid
	 * @param hostname the hostname to check
	 * @return true if valid, false otherwise
	 */
	public static boolean isValidHostname(String hostname) {
        Pattern p = Pattern.compile("^"
                + "(((?!-)[A-Za-z0-9-]{1,63}(?<!-)\\.)+[A-Za-z]{2,}" // Domain name
                + "|"
                + "localhost" // localhost
                + "|"
                + "(([0-9]{1,3}\\.){3})[0-9]{1,3})"); // Ip
        
        if(p.matcher(hostname).matches())
        	return true;
        
        return false;
	}
	
	
	/**
	 * Check if an address:port is valid
	 * 
	 * @param address the address:port string to check
	 * @return true if valid, false otherwise
	 */
	public static boolean isValidHostnameAndPort(String address) {
		String[] splitedAddress = address.split(":");
		
		if(splitedAddress.length == 2) {
			try {
				
				int port = Integer.parseInt(splitedAddress[1]);
				return isValidHostname(splitedAddress[0]) && isValidPort(port);
						
			}catch(NumberFormatException e) {}
			
		}
        
        return false;
	}
	
	/**
	 * Checks to see if a specific port is available.
	 *
	 * @param port the port to check for availability
	 */
	public static boolean isPortAvailable(int port) throws IllegalArgumentException {
		
	    if (!isValidPort(port)) {
	        throw new IllegalArgumentException("Out of range port: " + port + ", should be between 1 and 65535");
	    }

	    ServerSocket ss = null;
	    DatagramSocket ds = null;
	    try {
	        ss = new ServerSocket(port);
	        ss.setReuseAddress(true);
	        ds = new DatagramSocket(port);
	        ds.setReuseAddress(true);
	        return true;
	    } catch (IOException e) {
	    } finally {
	        if (ds != null) {
	            ds.close();
	        }

	        if (ss != null) {
	            try {
	                ss.close();
	            } catch (IOException e) {
	                /* should not be thrown */
	            }
	        }
	    }

	    return false;
	}
	
	/**
	 * Check if a port is in range
	 * 
	 * @param port the port to validate
	 */
	public static boolean isValidPort(int port) {
		return !(port < 1 || port > 65535);
	}
	
}
