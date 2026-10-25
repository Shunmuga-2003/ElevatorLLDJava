package elevatorSystem;

import elevatorSystem.controller.BuildingLocks;
import elevatorSystem.controller.ElevatorController;
import elevatorSystem.controller.ElevatorPanelController;
import elevatorSystem.controller.FloorPanelController;
import elevatorSystem.event.ElevatorEventListener;
import elevatorSystem.event.EventPublisher;
import elevatorSystem.event.LoggingListener;
import elevatorSystem.event.WaitTimeMetrics;
import elevatorSystem.repository.impl.BuildingRepositoryImpl;
import elevatorSystem.repository.impl.ElevatorRepositoryImpl;
import elevatorSystem.repository.impl.ExternalRequestRepositoryImpl;
import elevatorSystem.repository.impl.InternalRequestRepositoryImpl;
import elevatorSystem.service.BuildingService;
import elevatorSystem.service.DispatcherService;
import elevatorSystem.service.ElevatorSchedulerService;
import elevatorSystem.service.ElevatorService;
import elevatorSystem.service.MovementService;
import elevatorSystem.service.RequestService;
import elevatorSystem.repository.BuildingRepository;
import elevatorSystem.repository.ElevatorRepository;
import elevatorSystem.repository.ExternalRequestRepository;
import elevatorSystem.repository.InternalRequestRepository;
import elevatorSystem.strategy.ElevatorSelectionStrategy;
import elevatorSystem.strategy.SelectionStrategyFactory;
import elevatorSystem.strategy.StrategyType;

import java.util.function.Consumer;

/** Composition root: wires repositories, services and controllers together (manual dependency injection). */
public final class ElevatorSystem {
    private final EventPublisher events = new EventPublisher();
    private final ElevatorController elevatorController;
    private final FloorPanelController floorPanelController;
    private final ElevatorPanelController elevatorPanelController;

    /** Default strategy, every event logged to {@code log}. */
    public ElevatorSystem(Consumer<String> log) {
        this(StrategyType.NEAREST, log);
    }

    public ElevatorSystem(StrategyType strategyType, Consumer<String> log) {
        this(SelectionStrategyFactory.create(strategyType), log);
    }

    public ElevatorSystem(ElevatorSelectionStrategy strategy, Consumer<String> log) {
        this(strategy);
        events.subscribe(new LoggingListener(log));
    }

    public ElevatorSystem(ElevatorSelectionStrategy strategy) {
        BuildingRepository buildingRepository = new BuildingRepositoryImpl();
        ElevatorRepository elevatorRepository = new ElevatorRepositoryImpl();
        ExternalRequestRepository externalRequestRepository = new ExternalRequestRepositoryImpl();
        InternalRequestRepository internalRequestRepository = new InternalRequestRepositoryImpl();

        ElevatorSchedulerService schedulerService = new ElevatorSchedulerService();
        RequestService requestService = new RequestService(externalRequestRepository, internalRequestRepository);
        ElevatorService elevatorService = new ElevatorService(elevatorRepository, requestService, events);
        BuildingService buildingService = new BuildingService(buildingRepository, elevatorService, events);
        DispatcherService dispatcherService = new DispatcherService(elevatorService, requestService, strategy);
        MovementService movementService = new MovementService(schedulerService, requestService, events);

        WaitTimeMetrics metrics = new WaitTimeMetrics();
        events.subscribe(metrics);

        // one lock per building: presses and ticks of one building never interleave, buildings run in parallel
        BuildingLocks locks = new BuildingLocks();
        this.elevatorController = new ElevatorController(buildingService, elevatorService, requestService,
                dispatcherService, movementService, metrics, locks);
        this.floorPanelController = new FloorPanelController(buildingService, requestService, dispatcherService, locks);
        this.elevatorPanelController = new ElevatorPanelController(buildingService, elevatorService, requestService,
                dispatcherService, locks);
    }

    /** Adds a subscriber to every event in every building (displays, audit logs, metrics exporters...). */
    public void subscribe(ElevatorEventListener listener) {
        events.subscribe(listener);
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
