package elevatorSystem;

import elevatorSystem.Entity.Building;
import elevatorSystem.Entity.Direction;
import elevatorSystem.Entity.Elevator;
import elevatorSystem.Entity.ElevatorState;
import elevatorSystem.Entity.ExternalRequest;
import elevatorSystem.Entity.InternalRequest;
import elevatorSystem.Entity.RequestStatus;
import elevatorSystem.Entity.SystemState;
import elevatorSystem.controller.ElevatorController;
import elevatorSystem.controller.ElevatorPanelController;
import elevatorSystem.controller.FloorPanelController;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Dependency-free test runner: compile together with src/ and run the main method. */
public class ElevatorSystemTest {
    private static final Pattern STOP = Pattern.compile("(E\\d+): stopped at floor (\\d+)");

    private final List<String> log = new ArrayList<>();
    private final ElevatorController controller;
    private final FloorPanelController floorPanel;
    private final ElevatorPanelController elevatorPanel;

    private ElevatorSystemTest() {
        ElevatorSystem system = new ElevatorSystem(log::add);
        controller = system.getElevatorController();
        floorPanel = system.getFloorPanelController();
        elevatorPanel = system.getElevatorPanelController();
    }

    public static void main(String[] args) {
        int failed = 0;
        failed += run("car calls are served in LOOK order", ElevatorSystemTest::carCallsServedInSweepOrder);
        failed += run("down hall call is skipped on the way up", ElevatorSystemTest::hallCallServedOnlyInItsDirection);
        failed += run("nearest idle elevator is chosen", ElevatorSystemTest::nearestElevatorIsChosen);
        failed += run("selection prefers elevators already heading to the caller", ElevatorSystemTest::elevatorOnTheWayIsPreferred);
        failed += run("pressing a lit button does not duplicate the request", ElevatorSystemTest::duplicatePressIsIgnored);
        failed += run("maintenance hands hall calls to another elevator", ElevatorSystemTest::maintenanceRequeuesHallCalls);
        failed += run("invalid requests are rejected", ElevatorSystemTest::invalidRequestsRejected);
        failed += run("graceful stop finishes outstanding requests", ElevatorSystemTest::gracefulStop);

        System.out.println(failed == 0 ? "\nAll tests passed" : "\n" + failed + " test(s) failed");
        if (failed > 0) {
            System.exit(1);
        }
    }

    private static void carCallsServedInSweepOrder() {
        ElevatorSystemTest t = new ElevatorSystemTest();
        String b = t.building(1);
        Elevator e1 = t.controller.getElevators(b).get(0);
        t.elevatorPanel.pressFloorButton(e1.getElevatorId(), 7);
        t.elevatorPanel.pressFloorButton(e1.getElevatorId(), 3);
        t.elevatorPanel.pressFloorButton(e1.getElevatorId(), 5);
        t.controller.runUntilIdle(b, 100);
        assertEquals(List.of("E1@3", "E1@5", "E1@7"), t.stops());
    }

    private static void hallCallServedOnlyInItsDirection() {
        ElevatorSystemTest t = new ElevatorSystemTest();
        String b = t.building(1);
        Elevator e1 = t.controller.getElevators(b).get(0);
        t.elevatorPanel.pressFloorButton(e1.getElevatorId(), 8);
        t.controller.tick(b);
        ExternalRequest down = t.floorPanel.pressButton(b, 4, Direction.DOWN);
        t.controller.runUntilIdle(b, 100);
        assertEquals(List.of("E1@8", "E1@4"), t.stops());
        assertEquals(RequestStatus.COMPLETED, down.getRequestStatus());
    }

    private static void nearestElevatorIsChosen() {
        ElevatorSystemTest t = new ElevatorSystemTest();
        String b = t.building(2);
        Elevator e2 = t.controller.getElevators(b).get(1);
        t.elevatorPanel.pressFloorButton(e2.getElevatorId(), 8);
        t.controller.runUntilIdle(b, 100);
        ExternalRequest call = t.floorPanel.pressButton(b, 6, Direction.DOWN);
        assertEquals(e2.getElevatorId(), call.getAssignedElevatorId());
    }

    private static void elevatorOnTheWayIsPreferred() {
        ElevatorSystemTest t = new ElevatorSystemTest();
        String b = t.building(2);
        Elevator e1 = t.controller.getElevators(b).get(0);
        Elevator e2 = t.controller.getElevators(b).get(1);
        // E2 parks at floor 6; E1 starts heading up to 10 from the ground floor.
        t.elevatorPanel.pressFloorButton(e2.getElevatorId(), 6);
        t.controller.runUntilIdle(b, 100);
        t.elevatorPanel.pressFloorButton(e1.getElevatorId(), 10);
        for (int i = 0; i < 3; i++) {
            t.controller.tick(b);
        }
        // E1 is at floor 3 going up: an UP call at floor 4 is on its way.
        ExternalRequest onTheWay = t.floorPanel.pressButton(b, 4, Direction.UP);
        assertEquals(e1.getElevatorId(), onTheWay.getAssignedElevatorId());
        // A DOWN call at floor 2 is behind E1, so idle E2 must take it even though E1 is closer.
        ExternalRequest behindE1 = t.floorPanel.pressButton(b, 2, Direction.DOWN);
        assertEquals(e2.getElevatorId(), behindE1.getAssignedElevatorId());
    }

    private static void duplicatePressIsIgnored() {
        ElevatorSystemTest t = new ElevatorSystemTest();
        String b = t.building(2);
        ExternalRequest first = t.floorPanel.pressButton(b, 4, Direction.UP);
        ExternalRequest second = t.floorPanel.pressButton(b, 4, Direction.UP);
        assertEquals(first.getId(), second.getId());
    }

    private static void maintenanceRequeuesHallCalls() {
        ElevatorSystemTest t = new ElevatorSystemTest();
        String b = t.building(2);
        ExternalRequest call = t.floorPanel.pressButton(b, 5, Direction.UP);
        String original = call.getAssignedElevatorId();
        InternalRequest carCall = t.elevatorPanel.pressFloorButton(original, 9);

        t.controller.startMaintenance(original);
        assertTrue(!original.equals(call.getAssignedElevatorId()), "call should move to the other elevator");
        assertEquals(RequestStatus.ASSIGNED, call.getRequestStatus());
        assertEquals(RequestStatus.CANCELLED, carCall.getRequestStatus());
        t.controller.runUntilIdle(b, 100);
        assertEquals(RequestStatus.COMPLETED, call.getRequestStatus());

        Elevator broken = t.controller.getElevators(b).stream()
                .filter(e -> e.getElevatorId().equals(original)).findFirst().orElseThrow();
        assertEquals(ElevatorState.MAINTENANCE, broken.getElevatorState());
        assertEquals(0, broken.getCurrentFloor());
        assertThrows(() -> t.elevatorPanel.pressFloorButton(original, 3));
    }

    private static void invalidRequestsRejected() {
        ElevatorSystemTest t = new ElevatorSystemTest();
        String b = t.building(1);
        assertThrows(() -> t.floorPanel.pressButton(b, 11, Direction.UP));
        assertThrows(() -> t.floorPanel.pressButton(b, 10, Direction.UP));
        assertThrows(() -> t.floorPanel.pressButton(b, 0, Direction.DOWN));
        assertThrows(() -> t.floorPanel.pressButton(b, 3, Direction.IDLE));
        assertThrows(() -> new Building("bad", 5, 5, 1));
    }

    private static void gracefulStop() {
        ElevatorSystemTest t = new ElevatorSystemTest();
        String b = t.building(1);
        ExternalRequest call = t.floorPanel.pressButton(b, 6, Direction.DOWN);
        t.controller.stop(b);
        assertThrows(() -> t.floorPanel.pressButton(b, 2, Direction.UP));
        t.controller.runUntilIdle(b, 100);
        assertEquals(RequestStatus.COMPLETED, call.getRequestStatus());
        t.controller.tick(b);
        assertTrue(t.log.stream().anyMatch(l -> l.contains("system stopped")), "system should report STOPPED");
        t.controller.printStatus(b);
        assertTrue(t.log.stream().anyMatch(l -> l.contains("(" + SystemState.STOPPED)), "status should be STOPPED");
    }

    // --- helpers ---

    private String building(int elevators) {
        Building building = controller.createBuilding("Test Tower", 0, 10, elevators, 8);
        controller.start(building.getId());
        return building.getId();
    }

    private List<String> stops() {
        List<String> stops = new ArrayList<>();
        for (String line : log) {
            Matcher m = STOP.matcher(line);
            if (m.find()) {
                stops.add(m.group(1) + "@" + m.group(2));
            }
        }
        return stops;
    }

    private static int run(String name, Runnable test) {
        try {
            test.run();
            System.out.println("PASS  " + name);
            return 0;
        } catch (Throwable e) {
            System.out.println("FAIL  " + name + " -> " + e);
            return 1;
        }
    }

    private static void assertEquals(Object expected, Object actual) {
        if (!expected.equals(actual)) {
            throw new AssertionError("expected " + expected + " but was " + actual);
        }
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void assertThrows(Runnable action) {
        try {
            action.run();
        } catch (IllegalArgumentException | IllegalStateException expected) {
            return;
        }
        throw new AssertionError("expected an exception");
    }
}
