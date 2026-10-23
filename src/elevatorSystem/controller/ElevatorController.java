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

import java.util.List;
import java.util.function.Consumer;

/** System-level controller: starts/stops a building and drives the simulation clock. */
public class ElevatorController {
    private final BuildingService buildingService;
    private final ElevatorService elevatorService;
    private final RequestService requestService;
    private final DispatcherService dispatcherService;
    private final MovementService movementService;
    private final Consumer<String> log;
    private int tick;

    public ElevatorController(BuildingService buildingService,
                              ElevatorService elevatorService,
                              RequestService requestService,
                              DispatcherService dispatcherService,
                              MovementService movementService,
                              Consumer<String> log) {
        this.buildingService = buildingService;
        this.elevatorService = elevatorService;
        this.requestService = requestService;
        this.dispatcherService = dispatcherService;
        this.movementService = movementService;
        this.log = log;
    }

    public Building createBuilding(String name, int minFloor, int maxFloor, int elevatorCount, int elevatorCapacity) {
        return buildingService.createBuilding(name, minFloor, maxFloor, elevatorCount, elevatorCapacity);
    }

    public void start(String buildingId) {
        buildingService.updateSystemState(buildingId, SystemState.RUNNING);
    }

    /** Graceful stop: no new requests are accepted, outstanding ones are finished, then the system is STOPPED. */
    public void stop(String buildingId) {
        buildingService.updateSystemState(buildingId, SystemState.STOPPING);
        if (isIdle(buildingId)) {
            buildingService.updateSystemState(buildingId, SystemState.STOPPED);
        }
    }

    public void startMaintenance(String elevatorId) {
        Elevator elevator = elevatorService.getElevator(elevatorId);
        elevatorService.startMaintenance(elevatorId);
        log.accept(String.format("t=%03d %s: out of service", tick, elevator.getName()));
        dispatcherService.dispatchQueuedRequests(elevator.getBuildingId());
    }

    public void endMaintenance(String elevatorId) {
        elevatorService.endMaintenance(elevatorId);
        log.accept(String.format("t=%03d %s: back in service", tick, elevatorService.getElevator(elevatorId).getName()));
    }

    /** Advances the simulation by one time unit. */
    public void tick(String buildingId) {
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
    }

    /** Ticks until every request is served and all elevators are parked. @return ticks taken. */
    public int runUntilIdle(String buildingId, int maxTicks) {
        int start = tick;
        while (!isIdle(buildingId)) {
            if (tick - start >= maxTicks) {
                throw new IllegalStateException("System did not settle within " + maxTicks + " ticks");
            }
            tick(buildingId);
        }
        return tick - start;
    }

    public boolean isIdle(String buildingId) {
        if (requestService.hasOutstandingRequests(buildingId)) {
            return false;
        }
        return elevatorService.findByBuilding(buildingId).stream()
                .allMatch(e -> !e.hasPendingStops()
                        && (e.getElevatorState() == ElevatorState.STOPPED
                        || e.getElevatorState() == ElevatorState.MAINTENANCE));
    }

    public List<Elevator> getElevators(String buildingId) {
        return elevatorService.findByBuilding(buildingId);
    }

    public void printStatus(String buildingId) {
        Building building = buildingService.getBuilding(buildingId);
        log.accept(String.format("%s (%s, floors %d-%d)", building.getName(), building.getSystemState(),
                building.getMinFloor(), building.getMaxFloor()));
        getElevators(buildingId).forEach(e -> log.accept("  " + e));
    }
}
