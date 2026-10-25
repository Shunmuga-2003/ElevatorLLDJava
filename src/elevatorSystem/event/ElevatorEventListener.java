package elevatorSystem.event;

/**
 * Receives events synchronously, on the thread that caused them and while that building's lock is held.
 * Implementations must be fast and must not call back into the controllers of the same building.
 */
@FunctionalInterface
public interface ElevatorEventListener {
    void onEvent(ElevatorEvent event);
}
