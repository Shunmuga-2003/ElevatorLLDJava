package elevatorSystem.event;

import elevatorSystem.entity.Direction;
import elevatorSystem.entity.SystemState;

/**
 * Something that happened in a building, published on the tick it happened. Services publish events
 * instead of writing log lines, so logging, metrics and displays are independent subscribers (Observer pattern).
 * {@code source} is the elevator's name, or the building's name for building-wide events.
 */
public sealed interface ElevatorEvent {
    long tick();

    String buildingId();

    String source();

    /** Human-readable description, e.g. {@code moving UP 4 -> 5}. */
    String describe();

    record Moved(long tick, String buildingId, String source, int from, int to, Direction direction) implements ElevatorEvent {
        public String describe() {
            return "moving " + direction + " " + from + " -> " + to;
        }
    }

    record Stopped(long tick, String buildingId, String source, int floor) implements ElevatorEvent {
        public String describe() {
            return "stopped at floor " + floor + ", doors opening";
        }
    }

    record DoorsHeld(long tick, String buildingId, String source, int floor, String reason) implements ElevatorEvent {
        public String describe() {
            return "doors held open at floor " + floor + " (" + reason + ")";
        }
    }

    record DoorsClosing(long tick, String buildingId, String source, int floor) implements ElevatorEvent {
        public String describe() {
            return "doors closing at floor " + floor;
        }
    }

    record Idle(long tick, String buildingId, String source, int floor) implements ElevatorEvent {
        public String describe() {
            return "idle at floor " + floor;
        }
    }

    record HallCallCompleted(long tick, String buildingId, String source, String requestId,
                             int floor, Direction direction, long waitTicks) implements ElevatorEvent {
        public String describe() {
            return "picked up floor " + floor + " " + direction + " after " + waitTicks + " ticks";
        }
    }

    record HallCallsReleased(long tick, String buildingId, String source, int count, String reason) implements ElevatorEvent {
        public String describe() {
            return reason + ", handed back " + count + " hall call(s)";
        }
    }

    record ServiceChanged(long tick, String buildingId, String source, boolean inService) implements ElevatorEvent {
        public String describe() {
            return inService ? "back in service" : "out of service";
        }
    }

    record SystemStateChanged(long tick, String buildingId, String source, SystemState state) implements ElevatorEvent {
        public String describe() {
            return "system " + state.name().toLowerCase();
        }
    }
}
