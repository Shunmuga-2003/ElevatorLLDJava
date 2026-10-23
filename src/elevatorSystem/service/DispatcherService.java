package elevatorSystem.service;

import elevatorSystem.Entity.Building;
import elevatorSystem.Entity.Elevator;
import elevatorSystem.Entity.ExternalRequest;
import elevatorSystem.Entity.RequestStatus;
import elevatorSystem.Repository.BuildingRepository;
import elevatorSystem.Repository.ElevatorRepository;
import elevatorSystem.strategy.ElevatorSelectionStrategy;

import java.util.Optional;

/** Assigns hall calls to elevators. The selection rule is pluggable via {@link ElevatorSelectionStrategy}. */
public class DispatcherService {
    private final BuildingRepository buildingRepository;
    private final ElevatorRepository elevatorRepository;
    private final RequestService requestService;
    private final ElevatorSchedulerService schedulerService;
    private ElevatorSelectionStrategy selectionStrategy;

    public DispatcherService(BuildingRepository buildingRepository,
                             ElevatorRepository elevatorRepository,
                             RequestService requestService,
                             ElevatorSchedulerService schedulerService,
                             ElevatorSelectionStrategy selectionStrategy) {
        this.buildingRepository = buildingRepository;
        this.elevatorRepository = elevatorRepository;
        this.requestService = requestService;
        this.schedulerService = schedulerService;
        this.selectionStrategy = selectionStrategy;
    }

    public void setSelectionStrategy(ElevatorSelectionStrategy selectionStrategy) {
        this.selectionStrategy = selectionStrategy;
    }

    /** @return the elevator the request was assigned to, or empty if none is available (request stays queued). */
    public Optional<Elevator> dispatch(ExternalRequest request) {
        if (request.getRequestStatus() != RequestStatus.QUEUED) {
            return Optional.empty();
        }
        Building building = buildingRepository.getBuildingById(request.getBuildingId())
                .orElseThrow(() -> new IllegalArgumentException("Unknown building " + request.getBuildingId()));

        Optional<Elevator> selected = selectionStrategy.selectElevator(
                elevatorRepository.findAvailableElevators(building.getId()), request, building);
        selected.ifPresent(elevator -> {
            request.setAssignedElevatorId(elevator.getElevatorId());
            request.setRequestStatus(RequestStatus.ASSIGNED);
            schedulerService.addHallCall(elevator, request.getFloorNumber(), request.getDirection());
        });
        return selected;
    }

    /** Retries every request that could not be assigned earlier. */
    public void dispatchQueuedRequests(String buildingId) {
        requestService.findQueuedRequests(buildingId).forEach(this::dispatch);
    }
}
