package elevatorSystem.entity;

import java.util.UUID;

/**
 * A building and its elevator group. Owns the group's lifecycle ({@link SystemState}) and its
 * simulation clock, so every timestamp in the building is measured in the same ticks.
 */
public class Building {
    private final String id;
    private final String name;
    private final int minFloor;
    private final int maxFloor;
    private final int recallFloor;
    private final int elevatorCount;
    private SystemState systemState;
    private long currentTick;

    public Building(String name, int minFloor, int maxFloor, int recallFloor, int elevatorCount) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("A building needs a name");
        }
        if (minFloor >= maxFloor) {
            throw new IllegalArgumentException("minFloor must be lower than maxFloor");
        }
        if (recallFloor < minFloor || recallFloor > maxFloor) {
            throw new IllegalArgumentException("Recall floor " + recallFloor + " is outside " + minFloor + "-" + maxFloor);
        }
        if (elevatorCount <= 0) {
            throw new IllegalArgumentException("A building needs at least one elevator");
        }
        this.id = UUID.randomUUID().toString();
        this.name = name;
        this.minFloor = minFloor;
        this.maxFloor = maxFloor;
        this.recallFloor = recallFloor;
        this.elevatorCount = elevatorCount;
        this.systemState = SystemState.STOPPED;
    }

    public boolean isValidFloor(int floor) {
        return floor >= minFloor && floor <= maxFloor;
    }

    public void transitionTo(SystemState next) {
        if (!systemState.canTransitionTo(next)) {
            throw new IllegalStateException(name + " cannot go from " + systemState + " to " + next);
        }
        systemState = next;
    }

    /** Advances the simulation clock by one tick and returns the new time. */
    public long advanceClock() {
        return ++currentTick;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public int getMinFloor() {
        return minFloor;
    }

    public int getMaxFloor() {
        return maxFloor;
    }

    public int getRecallFloor() {
        return recallFloor;
    }

    public int getElevatorCount() {
        return elevatorCount;
    }

    public SystemState getSystemState() {
        return systemState;
    }

    public long getCurrentTick() {
        return currentTick;
    }
}
