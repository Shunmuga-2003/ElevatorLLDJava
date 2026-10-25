package elevatorSystem.strategy;

import elevatorSystem.entity.Direction;
import elevatorSystem.entity.Elevator;
import elevatorSystem.entity.ElevatorState;

/**
 * Estimates how many ticks an elevator needs to reach a caller, following the route LOOK will actually take:
 * the car turns at its farthest pending stop, not at the end of the building (that would be SCAN), and every
 * stop on the way costs {@link #DOOR_TICKS} for opening and closing the doors.
 */
public class TravelTimeEstimator {
    public static final int DOOR_TICKS = 2;

    public int estimate(Elevator elevator, int target, Direction wanted) {
        int current = elevator.getCurrentFloor();
        int doorsToClose = elevator.getState() == ElevatorState.DOORS_OPEN ? 1 : 0;
        Direction moving = elevator.hasPendingStops() ? elevator.getDirection() : Direction.IDLE;
        if (moving == Direction.IDLE && elevator.hasPendingStops()) {
            // has work but has not started yet: it will set off the way LOOK picks (up first)
            moving = elevator.hasStopsAbove() ? Direction.UP : elevator.hasStopsBelow() ? Direction.DOWN : Direction.IDLE;
        }

        if (moving == Direction.IDLE) {
            return doorsToClose + Math.abs(current - target);
        }

        boolean ahead = moving == Direction.UP ? target >= current : target <= current;
        if (wanted == moving && ahead) {
            // picked up on the way: only the stops before the caller delay it
            return doorsToClose + Math.abs(target - current) + DOOR_TICKS * elevator.stopsBetween(current, target, moving);
        }

        int allStops = DOOR_TICKS * elevator.getPendingStopCount();
        if (moving == Direction.UP) {
            int turn = Math.max(current, elevator.highestStop().orElse(current));
            if (wanted == Direction.DOWN) {
                turn = Math.max(turn, target);
                return doorsToClose + (turn - current) + (turn - target) + allStops;
            }
            int bottom = Math.min(target, elevator.lowestStop().orElse(target));
            return doorsToClose + (turn - current) + (turn - bottom) + (target - bottom) + allStops;
        }
        int turn = Math.min(current, elevator.lowestStop().orElse(current));
        if (wanted == Direction.UP) {
            turn = Math.min(turn, target);
            return doorsToClose + (current - turn) + (target - turn) + allStops;
        }
        int top = Math.max(target, elevator.highestStop().orElse(target));
        return doorsToClose + (current - turn) + (top - turn) + (top - target) + allStops;
    }
}
