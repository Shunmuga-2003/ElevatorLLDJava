package elevatorSystem.Repository;

import elevatorSystem.Entity.Building;

import java.util.List;
import java.util.Optional;

public interface BuildingRepository {
     Building save(Building building);
     Optional<Building> getBuildingById(String buildingId);
     List<Building> findAll();
     void deleteByBuildingId(String buildingId);
}
