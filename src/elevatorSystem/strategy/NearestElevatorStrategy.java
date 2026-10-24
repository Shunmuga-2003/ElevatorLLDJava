package elevatorSystem.strategy;

import elevatorSystem.Entity.Building;
import elevatorSystem.Entity.Direction;
import elevatorSystem.Entity.Elevator;
import elevatorSystem.Entity.ExternalRequest;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Picks the elevator that would reach the caller in the fewest floors of travel.
 * An elevator already heading towards the caller in the caller's direction is preferred;
 * otherwise the cost includes finishing its current sweep and coming back.
 */
public class NearestElevatorStrategy implements ElevatorSelectionStrategy {

    @Override
    public Optional<Elevator> selectElevator(List<Elevator> candidates, ExternalRequest request, Building building) {
        return candidates.stream()
                .min(Comparator.<Elevator>comparingInt(e -> cost(e, request, building))
                        .thenComparingInt(Elevator::getPendingStopCount)
                        .thenComparing(Elevator::getName));
    }

    int cost(Elevator elevator, ExternalRequest request, Building building) {
        int current = elevator.getCurrentFloor();
        int target = request.getFloorNumber();
        Direction moving = elevator.getDirection();

        if (moving == Direction.IDLE) {
            return Math.abs(current - target);
        }

        boolean onTheWay = moving == request.getDirection()
                && (moving == Direction.UP ? target >= current : target <= current);
        if (onTheWay) {
            return Math.abs(current - target);
        }

        int sweepEnd = moving == Direction.UP ? building.getMaxFloor() : building.getMinFloor();
        int otherEnd = moving == Direction.UP ? building.getMinFloor() : building.getMaxFloor();
        int toSweepEnd = Math.abs(sweepEnd - current);

        boolean onTheWayBack = request.getDirection() != moving;
        if (onTheWayBack) {
            return toSweepEnd + Math.abs(sweepEnd - target);
        }
        return toSweepEnd + Math.abs(sweepEnd - otherEnd) + Math.abs(otherEnd - target);
    }
}
