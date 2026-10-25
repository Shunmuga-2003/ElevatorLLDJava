package elevatorSystem.strategy;

import elevatorSystem.entity.Building;
import elevatorSystem.entity.Elevator;
import elevatorSystem.entity.ExternalRequest;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Picks the elevator with the fewest pending stops, using the estimated arrival time only to break ties.
 * Balances load during rush hour, at the cost of sometimes sending an elevator that is further away.
 */
public class LeastBusyStrategy implements ElevatorSelectionStrategy {
    private final TravelTimeEstimator estimator = new TravelTimeEstimator();

    @Override
    public Optional<Elevator> selectElevator(List<Elevator> candidates, ExternalRequest request, Building building) {
        return candidates.stream()
                .min(Comparator.comparingInt(Elevator::getPendingStopCount)
                        .thenComparingInt(e -> estimator.estimate(e, request.getFloorNumber(), request.getDirection()))
                        .thenComparing(Elevator::getName));
    }
}
