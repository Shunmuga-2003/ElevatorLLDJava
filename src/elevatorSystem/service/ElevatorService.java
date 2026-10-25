package elevatorSystem.service;

import elevatorSystem.entity.Building;
import elevatorSystem.entity.Direction;
import elevatorSystem.entity.Elevator;
import elevatorSystem.entity.ElevatorState;
import elevatorSystem.entity.SystemState;
import elevatorSystem.event.ElevatorEvent;
import elevatorSystem.event.EventPublisher;
import elevatorSystem.repository.ElevatorRepository;

import java.util.List;
import java.util.stream.Collectors;

/** Elevator lookup and the operations that change an elevator's availability: maintenance, load and recall. */
public class ElevatorService {
    private final ElevatorRepository elevatorRepository;
    private final RequestService requestService;
    private final EventPublisher events;

    public ElevatorService(ElevatorRepository elevatorRepository, RequestService requestService, EventPublisher events) {
        this.elevatorRepository = elevatorRepository;
        this.requestService = requestService;
        this.events = events;
    }

    public Elevator createElevator(String name, String buildingId, int startFloor, int capacity) {
        return elevatorRepository.save(new Elevator(name, buildingId, startFloor, capacity));
    }

    public Elevator getElevator(String elevatorId) {
        return elevatorRepository.findById(elevatorId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown elevator " + elevatorId));
    }

    public List<Elevator> findByBuilding(String buildingId) {
        return elevatorRepository.findByBuilding(buildingId);
    }

    public List<Elevator> findAvailableForDispatch(String buildingId) {
        return findByBuilding(buildingId).stream().filter(Elevator::isAvailableForDispatch).collect(Collectors.toList());
    }

    /** Takes an elevator out of service: hall calls go back to the queue, car calls are cancelled. */
    public void startMaintenance(Building building, Elevator elevator) {
        if (!elevator.isInService()) {
            return;
        }
        requestService.requeueHallCalls(building, elevator);
        requestService.cancelCarCalls(building, elevator);
        elevator.clearStops();
        elevator.setDirection(Direction.IDLE);
        elevator.transitionTo(ElevatorState.MAINTENANCE);
        elevatorRepository.save(elevator);
        events.publish(new ElevatorEvent.ServiceChanged(building.getCurrentTick(), building.getId(), elevator.getName(), false));
    }

    /** Returns an elevator to service. During an emergency it joins the recall instead of taking calls. */
    public void endMaintenance(Building building, Elevator elevator) {
        if (elevator.isInService()) {
            return;
        }
        elevator.transitionTo(ElevatorState.IDLE);
        elevator.updateLoad(0);
        elevatorRepository.save(elevator);
        events.publish(new ElevatorEvent.ServiceChanged(building.getCurrentTick(), building.getId(), elevator.getName(), true));
        if (building.getSystemState() == SystemState.EMERGENCY) {
            recall(elevator, building);
        }
    }

    /**
     * Load-sensor reading. A car that becomes full stops answering hall calls (full-load bypass): its hall calls
     * go back to the queue for other cars, while its passengers' car calls are still served.
     * @return true if hall calls were handed back and should be re-dispatched.
     */
    public boolean updateLoad(Building building, Elevator elevator, int passengers) {
        if (!elevator.isInService()) {
            throw new IllegalStateException(elevator.getName() + " is not in service");
        }
        if (elevator.getState() == ElevatorState.MOVING) {
            throw new IllegalStateException(elevator.getName() + " is moving; load can only change while stopped");
        }
        elevator.updateLoad(passengers);
        elevatorRepository.save(elevator);
        if (!elevator.isFull()) {
            return false;
        }
        int released = requestService.requeueHallCalls(building, elevator);
        if (released > 0) {
            events.publish(new ElevatorEvent.HallCallsReleased(building.getCurrentTick(), building.getId(),
                    elevator.getName(), released, "full"));
        }
        return released > 0;
    }

    /** Fire recall: drop every stop and travel non-stop to the building's recall floor. */
    public void recall(Elevator elevator, Building building) {
        if (!elevator.isInService()) {
            return;
        }
        elevator.clearStops();
        elevator.addCarCall(building.getRecallFloor());
        elevatorRepository.save(elevator);
    }
}
