package elevatorSystem.service;

import elevatorSystem.Entity.Direction;
import elevatorSystem.Entity.Elevator;

import java.util.EnumSet;
import java.util.Set;

/**
 * Per-elevator stop scheduling using the LOOK algorithm: keep travelling in the current
 * direction while there are stops ahead, then reverse. Hall calls are only served while
 * travelling in the requested direction, or at the floor where the elevator turns around.
 */
public class ElevatorSchedulerService {

    public void addHallCall(Elevator elevator, int floor, Direction direction) {
        if (direction == Direction.UP) {
            elevator.getUpHallCalls().add(floor);
        } else {
            elevator.getDownHallCalls().add(floor);
        }
    }

    public void addCarCall(Elevator elevator, int floor) {
        elevator.getCarCalls().add(floor);
    }

    public boolean shouldStopAtCurrentFloor(Elevator elevator) {
        int floor = elevator.getCurrentFloor();
        if (elevator.getCarCalls().contains(floor)) {
            return true;
        }
        boolean up = elevator.getUpHallCalls().contains(floor);
        boolean down = elevator.getDownHallCalls().contains(floor);
        switch (elevator.getDirection()) {
            case UP:
                return up || (down && !hasStopsAbove(elevator));
            case DOWN:
                return down || (up && !hasStopsBelow(elevator));
            default:
                return up || down;
        }
    }

    /**
     * Clears the stops being served at the current floor.
     * @return the hall-call directions that were served here (car calls are always served).
     */
    public Set<Direction> serveCurrentFloor(Elevator elevator) {
        int floor = elevator.getCurrentFloor();
        Direction direction = elevator.getDirection();
        boolean serveUp = direction != Direction.DOWN || !hasStopsBelow(elevator);
        boolean serveDown = direction != Direction.UP || !hasStopsAbove(elevator);

        elevator.getCarCalls().remove(floor);
        Set<Direction> served = EnumSet.noneOf(Direction.class);
        if (serveUp && elevator.getUpHallCalls().remove(floor)) {
            served.add(Direction.UP);
        }
        if (serveDown && elevator.getDownHallCalls().remove(floor)) {
            served.add(Direction.DOWN);
        }
        return served;
    }

    public Direction nextDirection(Elevator elevator) {
        boolean above = hasStopsAbove(elevator);
        boolean below = hasStopsBelow(elevator);
        if (elevator.getDirection() == Direction.DOWN) {
            return below ? Direction.DOWN : above ? Direction.UP : Direction.IDLE;
        }
        // UP keeps going up first; an IDLE elevator also prefers up when calls are on both sides
        return above ? Direction.UP : below ? Direction.DOWN : Direction.IDLE;
    }

    public void clearStops(Elevator elevator) {
        elevator.getCarCalls().clear();
        elevator.getUpHallCalls().clear();
        elevator.getDownHallCalls().clear();
    }

    private boolean hasStopsAbove(Elevator elevator) {
        int floor = elevator.getCurrentFloor();
        return elevator.getCarCalls().higher(floor) != null
                || elevator.getUpHallCalls().higher(floor) != null
                || elevator.getDownHallCalls().higher(floor) != null;
    }

    private boolean hasStopsBelow(Elevator elevator) {
        int floor = elevator.getCurrentFloor();
        return elevator.getCarCalls().lower(floor) != null
                || elevator.getUpHallCalls().lower(floor) != null
                || elevator.getDownHallCalls().lower(floor) != null;
    }
}
