package elevatorSystem.controller;

import elevatorSystem.dto.CarCallView;
import elevatorSystem.dto.ElevatorView;
import elevatorSystem.entity.Building;
import elevatorSystem.entity.Elevator;
import elevatorSystem.service.BuildingService;
import elevatorSystem.service.DispatcherService;
import elevatorSystem.service.ElevatorService;
import elevatorSystem.service.RequestService;

/** The cabin: floor buttons and the load sensor. Safe to call from any thread. */
public class ElevatorPanelController {
    private final BuildingService buildingService;
    private final ElevatorService elevatorService;
    private final RequestService requestService;
    private final DispatcherService dispatcherService;
    private final BuildingLocks locks;

    public ElevatorPanelController(BuildingService buildingService, ElevatorService elevatorService,
                                   RequestService requestService, DispatcherService dispatcherService,
                                   BuildingLocks locks) {
        this.buildingService = buildingService;
        this.elevatorService = elevatorService;
        this.requestService = requestService;
        this.dispatcherService = dispatcherService;
        this.locks = locks;
    }

    public CarCallView pressFloorButton(String elevatorId, int floor) {
        Elevator elevator = elevatorService.getElevator(elevatorId);
        return locks.call(elevator.getBuildingId(), () -> CarCallView.of(requestService.createCarCall(
                buildingService.getBuilding(elevator.getBuildingId()), elevator, floor)));
    }

    /**
     * Load-sensor reading in passengers. Above capacity the doors stay open until someone steps out;
     * at capacity the car hands its hall calls to other cars, which are re-dispatched immediately.
     */
    public ElevatorView reportLoad(String elevatorId, int passengers) {
        Elevator elevator = elevatorService.getElevator(elevatorId);
        return locks.call(elevator.getBuildingId(), () -> {
            Building building = buildingService.getBuilding(elevator.getBuildingId());
            if (elevatorService.updateLoad(building, elevator, passengers)) {
                dispatcherService.dispatchQueued(building);
            }
            return ElevatorView.of(elevator);
        });
    }
}
