package elevatorSystem.controller;

import elevatorSystem.Entity.Building;
import elevatorSystem.Entity.Elevator;
import elevatorSystem.Entity.ElevatorState;
import elevatorSystem.Entity.SystemState;
import elevatorSystem.service.BuildingService;
import elevatorSystem.service.DispatcherService;
import elevatorSystem.service.ElevatorService;
import elevatorSystem.service.MovementService;
import elevatorSystem.service.RequestService;
import elevatorSystem.strategy.ElevatorSelectionStrategy;

import java.util.List;
import java.util.function.Consumer;

/** System-level controller: starts/stops a building and drives the simulation clock. Safe to call from any thread. */
public class ElevatorController {
    private final BuildingService buildingService;
    private final ElevatorService elevatorService;
    private final RequestService requestService;
    private final DispatcherService dispatcherService;
    private final MovementService movementService;
    private final Consumer<String> log;
    private final SystemLock lock;
    private int tick; // guarded by lock

    public ElevatorController(BuildingService buildingService,
                              ElevatorService elevatorService,
                              RequestService requestService,
                              DispatcherService dispatcherService,
                              MovementService movementService,
                              Consumer<String> log,
                              SystemLock lock) {
        this.buildingService = buildingService;
        this.elevatorService = elevatorService;
        this.requestService = requestService;
        this.dispatcherService = dispatcherService;
        this.movementService = movementService;
        this.log = log;
        this.lock = lock;
    }

    public Building createBuilding(String name, int minFloor, int maxFloor, int elevatorCount, int elevatorCapacity) {
        return lock.call(() -> buildingService.createBuilding(name, minFloor, maxFloor, elevatorCount, elevatorCapacity));
    }

    public void start(String buildingId) {
        lock.run(() -> buildingService.updateSystemState(buildingId, SystemState.RUNNING));
    }

    /** Graceful stop: no new requests are accepted, outstanding ones are finished, then the system is STOPPED. */
    public void stop(String buildingId) {
        lock.run(() -> {
            buildingService.updateSystemState(buildingId, SystemState.STOPPING);
            if (isIdle(buildingId)) {
                buildingService.updateSystemState(buildingId, SystemState.STOPPED);
            }
        });
    }

    /** Swaps the rule used to pick an elevator for new hall calls. Already-assigned calls keep their elevator. */
    public void setSelectionStrategy(ElevatorSelectionStrategy strategy) {
        lock.run(() -> dispatcherService.setSelectionStrategy(strategy));
    }

    public void startMaintenance(String elevatorId) {
        lock.run(() -> {
            Elevator elevator = elevatorService.getElevator(elevatorId);
            elevatorService.startMaintenance(elevatorId);
            log.accept(String.format("t=%03d %s: out of service", tick, elevator.getName()));
            dispatcherService.dispatchQueuedRequests(elevator.getBuildingId());
        });
    }

    public void endMaintenance(String elevatorId) {
        lock.run(() -> {
            elevatorService.endMaintenance(elevatorId);
            log.accept(String.format("t=%03d %s: back in service", tick, elevatorService.getElevator(elevatorId).getName()));
        });
    }

    /** Advances the simulation by one time unit. */
    public void tick(String buildingId) {
        lock.run(() -> {
            tick++;
            dispatcherService.dispatchQueuedRequests(buildingId);
            for (Elevator elevator : elevatorService.findByBuilding(buildingId)) {
                movementService.step(elevator)
                        .ifPresent(event -> log.accept(String.format("t=%03d %s: %s", tick, elevator.getName(), event)));
            }

            Building building = buildingService.getBuilding(buildingId);
            if (building.getSystemState() == SystemState.STOPPING && isIdle(buildingId)) {
                buildingService.updateSystemState(buildingId, SystemState.STOPPED);
                log.accept(String.format("t=%03d %s: system stopped", tick, building.getName()));
            }
        });
    }

    /**
     * Ticks until every request is served and all elevators are parked. @return ticks taken.
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
        return lock.call(() -> {
            if (requestService.hasOutstandingRequests(buildingId)) {
                return false;
            }
            return elevatorService.findByBuilding(buildingId).stream()
                    .allMatch(e -> !e.hasPendingStops()
                            && (e.getElevatorState() == ElevatorState.STOPPED
                            || e.getElevatorState() == ElevatorState.MAINTENANCE));
        });
    }

    /** Returns the live elevator objects; read them under this controller's calls, or treat values as a snapshot. */
    public List<Elevator> getElevators(String buildingId) {
        return lock.call(() -> elevatorService.findByBuilding(buildingId));
    }

    public void printStatus(String buildingId) {
        lock.run(() -> {
            Building building = buildingService.getBuilding(buildingId);
            log.accept(String.format("%s (%s, floors %d-%d)", building.getName(), building.getSystemState(),
                    building.getMinFloor(), building.getMaxFloor()));
            elevatorService.findByBuilding(buildingId).forEach(e -> log.accept("  " + e));
        });
    }
}
