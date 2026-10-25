package elevatorSystem.strategy;

import elevatorSystem.entity.Building;
import elevatorSystem.entity.Elevator;
import elevatorSystem.entity.ExternalRequest;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Picks the elevator with the lowest estimated time of arrival at the caller ({@link TravelTimeEstimator}).
 * An elevator already heading towards the caller in the caller's direction usually wins, because it
 * picks them up on the way; one moving away pays for finishing its sweep and coming back.
 */
public class NearestElevatorStrategy implements ElevatorSelectionStrategy {
    private final TravelTimeEstimator estimator = new TravelTimeEstimator();

    @Override
    public Optional<Elevator> selectElevator(List<Elevator> candidates, ExternalRequest request, Building building) {
        return candidates.stream()
                .min(Comparator.<Elevator>comparingInt(e -> eta(e, request))
                        .thenComparingInt(Elevator::getPendingStopCount)
                        .thenComparing(Elevator::getName));
    }

    int eta(Elevator elevator, ExternalRequest request) {
        return estimator.estimate(elevator, request.getFloorNumber(), request.getDirection());
    }
}
