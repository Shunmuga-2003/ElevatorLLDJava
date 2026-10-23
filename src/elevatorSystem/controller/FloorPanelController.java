package elevatorSystem.controller;

import elevatorSystem.Entity.Direction;
import elevatorSystem.Entity.ExternalRequest;
import elevatorSystem.service.DispatcherService;
import elevatorSystem.service.RequestService;

/** The UP/DOWN buttons in each floor's hallway. */
public class FloorPanelController {
    private final RequestService requestService;
    private final DispatcherService dispatcherService;

    public FloorPanelController(RequestService requestService, DispatcherService dispatcherService) {
        this.requestService = requestService;
        this.dispatcherService = dispatcherService;
    }

    public ExternalRequest pressButton(String buildingId, int floor, Direction direction) {
        ExternalRequest request = requestService.createExternalRequest(buildingId, floor, direction);
        dispatcherService.dispatch(request);
        return request;
    }
}
