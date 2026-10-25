package elevatorSystem.service;

import elevatorSystem.entity.Direction;
import elevatorSystem.entity.Elevator;

import java.util.EnumSet;
import java.util.Set;

/**
 * Per-elevator stop scheduling using the LOOK algorithm: keep travelling in the current direction while
 * there are stops ahead, then reverse. Hall calls are only served while travelling in the requested
 * direction, or at the floor where the elevator turns around. Stateless: all state lives in {@link Elevator}.
 */
public class ElevatorSchedulerService {

    public boolean shouldStopAtCurrentFloor(Elevator elevator) {
        int floor = elevator.getCurrentFloor();
        if (elevator.hasCarCall(floor)) {
            return true;
        }
        boolean up = elevator.hasHallCall(floor, Direction.UP);
        boolean down = elevator.hasHallCall(floor, Direction.DOWN);
        switch (elevator.getDirection()) {
            case UP:
                return up || (down && !elevator.hasStopsAbove());
            case DOWN:
                return down || (up && !elevator.hasStopsBelow());
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
        boolean serveUp = direction != Direction.DOWN || !elevator.hasStopsBelow();
        boolean serveDown = direction != Direction.UP || !elevator.hasStopsAbove();

        elevator.removeCarCall(floor);
        Set<Direction> served = EnumSet.noneOf(Direction.class);
        if (serveUp && elevator.removeHallCall(floor, Direction.UP)) {
            served.add(Direction.UP);
        }
        if (serveDown && elevator.removeHallCall(floor, Direction.DOWN)) {
            served.add(Direction.DOWN);
        }
        return served;
    }

    public Direction nextDirection(Elevator elevator) {
        boolean above = elevator.hasStopsAbove();
        boolean below = elevator.hasStopsBelow();
        if (elevator.getDirection() == Direction.DOWN) {
            return below ? Direction.DOWN : above ? Direction.UP : Direction.IDLE;
        }
        // UP keeps going up first; an IDLE elevator also prefers up when calls are on both sides
        return above ? Direction.UP : below ? Direction.DOWN : Direction.IDLE;
    }
}
