package elevatorSystem.service;

import elevatorSystem.entity.Building;
import elevatorSystem.entity.SystemState;
import elevatorSystem.event.ElevatorEvent;
import elevatorSystem.event.EventPublisher;
import elevatorSystem.repository.BuildingRepository;

public class BuildingService {
    private final BuildingRepository buildingRepository;
    private final ElevatorService elevatorService;
    private final EventPublisher events;

    public BuildingService(BuildingRepository buildingRepository, ElevatorService elevatorService, EventPublisher events) {
        this.buildingRepository = buildingRepository;
        this.elevatorService = elevatorService;
        this.events = events;
    }

    /**
     * Creates a building and its elevators (named E1..En), all parked at the lowest floor.
     * The elevators are stored before the building, so no other thread can see a building without its cars.
     */
    public Building createBuilding(String name, int minFloor, int maxFloor, int recallFloor,
                                   int elevatorCount, int elevatorCapacity) {
        Building building = new Building(name, minFloor, maxFloor, recallFloor, elevatorCount);
        for (int i = 1; i <= elevatorCount; i++) {
            elevatorService.createElevator("E" + i, building.getId(), minFloor, elevatorCapacity);
        }
        return buildingRepository.save(building);
    }

    public Building getBuilding(String buildingId) {
        return buildingRepository.findById(buildingId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown building " + buildingId));
    }

    /** Moves the building to {@code state}, rejecting illegal transitions. Publishes an event only on a change. */
    public void transition(Building building, SystemState state) {
        if (building.getSystemState() == state) {
            return;
        }
        building.transitionTo(state);
        buildingRepository.save(building);
        events.publish(new ElevatorEvent.SystemStateChanged(building.getCurrentTick(), building.getId(),
                building.getName(), state));
    }
}
