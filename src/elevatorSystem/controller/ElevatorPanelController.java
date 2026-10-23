package elevatorSystem.controller;

import elevatorSystem.Entity.InternalRequest;
import elevatorSystem.service.RequestService;

/** The floor buttons inside an elevator cabin. */
public class ElevatorPanelController {
    private final RequestService requestService;

    public ElevatorPanelController(RequestService requestService) {
        this.requestService = requestService;
    }

    public InternalRequest pressFloorButton(String elevatorId, int floor) {
        return requestService.createInternalRequest(elevatorId, floor);
    }
}
