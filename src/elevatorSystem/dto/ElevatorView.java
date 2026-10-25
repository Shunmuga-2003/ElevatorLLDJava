package elevatorSystem.dto;

import elevatorSystem.entity.Direction;
import elevatorSystem.entity.Elevator;
import elevatorSystem.entity.ElevatorState;

import java.util.List;

/** Immutable snapshot of an elevator, safe to hold and read from any thread. */
public record ElevatorView(String elevatorId, String name, String buildingId, int currentFloor,
                           Direction direction, ElevatorState state, int currentLoad, int capacity,
                           List<Integer> carCalls, List<Integer> upHallCalls, List<Integer> downHallCalls) {

    public static ElevatorView of(Elevator e) {
        return new ElevatorView(e.getElevatorId(), e.getName(), e.getBuildingId(), e.getCurrentFloor(),
                e.getDirection(), e.getState(), e.getCurrentLoad(), e.getCapacity(),
                List.copyOf(e.carCallsView()), List.copyOf(e.hallCallsView(Direction.UP)),
                List.copyOf(e.hallCallsView(Direction.DOWN)));
    }

    @Override
    public String toString() {
        return String.format("%s[floor=%d, dir=%s, state=%s, load=%d/%d, car=%s, up=%s, down=%s]",
                name, currentFloor, direction, state, currentLoad, capacity, carCalls, upHallCalls, downHallCalls);
    }
}
