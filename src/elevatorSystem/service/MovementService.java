package elevatorSystem.service;

import elevatorSystem.Entity.Direction;
import elevatorSystem.Entity.Elevator;
import elevatorSystem.Entity.ElevatorState;

import java.util.Optional;
import java.util.Set;

/**
 * Advances one elevator by one simulation tick. Each tick the elevator does exactly one thing:
 * open its doors at a stop, close them, move one floor, or stay idle.
 */
public class MovementService {
    private final ElevatorSchedulerService schedulerService;
    private final RequestService requestService;

    public MovementService(ElevatorSchedulerService schedulerService, RequestService requestService) {
        this.schedulerService = schedulerService;
        this.requestService = requestService;
    }

    /** @return a description of what happened, or empty if the elevator did nothing. */
    public Optional<String> step(Elevator elevator) {
        if (!elevator.isActive() || elevator.getElevatorState() == ElevatorState.MAINTENANCE) {
            return Optional.empty();
        }

        if (elevator.getElevatorState() == ElevatorState.DOOROPENING) {
            elevator.setElevatorState(ElevatorState.DOORCLOSING);
            return Optional.of("doors closing at floor " + elevator.getCurrentFloor());
        }

        if (schedulerService.shouldStopAtCurrentFloor(elevator)) {
            Set<Direction> served = schedulerService.serveCurrentFloor(elevator);
            requestService.completeRequestsAtCurrentFloor(elevator, served);
            elevator.setDirection(schedulerService.nextDirection(elevator));
            elevator.setElevatorState(ElevatorState.DOOROPENING);
            return Optional.of("stopped at floor " + elevator.getCurrentFloor() + ", doors opening");
        }

        Direction next = schedulerService.nextDirection(elevator);
        elevator.setDirection(next);
        if (next == Direction.IDLE) {
            boolean wasBusy = elevator.getElevatorState() != ElevatorState.STOPPED;
            elevator.setElevatorState(ElevatorState.STOPPED);
            return wasBusy ? Optional.of("idle at floor " + elevator.getCurrentFloor()) : Optional.empty();
        }

        int from = elevator.getCurrentFloor();
        elevator.setCurrentFloor(next == Direction.UP ? from + 1 : from - 1);
        elevator.setElevatorState(ElevatorState.MOVING);
        return Optional.of("moving " + next + " " + from + " -> " + elevator.getCurrentFloor());
    }
}
