package elevatorSystem.event;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Fans each event out to every subscriber. A failing subscriber is reported and skipped, never allowed to stop the clock. */
public class EventPublisher {
    private final List<ElevatorEventListener> listeners = new CopyOnWriteArrayList<>();

    public void subscribe(ElevatorEventListener listener) {
        listeners.add(listener);
    }

    public void unsubscribe(ElevatorEventListener listener) {
        listeners.remove(listener);
    }

    public void publish(ElevatorEvent event) {
        for (ElevatorEventListener listener : listeners) {
            try {
                listener.onEvent(event);
            } catch (RuntimeException e) {
                System.err.println("Event listener " + listener + " failed on " + event + ": " + e);
            }
        }
    }
}
