package elevatorSystem.strategy;

import elevatorSystem.Entity.Building;
import elevatorSystem.Entity.Elevator;
import elevatorSystem.Entity.ExternalRequest;

import java.util.List;
import java.util.Optional;

/** Strategy pattern: decides which elevator should answer a hall call. */
public interface ElevatorSelectionStrategy {
    Optional<Elevator> selectElevator(List<Elevator> candidates, ExternalRequest request, Building building);
}
