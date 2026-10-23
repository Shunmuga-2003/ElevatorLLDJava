package elevatorSystem.service;

import elevatorSystem.Entity.Building;
import elevatorSystem.Entity.SystemState;
import elevatorSystem.Repository.BuildingRepository;

public class BuildingService {
    private final BuildingRepository buildingRepository;
    private final ElevatorService elevatorService;

    public BuildingService(BuildingRepository buildingRepository, ElevatorService elevatorService) {
        this.buildingRepository = buildingRepository;
        this.elevatorService = elevatorService;
    }

    /** Creates a building and its elevators (named E1..En), all parked at the lowest floor. */
    public Building createBuilding(String name, int minFloor, int maxFloor, int elevatorCount, int elevatorCapacity) {
        Building building = buildingRepository.save(new Building(name, minFloor, maxFloor, elevatorCount));
        for (int i = 1; i <= elevatorCount; i++) {
            elevatorService.createElevator("E" + i, building.getId(), minFloor, elevatorCapacity);
        }
        return building;
    }

    public Building getBuilding(String buildingId) {
        return buildingRepository.getBuildingById(buildingId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown building " + buildingId));
    }

    public void updateSystemState(String buildingId, SystemState state) {
        Building building = getBuilding(buildingId);
        building.setSystemState(state);
        buildingRepository.save(building);
    }
}
