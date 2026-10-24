package elevatorSystem;

import elevatorSystem.Repository.BuildingRepository;
import elevatorSystem.Repository.ElevatorRepository;
import elevatorSystem.Repository.ExternalRequestRepository;
import elevatorSystem.Repository.InternalRequestRepository;
import elevatorSystem.Repository.impl.BuildingRepositoryImpl;
import elevatorSystem.Repository.impl.ElevatorRepositoryImpl;
import elevatorSystem.Repository.impl.ExternalRequestRepositoryImpl;
import elevatorSystem.Repository.impl.InternalRequestRepositoryImpl;
import elevatorSystem.controller.ElevatorController;
import elevatorSystem.controller.ElevatorPanelController;
import elevatorSystem.controller.FloorPanelController;
import elevatorSystem.controller.SystemLock;
import elevatorSystem.service.BuildingService;
import elevatorSystem.service.DispatcherService;
import elevatorSystem.service.ElevatorSchedulerService;
import elevatorSystem.service.ElevatorService;
import elevatorSystem.service.MovementService;
import elevatorSystem.service.RequestService;
import elevatorSystem.strategy.ElevatorSelectionStrategy;
import elevatorSystem.strategy.SelectionStrategyFactory;
import elevatorSystem.strategy.StrategyType;

import java.util.function.Consumer;

/** Composition root: wires repositories, services and controllers together (manual dependency injection). */
public class ElevatorSystem {
    private final ElevatorController elevatorController;
    private final FloorPanelController floorPanelController;
    private final ElevatorPanelController elevatorPanelController;

    public ElevatorSystem(Consumer<String> log) {
        this(StrategyType.NEAREST, log);
    }

    public ElevatorSystem(StrategyType strategyType, Consumer<String> log) {
        this(SelectionStrategyFactory.create(strategyType), log);
    }

    public ElevatorSystem(ElevatorSelectionStrategy strategy, Consumer<String> log) {
        BuildingRepository buildingRepository = new BuildingRepositoryImpl();
        ElevatorRepository elevatorRepository = new ElevatorRepositoryImpl();
        ExternalRequestRepository externalRequestRepository = new ExternalRequestRepositoryImpl();
        InternalRequestRepository internalRequestRepository = new InternalRequestRepositoryImpl();

        ElevatorSchedulerService schedulerService = new ElevatorSchedulerService();
        RequestService requestService = new RequestService(buildingRepository, elevatorRepository,
                externalRequestRepository, internalRequestRepository, schedulerService);
        ElevatorService elevatorService = new ElevatorService(elevatorRepository, requestService, schedulerService);
        BuildingService buildingService = new BuildingService(buildingRepository, elevatorService);
        DispatcherService dispatcherService = new DispatcherService(buildingRepository, elevatorRepository,
                requestService, schedulerService, strategy);
        MovementService movementService = new MovementService(schedulerService, requestService);

        // all controllers share one lock, so button presses and clock ticks never interleave
        SystemLock lock = new SystemLock();
        this.elevatorController = new ElevatorController(buildingService, elevatorService, requestService,
                dispatcherService, movementService, log, lock);
        this.floorPanelController = new FloorPanelController(requestService, dispatcherService, lock);
        this.elevatorPanelController = new ElevatorPanelController(requestService, lock);
    }

    public ElevatorController getElevatorController() {
        return elevatorController;
    }

    public FloorPanelController getFloorPanelController() {
        return floorPanelController;
    }

    public ElevatorPanelController getElevatorPanelController() {
        return elevatorPanelController;
    }
}
