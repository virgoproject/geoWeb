package io.virgo.geoWeb.events;

public class SetupCompleteEvent extends Event {

	private long loadTime;
	
	public SetupCompleteEvent(long loadTime) {
		super(EventType.SETUP_COMPLETE);
		
		this.loadTime = loadTime;
	}

	public long getLoadTime() {
		return loadTime;
	}
	
}
