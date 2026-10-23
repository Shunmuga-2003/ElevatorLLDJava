package elevatorSystem.Entity;

import java.util.NavigableSet;
import java.util.TreeSet;
import java.util.UUID;

public class Elevator {
    private String elevatorId;
    private String name;
    private String buildingId;
    private int currentFloor;
    private Direction direction;
    private ElevatorState elevatorState;
    private int elevatorCapacity;
    private int currentLoad;
    private boolean isActive;

    // Floors chosen from inside the cabin. Served whenever the elevator reaches them.
    private final NavigableSet<Integer> carCalls = new TreeSet<>();
    // Floors where someone pressed UP / DOWN in the hall. Served only while travelling that way (or when turning).
    private final NavigableSet<Integer> upHallCalls = new TreeSet<>();
    private final NavigableSet<Integer> downHallCalls = new TreeSet<>();

    public Elevator(String name, String buildingId, int startFloor, int elevatorCapacity) {
        this.elevatorId = UUID.randomUUID().toString();
        this.name = name;
        this.buildingId = buildingId;
        this.currentFloor = startFloor;
        this.direction = Direction.IDLE;
        this.elevatorState = ElevatorState.STOPPED;
        this.elevatorCapacity = elevatorCapacity;
        this.currentLoad = 0;
        this.isActive = true;
    }

    public boolean hasPendingStops() {
        return !carCalls.isEmpty() || !upHallCalls.isEmpty() || !downHallCalls.isEmpty();
    }

    public boolean isAvailable() {
        return isActive && elevatorState != ElevatorState.MAINTENANCE && currentLoad < elevatorCapacity;
    }

    public NavigableSet<Integer> getCarCalls() {
        return carCalls;
    }

    public NavigableSet<Integer> getUpHallCalls() {
        return upHallCalls;
    }

    public NavigableSet<Integer> getDownHallCalls() {
        return downHallCalls;
    }

    public String getElevatorId() {
        return elevatorId;
    }

    public void setElevatorId(String elevatorId) {
        this.elevatorId = elevatorId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getBuildingId() {
        return buildingId;
    }

    public void setBuildingId(String buildingId) {
        this.buildingId = buildingId;
    }

    public int getCurrentFloor() {
        return currentFloor;
    }

    public void setCurrentFloor(int currentFloor) {
        this.currentFloor = currentFloor;
    }

    public Direction getDirection() {
        return direction;
    }

    public void setDirection(Direction direction) {
        this.direction = direction;
    }

    public ElevatorState getElevatorState() {
        return elevatorState;
    }

    public void setElevatorState(ElevatorState elevatorState) {
        this.elevatorState = elevatorState;
    }

    public int getElevatorCapacity() {
        return elevatorCapacity;
    }

    public void setElevatorCapacity(int elevatorCapacity) {
        this.elevatorCapacity = elevatorCapacity;
    }

    public int getCurrentLoad() {
        return currentLoad;
    }

    public void setCurrentLoad(int currentLoad) {
        this.currentLoad = currentLoad;
    }

    public boolean isActive() {
        return isActive;
    }

    public void setActive(boolean active) {
        isActive = active;
    }

    @Override
    public String toString() {
        return String.format("%s[floor=%d, dir=%s, state=%s, car=%s, up=%s, down=%s]",
                name, currentFloor, direction, elevatorState, carCalls, upHallCalls, downHallCalls);
    }
}
