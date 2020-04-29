package io.virgo.geoWeb.exceptions;

@SuppressWarnings("serial")

public class PortUnavailableException extends Exception {

	public PortUnavailableException(int port) {
		
		super("The port number " + port + " is unavailable.");
		
	}
	
}
