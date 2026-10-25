package elevatorSystem.service;

import elevatorSystem.entity.Building;
import elevatorSystem.entity.Elevator;
import elevatorSystem.entity.ExternalRequest;
import elevatorSystem.entity.RequestStatus;
import elevatorSystem.strategy.ElevatorSelectionStrategy;

import java.util.Optional;

/** Assigns hall calls to elevators. The selection rule is pluggable via {@link ElevatorSelectionStrategy}. */
public class DispatcherService {
    private final ElevatorService elevatorService;
    private final RequestService requestService;
    private volatile ElevatorSelectionStrategy selectionStrategy;

    public DispatcherService(ElevatorService elevatorService, RequestService requestService,
                             ElevatorSelectionStrategy selectionStrategy) {
        this.elevatorService = elevatorService;
        this.requestService = requestService;
        this.selectionStrategy = selectionStrategy;
    }

    /** Swaps the rule for new hall calls. Strategies are shared by all buildings, so they must be thread-safe. */
    public void setSelectionStrategy(ElevatorSelectionStrategy selectionStrategy) {
        this.selectionStrategy = selectionStrategy;
    }

    /** @return the elevator the request was assigned to, or empty if none is available (request stays queued). */
    public Optional<Elevator> dispatch(Building building, ExternalRequest request) {
        if (request.getStatus() != RequestStatus.QUEUED) {
            return Optional.empty();
        }
        ElevatorSelectionStrategy strategy = selectionStrategy; // read the volatile once
        Optional<Elevator> selected = strategy.selectElevator(
                elevatorService.findAvailableForDispatch(building.getId()), request, building);
        selected.ifPresent(elevator -> requestService.assign(request, elevator));
        return selected;
    }

    /** Retries every request that could not be assigned earlier, oldest first. */
    public void dispatchQueued(Building building) {
        requestService.findQueued(building.getId()).forEach(request -> dispatch(building, request));
    }
}
