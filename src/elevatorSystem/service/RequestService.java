package elevatorSystem.service;

import elevatorSystem.Entity.Building;
import elevatorSystem.Entity.Direction;
import elevatorSystem.Entity.Elevator;
import elevatorSystem.Entity.ExternalRequest;
import elevatorSystem.Entity.InternalRequest;
import elevatorSystem.Entity.RequestStatus;
import elevatorSystem.Entity.SystemState;
import elevatorSystem.Repository.BuildingRepository;
import elevatorSystem.Repository.ElevatorRepository;
import elevatorSystem.Repository.ExternalRequestRepository;
import elevatorSystem.Repository.InternalRequestRepository;

import java.util.List;
import java.util.Set;

public class RequestService {
    private final BuildingRepository buildingRepository;
    private final ElevatorRepository elevatorRepository;
    private final ExternalRequestRepository externalRequestRepository;
    private final InternalRequestRepository internalRequestRepository;
    private final ElevatorSchedulerService schedulerService;

    public RequestService(BuildingRepository buildingRepository,
                          ElevatorRepository elevatorRepository,
                          ExternalRequestRepository externalRequestRepository,
                          InternalRequestRepository internalRequestRepository,
                          ElevatorSchedulerService schedulerService) {
        this.buildingRepository = buildingRepository;
        this.elevatorRepository = elevatorRepository;
        this.externalRequestRepository = externalRequestRepository;
        this.internalRequestRepository = internalRequestRepository;
        this.schedulerService = schedulerService;
    }

    /** Hall button pressed. Pressing a button that is already lit returns the existing request. */
    public ExternalRequest createExternalRequest(String buildingId, int floor, Direction direction) {
        Building building = getRunningBuilding(buildingId);
        if (!building.isValidFloor(floor)) {
            throw new IllegalArgumentException("Floor " + floor + " does not exist in " + building.getName());
        }
        if (direction == Direction.IDLE
                || (direction == Direction.UP && floor == building.getMaxFloor())
                || (direction == Direction.DOWN && floor == building.getMinFloor())) {
            throw new IllegalArgumentException("Cannot request " + direction + " from floor " + floor);
        }

        return externalRequestRepository.findByBuilding(buildingId).stream()
                .filter(ExternalRequest::isActive)
                .filter(r -> r.getFloorNumber() == floor && r.getDirection() == direction)
                .findFirst()
                .orElseGet(() -> externalRequestRepository.save(new ExternalRequest(buildingId, floor, direction)));
    }

    /** Floor button pressed inside an elevator. */
    public InternalRequest createInternalRequest(String elevatorId, int destinationFloor) {
        Elevator elevator = elevatorRepository.findElevatorById(elevatorId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown elevator " + elevatorId));
        Building building = getRunningBuilding(elevator.getBuildingId());
        if (!building.isValidFloor(destinationFloor)) {
            throw new IllegalArgumentException("Floor " + destinationFloor + " does not exist in " + building.getName());
        }
        if (!elevator.isAvailable()) {
            throw new IllegalStateException(elevator.getName() + " is not in service");
        }

        InternalRequest request = internalRequestRepository.save(
                new InternalRequest(elevatorId, elevator.getBuildingId(), destinationFloor));
        schedulerService.addCarCall(elevator, destinationFloor);
        return request;
    }

    /** Marks every request answered by the elevator stopping at its current floor as completed. */
    public void completeRequestsAtCurrentFloor(Elevator elevator, Set<Direction> servedDirections) {
        int floor = elevator.getCurrentFloor();
        internalRequestRepository.findPendingByElevator(elevator.getElevatorId()).stream()
                .filter(r -> r.getDestinationFloor() == floor)
                .forEach(r -> internalRequestRepository.updateRequestStatus(r.getId(), RequestStatus.COMPLETED));
        externalRequestRepository.findAssignedByElevator(elevator.getElevatorId()).stream()
                .filter(r -> r.getFloorNumber() == floor && servedDirections.contains(r.getDirection()))
                .forEach(r -> externalRequestRepository.updateRequestStatus(r.getId(), RequestStatus.COMPLETED));
    }

    /** Used when an elevator goes out of service: hall calls go back to the queue, car calls are cancelled. */
    public void releaseRequests(Elevator elevator) {
        for (ExternalRequest request : externalRequestRepository.findAssignedByElevator(elevator.getElevatorId())) {
            request.setAssignedElevatorId(null);
            request.setRequestStatus(RequestStatus.QUEUED);
        }
        internalRequestRepository.findPendingByElevator(elevator.getElevatorId())
                .forEach(r -> internalRequestRepository.updateRequestStatus(r.getId(), RequestStatus.CANCELLED));
    }

    public List<ExternalRequest> findQueuedRequests(String buildingId) {
        return externalRequestRepository.findQueuedRequests(buildingId);
    }

    public boolean hasOutstandingRequests(String buildingId) {
        boolean external = externalRequestRepository.findByBuilding(buildingId).stream()
                .anyMatch(ExternalRequest::isActive);
        boolean internal = internalRequestRepository.findAll().stream()
                .anyMatch(r -> r.getBuildingId().equals(buildingId) && r.getRequestStatus() == RequestStatus.PENDING);
        return external || internal;
    }

    private Building getRunningBuilding(String buildingId) {
        Building building = buildingRepository.getBuildingById(buildingId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown building " + buildingId));
        if (building.getSystemState() != SystemState.RUNNING) {
            throw new IllegalStateException(building.getName() + " is " + building.getSystemState()
                    + "; new requests are not accepted");
        }
        return building;
    }
}
