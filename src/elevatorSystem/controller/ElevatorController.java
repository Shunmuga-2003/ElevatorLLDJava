package elevatorSystem.controller;

import elevatorSystem.dto.BuildingView;
import elevatorSystem.dto.CarCallView;
import elevatorSystem.dto.ElevatorView;
import elevatorSystem.dto.HallCallView;
import elevatorSystem.entity.Building;
import elevatorSystem.entity.Elevator;
import elevatorSystem.entity.ElevatorState;
import elevatorSystem.entity.SystemState;
import elevatorSystem.event.WaitTimeMetrics;
import elevatorSystem.service.BuildingService;
import elevatorSystem.service.DispatcherService;
import elevatorSystem.service.ElevatorService;
import elevatorSystem.service.MovementService;
import elevatorSystem.service.RequestService;
import elevatorSystem.strategy.ElevatorSelectionStrategy;
import elevatorSystem.strategy.SelectionStrategyFactory;
import elevatorSystem.strategy.StrategyType;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Operator-facing API: building lifecycle, maintenance, emergency recall, strategy and the simulation clock.
 * Safe to call from any thread. Every call holds only its own building's lock, and every result is an
 * immutable snapshot, so no caller ever holds a live, mutable domain object.
 */
public class ElevatorController {
    private final BuildingService buildingService;
    private final ElevatorService elevatorService;
    private final RequestService requestService;
    private final DispatcherService dispatcherService;
    private final MovementService movementService;
    private final WaitTimeMetrics metrics;
    private final BuildingLocks locks;

    public ElevatorController(BuildingService buildingService,
                              ElevatorService elevatorService,
                              RequestService requestService,
                              DispatcherService dispatcherService,
                              MovementService movementService,
                              WaitTimeMetrics metrics,
                              BuildingLocks locks) {
        this.buildingService = buildingService;
        this.elevatorService = elevatorService;
        this.requestService = requestService;
        this.dispatcherService = dispatcherService;
        this.movementService = movementService;
        this.metrics = metrics;
        this.locks = locks;
    }

    // --- building lifecycle ---

    /** Creates a building whose recall floor (for emergencies) is its lowest floor. */
    public BuildingView createBuilding(String name, int minFloor, int maxFloor, int elevatorCount, int elevatorCapacity) {
        return createBuilding(name, minFloor, maxFloor, minFloor, elevatorCount, elevatorCapacity);
    }

    public BuildingView createBuilding(String name, int minFloor, int maxFloor, int recallFloor,
                                       int elevatorCount, int elevatorCapacity) {
        // no lock needed: nobody else can know the new building's id yet
        return BuildingView.of(buildingService.createBuilding(name, minFloor, maxFloor, recallFloor,
                elevatorCount, elevatorCapacity));
    }

    public void start(String buildingId) {
        locks.run(buildingId, () -> buildingService.transition(buildingService.getBuilding(buildingId), SystemState.RUNNING));
    }

    /** Graceful stop: no new requests are accepted, outstanding ones are finished, then the system is STOPPED. */
    public void stop(String buildingId) {
        locks.run(buildingId, () -> {
            Building building = buildingService.getBuilding(buildingId);
            buildingService.transition(building, SystemState.STOPPING);
            if (isIdleUnlocked(buildingId)) {
                buildingService.transition(building, SystemState.STOPPED);
            }
        });
    }

    /**
     * Fire recall: every active request is cancelled, new ones are rejected, and every in-service car
     * travels non-stop to the recall floor and parks there until {@link #clearEmergency}.
     */
    public void emergencyRecall(String buildingId) {
        locks.run(buildingId, () -> {
            Building building = buildingService.getBuilding(buildingId);
            buildingService.transition(building, SystemState.EMERGENCY);
            requestService.cancelAll(building);
            elevatorService.findByBuilding(buildingId).forEach(e -> elevatorService.recall(e, building));
        });
    }

    public void clearEmergency(String buildingId) {
        locks.run(buildingId, () -> {
            Building building = buildingService.getBuilding(buildingId);
            if (building.getSystemState() != SystemState.EMERGENCY) {
                throw new IllegalStateException(building.getName() + " is not in an emergency");
            }
            buildingService.transition(building, SystemState.RUNNING);
        });
    }

    // --- elevators ---

    /** Swaps the rule used to pick an elevator for new hall calls. Already-assigned calls keep their elevator. */
    public void setSelectionStrategy(StrategyType type) {
        setSelectionStrategy(SelectionStrategyFactory.create(type));
    }

    /** Same as {@link #setSelectionStrategy(StrategyType)}, for a custom strategy that is not one of the built-in types. */
    public void setSelectionStrategy(ElevatorSelectionStrategy strategy) {
        dispatcherService.setSelectionStrategy(strategy); // volatile write, no building lock needed
    }

    public void startMaintenance(String elevatorId) {
        String buildingId = buildingOf(elevatorId);
        locks.run(buildingId, () -> {
            Building building = buildingService.getBuilding(buildingId);
            elevatorService.startMaintenance(building, elevatorService.getElevator(elevatorId));
            dispatcherService.dispatchQueued(building);
        });
    }

    public void endMaintenance(String elevatorId) {
        String buildingId = buildingOf(elevatorId);
        locks.run(buildingId, () -> elevatorService.endMaintenance(
                buildingService.getBuilding(buildingId), elevatorService.getElevator(elevatorId)));
    }

    // --- clock ---

    /** Advances the building's simulation by one tick. */
    public void tick(String buildingId) {
        locks.run(buildingId, () -> {
            Building building = buildingService.getBuilding(buildingId);
            building.advanceClock();
            dispatcherService.dispatchQueued(building);
            for (Elevator elevator : elevatorService.findByBuilding(buildingId)) {
                movementService.step(building, elevator);
            }
            if (building.getSystemState() == SystemState.STOPPING && isIdleUnlocked(buildingId)) {
                buildingService.transition(building, SystemState.STOPPED);
            }
        });
    }

    /**
     * Ticks until every request is served and all cars are parked. @return ticks taken.
     * The lock is taken per tick, not for the whole run, so button presses from other threads still get through.
     */
    public int runUntilIdle(String buildingId, int maxTicks) {
        for (int ticks = 0; ; ticks++) {
            if (isIdle(buildingId)) {
                return ticks;
            }
            if (ticks >= maxTicks) {
                throw new IllegalStateException("System did not settle within " + maxTicks + " ticks");
            }
            tick(buildingId);
        }
    }

    public boolean isIdle(String buildingId) {
        return locks.call(buildingId, () -> isIdleUnlocked(buildingId));
    }

    // --- queries (immutable snapshots) ---

    public BuildingView getBuilding(String buildingId) {
        return locks.call(buildingId, () -> BuildingView.of(buildingService.getBuilding(buildingId)));
    }

    public List<ElevatorView> getElevators(String buildingId) {
        return locks.call(buildingId, () -> elevatorService.findByBuilding(buildingId).stream()
                .map(ElevatorView::of)
                .collect(Collectors.toList()));
    }

    public ElevatorView getElevator(String elevatorId) {
        return locks.call(buildingOf(elevatorId), () -> ElevatorView.of(elevatorService.getElevator(elevatorId)));
    }

    public HallCallView getHallCall(String requestId) {
        String buildingId = requestService.getHallCall(requestId).getBuildingId();
        return locks.call(buildingId, () -> HallCallView.of(requestService.getHallCall(requestId)));
    }

    public CarCallView getCarCall(String requestId) {
        String buildingId = requestService.getCarCall(requestId).getBuildingId();
        return locks.call(buildingId, () -> CarCallView.of(requestService.getCarCall(requestId)));
    }

    public WaitTimeMetrics.Stats getWaitTimeStats(String buildingId) {
        return metrics.statsFor(buildingId);
    }

    /** Multi-line status: the building, then one line per elevator. */
    public String statusReport(String buildingId) {
        return locks.call(buildingId, () -> {
            Building b = buildingService.getBuilding(buildingId);
            StringBuilder report = new StringBuilder(String.format("%s (%s, floors %d-%d, t=%d)",
                    b.getName(), b.getSystemState(), b.getMinFloor(), b.getMaxFloor(), b.getCurrentTick()));
            elevatorService.findByBuilding(buildingId).forEach(e -> report.append("\n  ").append(e));
            return report.toString();
        });
    }

    private boolean isIdleUnlocked(String buildingId) {
        if (requestService.hasOutstandingRequests(buildingId)) {
            return false;
        }
        return elevatorService.findByBuilding(buildingId).stream()
                .allMatch(e -> !e.hasPendingStops()
                        && (e.getState() == ElevatorState.IDLE || e.getState() == ElevatorState.MAINTENANCE));
    }

    /** An elevator's building never changes, so it can be read without a lock to find which lock to take. */
    private String buildingOf(String elevatorId) {
        return elevatorService.getElevator(elevatorId).getBuildingId();
    }
}
