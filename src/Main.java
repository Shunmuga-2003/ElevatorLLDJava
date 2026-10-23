import elevatorSystem.ElevatorSystem;
import elevatorSystem.Entity.Building;
import elevatorSystem.Entity.Direction;
import elevatorSystem.Entity.Elevator;
import elevatorSystem.Entity.ExternalRequest;
import elevatorSystem.Entity.RequestStatus;
import elevatorSystem.controller.ElevatorController;
import elevatorSystem.controller.ElevatorPanelController;
import elevatorSystem.controller.FloorPanelController;

/** Runs a few scenarios end to end and prints what every elevator does on each tick. */
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

        Building building = controller.createBuilding("Tech Park", 0, 10, 3, 8);
        buildingId = building.getId();
        controller.start(buildingId);
        controller.printStatus(buildingId);

        section("1. Someone on floor 5 wants to go up to floor 9");
        ExternalRequest call = floorPanel.pressButton(buildingId, 5, Direction.UP);
        Elevator car = waitForPickup(call);
        elevatorPanel.pressFloorButton(car.getElevatorId(), 9);
        controller.runUntilIdle(buildingId, 50);

        section("2. Several people call at once; each call goes to the best-placed elevator");
        call = floorPanel.pressButton(buildingId, 2, Direction.UP);
        floorPanel.pressButton(buildingId, 7, Direction.DOWN);
        floorPanel.pressButton(buildingId, 10, Direction.DOWN);
        car = waitForPickup(call);
        elevatorPanel.pressFloorButton(car.getElevatorId(), 6);
        controller.runUntilIdle(buildingId, 50);
        controller.printStatus(buildingId);

        section("3. An elevator goes into maintenance; its hall call is handed to another elevator");
        Elevator e1 = controller.getElevators(buildingId).get(0);
        call = floorPanel.pressButton(buildingId, e1.getCurrentFloor() == 0 ? 4 : 0, Direction.UP);
        controller.startMaintenance(call.getAssignedElevatorId());
        controller.runUntilIdle(buildingId, 50);
        controller.printStatus(buildingId);

        section("4. Graceful shutdown");
        controller.stop(buildingId);
        try {
            floorPanel.pressButton(buildingId, 3, Direction.UP);
        } catch (IllegalStateException e) {
            System.out.println("Rejected: " + e.getMessage());
        }
        controller.printStatus(buildingId);
    }

    private static Elevator waitForPickup(ExternalRequest request) {
        for (int i = 0; i < 50 && request.getRequestStatus() != RequestStatus.COMPLETED; i++) {
            controller.tick(buildingId);
        }
        if (request.getRequestStatus() != RequestStatus.COMPLETED) {
            throw new IllegalStateException("Request on floor " + request.getFloorNumber() + " was never served");
        }
        return controller.getElevators(buildingId).stream()
                .filter(e -> e.getElevatorId().equals(request.getAssignedElevatorId()))
                .findFirst()
                .orElseThrow();
    }

    private static void section(String title) {
        System.out.println();
        System.out.println("=== " + title + " ===");
    }
}
