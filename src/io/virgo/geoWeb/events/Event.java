package io.virgo.geoWeb.events;

/**
 * Base event object, basically just storing an ENUM of its type
 */
public abstract class Event {
	
	private EventType eventType;
	
	public Event(EventType eventType) {
		this.eventType = eventType;
	}

	public EventType getType() {
		return eventType;
	}
	
}
