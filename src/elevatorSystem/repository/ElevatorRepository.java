package elevatorSystem.repository;

import elevatorSystem.entity.Elevator;

import java.util.List;
import java.util.Optional;

public interface ElevatorRepository {
    Elevator save(Elevator elevator);

    Optional<Elevator> findById(String elevatorId);

    /** Every car of the building, ordered by name (E1, E2, ...). */
    List<Elevator> findByBuilding(String buildingId);
}
