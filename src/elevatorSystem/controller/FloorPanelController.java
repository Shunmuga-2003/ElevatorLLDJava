package elevatorSystem.controller;

import elevatorSystem.Entity.Direction;
import elevatorSystem.Entity.ExternalRequest;
import elevatorSystem.service.DispatcherService;
import elevatorSystem.service.RequestService;

/** The UP/DOWN buttons in each floor's hallway. Safe to call from any thread. */
public class FloorPanelController {
    private final RequestService requestService;
    private final DispatcherService dispatcherService;
    private final SystemLock lock;

    public FloorPanelController(RequestService requestService, DispatcherService dispatcherService, SystemLock lock) {
        this.requestService = requestService;
        this.dispatcherService = dispatcherService;
        this.lock = lock;
    }

    public ExternalRequest pressButton(String buildingId, int floor, Direction direction) {
        // create + dispatch is one atomic step, so two people pressing the same button never get two requests
        return lock.call(() -> {
            ExternalRequest request = requestService.createExternalRequest(buildingId, floor, direction);
            dispatcherService.dispatch(request);
            return request;
        });
    }
}
