import elevatorSystem.ElevatorSystem;
import elevatorSystem.controller.ElevatorController;
import elevatorSystem.controller.ElevatorPanelController;
import elevatorSystem.controller.FloorPanelController;
import elevatorSystem.dto.ElevatorView;
import elevatorSystem.dto.HallCallView;
import elevatorSystem.entity.Direction;
import elevatorSystem.entity.ElevatorState;
import elevatorSystem.entity.RequestStatus;
import elevatorSystem.event.WaitTimeMetrics;
import elevatorSystem.strategy.StrategyType;

/** Runs the scenarios end to end and prints what every elevator does on each tick. */
public class Main {
    private static ElevatorController controller;
    private static FloorPanelController floorPanel;
    private static ElevatorPanelController elevatorPanel;
    private static String buildingId;

    public static void main(String[] args) {
        ElevatorSystem system = new ElevatorSystem(System.out::println);
        controller = system.getElevatorController();
        floorPanel = system.getFloorPanelController();
        elevatorPanel = system.getElevatorPanelController();

        buildingId = controller.createBuilding("Tech Park", 0, 10, 3, 8).id();
        controller.start(buildingId);
        System.out.println(controller.statusReport(buildingId));

        section("1. Someone on floor 5 wants to go up to floor 9");
        HallCallView call = floorPanel.pressButton(buildingId, 5, Direction.UP);
        ElevatorView car = waitForPickup(call);
        elevatorPanel.pressFloorButton(car.elevatorId(), 9);
        controller.runUntilIdle(buildingId, 50);

        section("2. An overloaded car holds its doors open; a full car hands its hall calls to another car");
        call = floorPanel.pressButton(buildingId, 9, Direction.DOWN);
        car = waitForPickup(call);
        elevatorPanel.pressFloorButton(car.elevatorId(), 0);
        HallCallView onTheWay = floorPanel.pressButton(buildingId, 6, Direction.DOWN);
        System.out.printf("floor 6 DOWN -> %s (on its way down)%n", nameOf(onTheWay.assignedElevatorId()));
        elevatorPanel.reportLoad(car.elevatorId(), 9);  // one too many got in: doors stay open, hall calls go elsewhere
        controller.tick(buildingId);
        elevatorPanel.reportLoad(car.elevatorId(), 8);  // one steps out: still full, so it keeps bypassing hall calls
        System.out.printf("floor 6 DOWN -> %s (E1 is full)%n",
                nameOf(controller.getHallCall(onTheWay.id()).assignedElevatorId()));
        controller.runUntilIdle(buildingId, 50);
        elevatorPanel.reportLoad(car.elevatorId(), 0);  // everyone got out at the ground floor

        section("3. Several people call at once; each call goes to the car with the lowest ETA");
        call = floorPanel.pressButton(buildingId, 2, Direction.UP);
        floorPanel.pressButton(buildingId, 7, Direction.DOWN);
        floorPanel.pressButton(buildingId, 10, Direction.DOWN);
        car = waitForPickup(call);
        elevatorPanel.pressFloorButton(car.elevatorId(), 6);
        controller.runUntilIdle(buildingId, 50);
        System.out.println(controller.statusReport(buildingId));

        section("4. An elevator goes into maintenance; its hall call is handed to another elevator");
        call = floorPanel.pressButton(buildingId, 4, Direction.UP);
        controller.startMaintenance(call.assignedElevatorId());
        controller.runUntilIdle(buildingId, 50);
        System.out.println(controller.statusReport(buildingId));
        controller.getElevators(buildingId).stream()
                .filter(e -> e.state() == ElevatorState.MAINTENANCE)
                .forEach(e -> controller.endMaintenance(e.elevatorId()));

        section("5. The same calls under each dispatch strategy (switched at runtime)");
        for (StrategyType type : StrategyType.values()) {
            controller.setSelectionStrategy(type);
            System.out.println("-- " + type);
            for (HallCallView request : new HallCallView[]{
                    floorPanel.pressButton(buildingId, 1, Direction.UP),
                    floorPanel.pressButton(buildingId, 5, Direction.UP),
                    floorPanel.pressButton(buildingId, 9, Direction.DOWN)}) {
                System.out.printf("   floor %d %s -> %s%n", request.floor(), request.direction(),
                        nameOf(request.assignedElevatorId()));
            }
            controller.runUntilIdle(buildingId, 50);
        }
        controller.setSelectionStrategy(StrategyType.NEAREST);

        section("6. Fire alarm: every car is recalled to the lobby and new calls are refused");
        floorPanel.pressButton(buildingId, 7, Direction.DOWN);
        controller.tick(buildingId);
        controller.emergencyRecall(buildingId);
        try {
            floorPanel.pressButton(buildingId, 3, Direction.UP);
        } catch (IllegalStateException e) {
            System.out.println("Rejected: " + e.getMessage());
        }
        controller.runUntilIdle(buildingId, 50);
        System.out.println(controller.statusReport(buildingId));
        controller.clearEmergency(buildingId);

        section("7. Wait-time metrics");
        WaitTimeMetrics.Stats stats = controller.getWaitTimeStats(buildingId);
        System.out.printf("%d hall calls served, average wait %.1f ticks, worst %d ticks%n",
                stats.served(), stats.averageWaitTicks(), stats.maxWaitTicks());

        section("8. Graceful shutdown");
        controller.stop(buildingId);
        try {
            floorPanel.pressButton(buildingId, 3, Direction.UP);
        } catch (IllegalStateException e) {
            System.out.println("Rejected: " + e.getMessage());
        }
        System.out.println(controller.statusReport(buildingId));
    }

    private static ElevatorView waitForPickup(HallCallView request) {
        for (int i = 0; i < 50 && controller.getHallCall(request.id()).status() != RequestStatus.COMPLETED; i++) {
            controller.tick(buildingId);
        }
        HallCallView served = controller.getHallCall(request.id());
        if (served.status() != RequestStatus.COMPLETED) {
            throw new IllegalStateException("Request on floor " + request.floor() + " was never served");
        }
        return controller.getElevator(served.assignedElevatorId());
    }

    private static String nameOf(String elevatorId) {
        return elevatorId == null ? "(queued)" : controller.getElevator(elevatorId).name();
    }

    private static void section(String title) {
        System.out.println();
        System.out.println("=== " + title + " ===");
    }
}
