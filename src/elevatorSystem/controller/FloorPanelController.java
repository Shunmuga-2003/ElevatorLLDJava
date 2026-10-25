package elevatorSystem.controller;

import elevatorSystem.dto.HallCallView;
import elevatorSystem.entity.Building;
import elevatorSystem.entity.Direction;
import elevatorSystem.entity.ExternalRequest;
import elevatorSystem.service.BuildingService;
import elevatorSystem.service.DispatcherService;
import elevatorSystem.service.RequestService;

/** The UP/DOWN buttons in each floor's hallway. Safe to call from any thread. */
public class FloorPanelController {
    private final BuildingService buildingService;
    private final RequestService requestService;
    private final DispatcherService dispatcherService;
    private final BuildingLocks locks;

    public FloorPanelController(BuildingService buildingService, RequestService requestService,
                                DispatcherService dispatcherService, BuildingLocks locks) {
        this.buildingService = buildingService;
        this.requestService = requestService;
        this.dispatcherService = dispatcherService;
        this.locks = locks;
    }

    public HallCallView pressButton(String buildingId, int floor, Direction direction) {
        // create + dispatch is one atomic step, so two people pressing the same button never get two requests
        return locks.call(buildingId, () -> {
            Building building = buildingService.getBuilding(buildingId);
            ExternalRequest request = requestService.createHallCall(building, floor, direction);
            dispatcherService.dispatch(building, request);
            return HallCallView.of(request);
        });
    }
}
