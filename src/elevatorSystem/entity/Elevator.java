package elevatorSystem.entity;

import java.util.NavigableSet;
import java.util.Optional;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * One elevator car. The car owns its stop sets and its state machine: callers ask it to add or serve
 * stops and to change state, and it enforces its own invariants. Nothing outside this class can reach
 * the stop sets, so they can only change through the methods below (always under the building's lock).
 */
public class Elevator {
    private final String elevatorId;
    private final String name;
    private final String buildingId;
    private final int capacity;
    private int currentFloor;
    private Direction direction = Direction.IDLE;
    private ElevatorState state = ElevatorState.IDLE;
    private int currentLoad;

    // Floors chosen from inside the cabin. Served whenever the car reaches them.
    private final NavigableSet<Integer> carCalls = new TreeSet<>();
    // Floors where someone pressed UP / DOWN in the hall. Served only while travelling that way (or when turning).
    private final NavigableSet<Integer> upHallCalls = new TreeSet<>();
    private final NavigableSet<Integer> downHallCalls = new TreeSet<>();

    public Elevator(String name, String buildingId, int startFloor, int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("Elevator capacity must be positive");
        }
        this.elevatorId = UUID.randomUUID().toString();
        this.name = name;
        this.buildingId = buildingId;
        this.currentFloor = startFloor;
        this.capacity = capacity;
    }

    // --- availability ---

    public boolean isInService() {
        return state != ElevatorState.MAINTENANCE;
    }

    public boolean isFull() {
        return currentLoad >= capacity;
    }

    public boolean isOverloaded() {
        return currentLoad > capacity;
    }

    /** May the dispatcher give this car a new hall call? */
    public boolean isAvailableForDispatch() {
        return isInService() && !isFull();
    }

    // --- stops ---

    public void addCarCall(int floor) {
        carCalls.add(floor);
    }

    public void addHallCall(int floor, Direction direction) {
        hallCalls(direction).add(floor);
    }

    public boolean hasCarCall(int floor) {
        return carCalls.contains(floor);
    }

    public boolean hasHallCall(int floor, Direction direction) {
        return hallCalls(direction).contains(floor);
    }

    public boolean removeCarCall(int floor) {
        return carCalls.remove(floor);
    }

    public boolean removeHallCall(int floor, Direction direction) {
        return hallCalls(direction).remove(floor);
    }

    public void clearHallCalls() {
        upHallCalls.clear();
        downHallCalls.clear();
    }

    public void clearStops() {
        carCalls.clear();
        clearHallCalls();
    }

    public boolean hasPendingStops() {
        return !carCalls.isEmpty() || !upHallCalls.isEmpty() || !downHallCalls.isEmpty();
    }

    public int getPendingStopCount() {
        return carCalls.size() + upHallCalls.size() + downHallCalls.size();
    }

    public boolean hasStopsAbove() {
        return highestStop().filter(f -> f > currentFloor).isPresent();
    }

    public boolean hasStopsBelow() {
        return lowestStop().filter(f -> f < currentFloor).isPresent();
    }

    public Optional<Integer> highestStop() {
        return allStopSets().filter(s -> !s.isEmpty()).map(NavigableSet::last).max(Integer::compare);
    }

    public Optional<Integer> lowestStop() {
        return allStopSets().filter(s -> !s.isEmpty()).map(NavigableSet::first).min(Integer::compare);
    }

    /** Stops the car will make strictly between two floors when travelling in {@code direction}. */
    public int stopsBetween(int fromExclusive, int toExclusive, Direction direction) {
        int low = Math.min(fromExclusive, toExclusive);
        int high = Math.max(fromExclusive, toExclusive);
        if (high - low < 2) {
            return 0;
        }
        NavigableSet<Integer> stops = new TreeSet<>(carCalls);
        stops.addAll(hallCalls(direction));
        return stops.subSet(low, false, high, false).size();
    }

    public NavigableSet<Integer> carCallsView() {
        return new TreeSet<>(carCalls);
    }

    public NavigableSet<Integer> hallCallsView(Direction direction) {
        return new TreeSet<>(hallCalls(direction));
    }

    // --- movement and state ---

    public void transitionTo(ElevatorState next) {
        if (!state.canTransitionTo(next)) {
            throw new IllegalStateException(name + " cannot go from " + state + " to " + next);
        }
        state = next;
    }

    public void setDirection(Direction direction) {
        this.direction = direction;
    }

    /** Moves one floor in {@code towards}. Only legal while the doors are closed. */
    public int moveOneFloor(Direction towards) {
        if (towards == Direction.IDLE) {
            throw new IllegalArgumentException("Cannot move without a direction");
        }
        transitionTo(ElevatorState.MOVING);
        direction = towards;
        currentFloor += towards == Direction.UP ? 1 : -1;
        return currentFloor;
    }

    /** Reading from the load sensor, in passengers. May exceed capacity: the car then refuses to close its doors. */
    public void updateLoad(int passengers) {
        if (passengers < 0) {
            throw new IllegalArgumentException("Load cannot be negative");
        }
        currentLoad = passengers;
    }

    // --- getters ---

    public String getElevatorId() {
        return elevatorId;
    }

    public String getName() {
        return name;
    }

    public String getBuildingId() {
        return buildingId;
    }

    public int getCapacity() {
        return capacity;
    }

    public int getCurrentFloor() {
        return currentFloor;
    }

    public Direction getDirection() {
        return direction;
    }

    public ElevatorState getState() {
        return state;
    }

    public int getCurrentLoad() {
        return currentLoad;
    }

    private NavigableSet<Integer> hallCalls(Direction direction) {
        switch (direction) {
            case UP:
                return upHallCalls;
            case DOWN:
                return downHallCalls;
            default:
                throw new IllegalArgumentException("A hall call needs UP or DOWN, not " + direction);
        }
    }

    private Stream<NavigableSet<Integer>> allStopSets() {
        return Stream.of(carCalls, upHallCalls, downHallCalls);
    }

    @Override
    public String toString() {
        return String.format("%s[floor=%d, dir=%s, state=%s, load=%d/%d, car=%s, up=%s, down=%s]",
                name, currentFloor, direction, state, currentLoad, capacity, carCalls, upHallCalls, downHallCalls);
    }
}
