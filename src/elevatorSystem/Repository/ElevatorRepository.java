package elevatorSystem.Repository;

import elevatorSystem.Entity.Elevator;

import java.util.List;
import java.util.Optional;

public interface ElevatorRepository {
    Elevator save(Elevator elevator);
    List<Elevator> findAll();
    List<Elevator> findAvailableElevators(String buildingId);
    List<Elevator> findByBuilding(String buildingId);
    Optional<Elevator> findElevatorById(String elevatorId);
    void deleteById(String elevatorId);
}
