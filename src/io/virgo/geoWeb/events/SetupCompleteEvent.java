package io.virgo.geoWeb.events;

/**
 * Event occurring when GeoWeb setup has successfully completed and is ready to use
 */
public class SetupCompleteEvent extends Event {

	private long loadTime;
	
	public SetupCompleteEvent(long loadTime) {
		super(EventType.SETUP_COMPLETE);
		
		this.loadTime = loadTime;
	}

	/**
	 * @return the time it took to setup GeoWeb, in microseconds 
	 */
	public long getLoadTime() {
		return loadTime;
	}
	
}
