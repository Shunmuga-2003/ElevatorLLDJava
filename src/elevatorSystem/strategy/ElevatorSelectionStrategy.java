package elevatorSystem.strategy;

import elevatorSystem.entity.Building;
import elevatorSystem.entity.Elevator;
import elevatorSystem.entity.ExternalRequest;

import java.util.List;
import java.util.Optional;

/** Strategy pattern: decides which elevator should answer a hall call. */
public interface ElevatorSelectionStrategy {
    Optional<Elevator> selectElevator(List<Elevator> candidates, ExternalRequest request, Building building);
}
