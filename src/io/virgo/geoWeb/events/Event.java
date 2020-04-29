package io.virgo.geoWeb.events;

public abstract class Event {
	
	private EventType eventType;
	
	public Event(EventType eventType) {
		this.eventType = eventType;
	}

	public EventType getType() {
		return eventType;
	}
	
}
