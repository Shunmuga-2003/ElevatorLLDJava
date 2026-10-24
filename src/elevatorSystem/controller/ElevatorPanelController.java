package elevatorSystem.controller;

import elevatorSystem.Entity.InternalRequest;
import elevatorSystem.service.RequestService;

/** The floor buttons inside an elevator cabin. Safe to call from any thread. */
public class ElevatorPanelController {
    private final RequestService requestService;
    private final SystemLock lock;

    public ElevatorPanelController(RequestService requestService, SystemLock lock) {
        this.requestService = requestService;
        this.lock = lock;
    }

    public InternalRequest pressFloorButton(String elevatorId, int floor) {
        return lock.call(() -> requestService.createInternalRequest(elevatorId, floor));
    }
}
