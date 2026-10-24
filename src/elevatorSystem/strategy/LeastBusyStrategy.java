package elevatorSystem.strategy;

import elevatorSystem.Entity.Building;
import elevatorSystem.Entity.Elevator;
import elevatorSystem.Entity.ExternalRequest;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Picks the elevator with the fewest pending stops, using travel distance only to break ties.
 * Balances load during rush hour, at the cost of sometimes sending an elevator that is further away.
 */
public class LeastBusyStrategy implements ElevatorSelectionStrategy {
    private final NearestElevatorStrategy distance = new NearestElevatorStrategy();

    @Override
    public Optional<Elevator> selectElevator(List<Elevator> candidates, ExternalRequest request, Building building) {
        return candidates.stream()
                .min(Comparator.comparingInt(Elevator::getPendingStopCount)
                        .thenComparingInt(e -> distance.cost(e, request, building))
                        .thenComparing(Elevator::getName));
    }
}
