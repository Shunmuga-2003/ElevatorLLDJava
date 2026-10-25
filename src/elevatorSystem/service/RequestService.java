package elevatorSystem.service;

import elevatorSystem.entity.Building;
import elevatorSystem.entity.Direction;
import elevatorSystem.entity.Elevator;
import elevatorSystem.entity.ExternalRequest;
import elevatorSystem.entity.InternalRequest;
import elevatorSystem.repository.ExternalRequestRepository;
import elevatorSystem.repository.InternalRequestRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/** Validates, stores and moves requests through their lifecycle. Every status change is saved back. */
public class RequestService {
    private final ExternalRequestRepository externalRequestRepository;
    private final InternalRequestRepository internalRequestRepository;
    private final AtomicLong sequence = new AtomicLong();

    public RequestService(ExternalRequestRepository externalRequestRepository,
                          InternalRequestRepository internalRequestRepository) {
        this.externalRequestRepository = externalRequestRepository;
        this.internalRequestRepository = internalRequestRepository;
    }

    /** Hall button pressed. Pressing a button that is already lit returns the existing request. */
    public ExternalRequest createHallCall(Building building, int floor, Direction direction) {
        requireAcceptingRequests(building);
        requireFloor(building, floor);
        if (direction == null || direction == Direction.IDLE
                || (direction == Direction.UP && floor == building.getMaxFloor())
                || (direction == Direction.DOWN && floor == building.getMinFloor())) {
            throw new IllegalArgumentException("Cannot request " + direction + " from floor " + floor);
        }
        return externalRequestRepository.findActive(building.getId(), floor, direction)
                .orElseGet(() -> externalRequestRepository.save(new ExternalRequest(
                        building.getId(), floor, direction, building.getCurrentTick(), sequence.incrementAndGet())));
    }

    /** Floor button pressed inside an elevator. */
    public InternalRequest createCarCall(Building building, Elevator elevator, int destinationFloor) {
        requireAcceptingRequests(building);
        requireFloor(building, destinationFloor);
        if (!elevator.isInService()) {
            throw new IllegalStateException(elevator.getName() + " is not in service");
        }
        InternalRequest request = internalRequestRepository.save(new InternalRequest(
                elevator.getElevatorId(), building.getId(), destinationFloor, building.getCurrentTick()));
        elevator.addCarCall(destinationFloor);
        return request;
    }

    public void assign(ExternalRequest request, Elevator elevator) {
        request.assignTo(elevator.getElevatorId());
        externalRequestRepository.save(request);
        elevator.addHallCall(request.getFloorNumber(), request.getDirection());
    }

    /**
     * Marks every request answered by the elevator stopping at its current floor as completed.
     * @return the hall calls completed here, so their wait times can be reported.
     */
    public List<ExternalRequest> completeAtCurrentFloor(Building building, Elevator elevator, Set<Direction> servedDirections) {
        int floor = elevator.getCurrentFloor();
        long tick = building.getCurrentTick();
        for (InternalRequest r : internalRequestRepository.findPendingByElevator(building.getId(), elevator.getElevatorId())) {
            if (r.getDestinationFloor() == floor) {
                r.complete(tick);
                internalRequestRepository.save(r);
            }
        }
        List<ExternalRequest> completed = new ArrayList<>();
        for (ExternalRequest r : externalRequestRepository.findAssignedTo(building.getId(), elevator.getElevatorId())) {
            if (r.getFloorNumber() == floor && servedDirections.contains(r.getDirection())) {
                r.complete(tick);
                externalRequestRepository.save(r);
                completed.add(r);
            }
        }
        return completed;
    }

    /** Puts the elevator's hall calls back in the queue (it left service or is full). @return how many. */
    public int requeueHallCalls(Building building, Elevator elevator) {
        List<ExternalRequest> assigned = externalRequestRepository.findAssignedTo(building.getId(), elevator.getElevatorId());
        for (ExternalRequest r : assigned) {
            r.requeue();
            externalRequestRepository.save(r);
        }
        elevator.clearHallCalls();
        return assigned.size();
    }

    public void cancelCarCalls(Building building, Elevator elevator) {
        for (InternalRequest r : internalRequestRepository.findPendingByElevator(building.getId(), elevator.getElevatorId())) {
            r.cancel();
            internalRequestRepository.save(r);
        }
    }

    /** Emergency: every active request in the building is cancelled. */
    public void cancelAll(Building building) {
        for (ExternalRequest r : externalRequestRepository.findActiveByBuilding(building.getId())) {
            r.cancel();
            externalRequestRepository.save(r);
        }
        for (InternalRequest r : internalRequestRepository.findPendingByBuilding(building.getId())) {
            r.cancel();
            internalRequestRepository.save(r);
        }
    }

    public List<ExternalRequest> findQueued(String buildingId) {
        return externalRequestRepository.findQueued(buildingId);
    }

    public boolean hasOutstandingRequests(String buildingId) {
        return !externalRequestRepository.findActiveByBuilding(buildingId).isEmpty()
                || !internalRequestRepository.findPendingByBuilding(buildingId).isEmpty();
    }

    public ExternalRequest getHallCall(String requestId) {
        return externalRequestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown hall call " + requestId));
    }

    public InternalRequest getCarCall(String requestId) {
        return internalRequestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown car call " + requestId));
    }

    private static void requireAcceptingRequests(Building building) {
        if (!building.getSystemState().acceptsRequests()) {
            throw new IllegalStateException(building.getName() + " is " + building.getSystemState()
                    + "; new requests are not accepted");
        }
    }

    private static void requireFloor(Building building, int floor) {
        if (!building.isValidFloor(floor)) {
            throw new IllegalArgumentException("Floor " + floor + " does not exist in " + building.getName());
        }
    }
}
