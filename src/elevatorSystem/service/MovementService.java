package elevatorSystem.service;

import elevatorSystem.entity.Building;
import elevatorSystem.entity.Direction;
import elevatorSystem.entity.Elevator;
import elevatorSystem.entity.ElevatorState;
import elevatorSystem.entity.ExternalRequest;
import elevatorSystem.event.ElevatorEvent;
import elevatorSystem.event.EventPublisher;

/**
 * Advances one elevator by one simulation tick. Each tick the elevator does exactly one thing:
 * open its doors at a stop, hold them open, close them, move one floor, or stay idle.
 */
public class MovementService {
    private final ElevatorSchedulerService schedulerService;
    private final RequestService requestService;
    private final EventPublisher events;

    public MovementService(ElevatorSchedulerService schedulerService, RequestService requestService, EventPublisher events) {
        this.schedulerService = schedulerService;
        this.requestService = requestService;
        this.events = events;
    }

    public void step(Building building, Elevator elevator) {
        if (!elevator.isInService()) {
            return;
        }
        long tick = building.getCurrentTick();
        String id = building.getId();
        String name = elevator.getName();
        int floor = elevator.getCurrentFloor();

        if (elevator.isOverloaded()) {
            // the doors never close on an overloaded car; reopen them if they were closing
            if (elevator.getState() != ElevatorState.DOORS_OPEN) {
                elevator.transitionTo(ElevatorState.DOORS_OPEN);
            }
            events.publish(new ElevatorEvent.DoorsHeld(tick, id, name, floor,
                    "overloaded " + elevator.getCurrentLoad() + "/" + elevator.getCapacity()));
            return;
        }

        if (elevator.getState() == ElevatorState.DOORS_OPEN) {
            if (schedulerService.shouldStopAtCurrentFloor(elevator)) {
                // someone pressed for this floor while the doors were open: serve them without closing
                events.publish(new ElevatorEvent.DoorsHeld(tick, id, name, floor, "new stop here"));
                serveCurrentFloor(building, elevator);
                return;
            }
            elevator.transitionTo(ElevatorState.DOORS_CLOSING);
            events.publish(new ElevatorEvent.DoorsClosing(tick, id, name, floor));
            return;
        }

        if (schedulerService.shouldStopAtCurrentFloor(elevator)) {
            elevator.transitionTo(ElevatorState.DOORS_OPEN);
            events.publish(new ElevatorEvent.Stopped(tick, id, name, floor));
            serveCurrentFloor(building, elevator);
            return;
        }

        Direction next = schedulerService.nextDirection(elevator);
        elevator.setDirection(next);
        if (next == Direction.IDLE) {
            if (elevator.getState() != ElevatorState.IDLE) {
                elevator.transitionTo(ElevatorState.IDLE);
                events.publish(new ElevatorEvent.Idle(tick, id, name, floor));
            }
            return;
        }
        int to = elevator.moveOneFloor(next);
        events.publish(new ElevatorEvent.Moved(tick, id, name, floor, to, next));
    }

    private void serveCurrentFloor(Building building, Elevator elevator) {
        for (ExternalRequest done : requestService.completeAtCurrentFloor(
                building, elevator, schedulerService.serveCurrentFloor(elevator))) {
            events.publish(new ElevatorEvent.HallCallCompleted(building.getCurrentTick(), building.getId(),
                    elevator.getName(), done.getId(), done.getFloorNumber(), done.getDirection(), done.waitTicks()));
        }
        elevator.setDirection(schedulerService.nextDirection(elevator));
    }
}
