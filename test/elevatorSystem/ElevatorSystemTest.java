package elevatorSystem;

import elevatorSystem.controller.ElevatorController;
import elevatorSystem.controller.ElevatorPanelController;
import elevatorSystem.controller.FloorPanelController;
import elevatorSystem.controller.RealTimeClock;
import elevatorSystem.dto.CarCallView;
import elevatorSystem.dto.ElevatorView;
import elevatorSystem.dto.HallCallView;
import elevatorSystem.entity.Building;
import elevatorSystem.entity.Direction;
import elevatorSystem.entity.Elevator;
import elevatorSystem.entity.ElevatorState;
import elevatorSystem.entity.ExternalRequest;
import elevatorSystem.entity.RequestStatus;
import elevatorSystem.entity.SystemState;
import elevatorSystem.event.ElevatorEvent;
import elevatorSystem.event.WaitTimeMetrics;
import elevatorSystem.strategy.ElevatorSelectionStrategy;
import elevatorSystem.strategy.LeastBusyStrategy;
import elevatorSystem.strategy.NearestElevatorStrategy;
import elevatorSystem.strategy.RoundRobinStrategy;
import elevatorSystem.strategy.SelectionStrategyFactory;
import elevatorSystem.strategy.StrategyType;
import elevatorSystem.strategy.ZoneBasedStrategy;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Queue;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Dependency-free test runner: compile together with src/ and run the main method. */
public class ElevatorSystemTest {
    private static final Pattern STOP = Pattern.compile("(E\\d+): stopped at floor (\\d+)");

    private final List<String> log = Collections.synchronizedList(new ArrayList<>());
    private final ElevatorSystem system;
    private final ElevatorController controller;
    private final FloorPanelController floorPanel;
    private final ElevatorPanelController elevatorPanel;

    private ElevatorSystemTest() {
        this(new NearestElevatorStrategy());
    }

    private ElevatorSystemTest(ElevatorSelectionStrategy strategy) {
        system = new ElevatorSystem(strategy, log::add);
        controller = system.getElevatorController();
        floorPanel = system.getFloorPanelController();
        elevatorPanel = system.getElevatorPanelController();
    }

    public static void main(String[] args) {
        int failed = 0;
        // scheduling
        failed += run("car calls are served in LOOK order", ElevatorSystemTest::carCallsServedInSweepOrder);
        failed += run("down hall call is skipped on the way up", ElevatorSystemTest::hallCallServedOnlyInItsDirection);
        failed += run("a press for the current floor while the doors are open holds them open", ElevatorSystemTest::pressWhileDoorsOpenHoldsDoors);
        // dispatching
        failed += run("nearest idle elevator is chosen", ElevatorSystemTest::nearestElevatorIsChosen);
        failed += run("selection prefers elevators already heading to the caller", ElevatorSystemTest::elevatorOnTheWayIsPreferred);
        failed += run("ETA turns at the last stop (LOOK), not the end of the building (SCAN)", ElevatorSystemTest::etaUsesLookTurnaround);
        failed += run("ETA charges for the stops on the way", ElevatorSystemTest::etaChargesIntermediateStops);
        failed += run("round robin hands calls out in turn", ElevatorSystemTest::roundRobinTakesTurns);
        failed += run("least busy prefers the elevator with fewer stops", ElevatorSystemTest::leastBusyPrefersFewerStops);
        failed += run("zone based sends calls to the zone's elevator", ElevatorSystemTest::zoneBasedUsesZoneOwner);
        failed += run("strategy can be switched at runtime", ElevatorSystemTest::strategySwitchAtRuntime);
        failed += run("factory builds every strategy type", ElevatorSystemTest::factoryBuildsEveryType);
        // requests and validation
        failed += run("pressing a lit button does not duplicate the request", ElevatorSystemTest::duplicatePressIsIgnored);
        failed += run("invalid requests are rejected", ElevatorSystemTest::invalidRequestsRejected);
        failed += run("request lifecycle rejects illegal transitions", ElevatorSystemTest::requestLifecycleIsEnforced);
        failed += run("elevator and system state machines reject illegal transitions", ElevatorSystemTest::stateMachinesAreEnforced);
        failed += run("snapshots do not change after they are returned", ElevatorSystemTest::snapshotsAreImmutable);
        // availability
        failed += run("maintenance hands hall calls to another elevator", ElevatorSystemTest::maintenanceRequeuesHallCalls);
        failed += run("a full car hands its hall calls to another car", ElevatorSystemTest::fullCarBypassesHallCalls);
        failed += run("an overloaded car keeps its doors open", ElevatorSystemTest::overloadedCarHoldsDoors);
        // building lifecycle
        failed += run("graceful stop finishes outstanding requests", ElevatorSystemTest::gracefulStop);
        failed += run("emergency recall cancels requests and parks every car at the recall floor", ElevatorSystemTest::emergencyRecall);
        failed += run("a car leaving maintenance during an emergency joins the recall", ElevatorSystemTest::maintenanceEndDuringEmergency);
        // observability
        failed += run("wait-time metrics are recorded per building", ElevatorSystemTest::waitTimeMetrics);
        failed += run("a failing listener does not stop the clock", ElevatorSystemTest::failingListenerIsIsolated);
        // concurrency
        failed += run("simultaneous presses of one button create one request", ElevatorSystemTest::concurrentDuplicatePresses);
        failed += run("presses from many threads while the clock runs are all served", ElevatorSystemTest::concurrentPressesWhileTicking);
        failed += run("a busy building does not block another building", ElevatorSystemTest::buildingsDoNotBlockEachOther);
        failed += run("the real-time clock serves calls on its own thread", ElevatorSystemTest::realTimeClockServesCalls);

        System.out.println(failed == 0 ? "\nAll tests passed" : "\n" + failed + " test(s) failed");
        if (failed > 0) {
            System.exit(1);
        }
    }

    // --- scheduling ---

    private static void carCallsServedInSweepOrder() {
        ElevatorSystemTest t = new ElevatorSystemTest();
        String b = t.building(1);
        String e1 = t.elevator(b, 0);
        t.elevatorPanel.pressFloorButton(e1, 7);
        t.elevatorPanel.pressFloorButton(e1, 3);
        t.elevatorPanel.pressFloorButton(e1, 5);
        t.controller.runUntilIdle(b, 100);
        assertEquals(List.of("E1@3", "E1@5", "E1@7"), t.stops());
    }

    private static void hallCallServedOnlyInItsDirection() {
        ElevatorSystemTest t = new ElevatorSystemTest();
        String b = t.building(1);
        t.elevatorPanel.pressFloorButton(t.elevator(b, 0), 8);
        t.controller.tick(b);
        HallCallView down = t.floorPanel.pressButton(b, 4, Direction.DOWN);
        t.controller.runUntilIdle(b, 100);
        assertEquals(List.of("E1@8", "E1@4"), t.stops());
        assertEquals(RequestStatus.COMPLETED, t.status(down));
    }

    private static void pressWhileDoorsOpenHoldsDoors() {
        ElevatorSystemTest t = new ElevatorSystemTest();
        String b = t.building(1);
        String e1 = t.elevator(b, 0);
        t.elevatorPanel.pressFloorButton(e1, 2);
        t.runUntil(b, () -> t.controller.getElevator(e1).state() == ElevatorState.DOORS_OPEN);
        HallCallView call = t.floorPanel.pressButton(b, 2, Direction.UP);
        t.controller.tick(b);
        assertEquals(RequestStatus.COMPLETED, t.status(call));
        assertEquals(ElevatorState.DOORS_OPEN, t.controller.getElevator(e1).state());
        assertEquals(List.of("E1@2"), t.stops()); // served without closing and reopening the doors
    }

    // --- dispatching ---

    private static void nearestElevatorIsChosen() {
        ElevatorSystemTest t = new ElevatorSystemTest();
        String b = t.building(2);
        String e2 = t.elevator(b, 1);
        t.elevatorPanel.pressFloorButton(e2, 8);
        t.controller.runUntilIdle(b, 100);
        assertEquals(e2, t.floorPanel.pressButton(b, 6, Direction.DOWN).assignedElevatorId());
    }

    private static void elevatorOnTheWayIsPreferred() {
        ElevatorSystemTest t = new ElevatorSystemTest();
        String b = t.building(2);
        String e1 = t.elevator(b, 0);
        String e2 = t.elevator(b, 1);
        // E2 parks at floor 6; E1 starts heading up to 10 from the ground floor.
        t.elevatorPanel.pressFloorButton(e2, 6);
        t.controller.runUntilIdle(b, 100);
        t.elevatorPanel.pressFloorButton(e1, 10);
        t.ticks(b, 3);
        // E1 is at floor 3 going up: an UP call at floor 4 is on its way.
        assertEquals(e1, t.floorPanel.pressButton(b, 4, Direction.UP).assignedElevatorId());
        // A DOWN call at floor 2 is behind E1, so idle E2 must take it even though E1 is closer.
        assertEquals(e2, t.floorPanel.pressButton(b, 2, Direction.DOWN).assignedElevatorId());
    }

    private static void etaUsesLookTurnaround() {
        ElevatorSystemTest t = new ElevatorSystemTest();
        String b = t.building("Tall", 0, 20, 2);
        String e1 = t.elevator(b, 0);
        String e2 = t.elevator(b, 1);
        t.elevatorPanel.pressFloorButton(e2, 12);
        t.controller.runUntilIdle(b, 100);
        t.elevatorPanel.pressFloorButton(e1, 4);
        t.ticks(b, 2); // E1 at floor 2, going up to 4
        // LOOK: E1 turns at 4 and is back at 1 in (4-2)+(4-1)+2 = 7 ticks; idle E2 needs 11.
        // A SCAN estimate would send E1 to floor 20 and back (37 ticks) and wrongly pick E2.
        assertEquals(e1, t.floorPanel.pressButton(b, 1, Direction.DOWN).assignedElevatorId());
    }

    private static void etaChargesIntermediateStops() {
        ElevatorSystemTest t = new ElevatorSystemTest();
        String b = t.building(2);
        String e1 = t.elevator(b, 0);
        String e2 = t.elevator(b, 1);
        t.elevatorPanel.pressFloorButton(e1, 2);
        t.elevatorPanel.pressFloorButton(e1, 3);
        t.elevatorPanel.pressFloorButton(e1, 4);
        t.controller.tick(b); // E1 at floor 1 going up; E2 idle at 0
        // By distance alone E1 wins (5 floors vs 6), but it stops 3 times on the way: 5 + 3*2 = 11 ticks.
        assertEquals(e2, t.floorPanel.pressButton(b, 6, Direction.UP).assignedElevatorId());
    }

    private static void roundRobinTakesTurns() {
        ElevatorSystemTest t = new ElevatorSystemTest(new RoundRobinStrategy());
        String b = t.building(3);
        // every elevator is parked at floor 0, yet the calls are spread E1, E2, E3, E1
        assertEquals(t.elevator(b, 0), t.floorPanel.pressButton(b, 1, Direction.UP).assignedElevatorId());
        assertEquals(t.elevator(b, 1), t.floorPanel.pressButton(b, 2, Direction.UP).assignedElevatorId());
        assertEquals(t.elevator(b, 2), t.floorPanel.pressButton(b, 3, Direction.UP).assignedElevatorId());
        assertEquals(t.elevator(b, 0), t.floorPanel.pressButton(b, 4, Direction.UP).assignedElevatorId());
    }

    private static void leastBusyPrefersFewerStops() {
        ElevatorSystemTest t = new ElevatorSystemTest(new LeastBusyStrategy());
        String b = t.building(2);
        String e1 = t.elevator(b, 0);
        String e2 = t.elevator(b, 1);
        t.elevatorPanel.pressFloorButton(e2, 10);
        t.controller.runUntilIdle(b, 100);
        // E1 is right next to floor 1 but already has two stops; idle E2 at floor 10 takes the call
        t.elevatorPanel.pressFloorButton(e1, 8);
        t.elevatorPanel.pressFloorButton(e1, 9);
        assertEquals(e2, t.floorPanel.pressButton(b, 1, Direction.UP).assignedElevatorId());
    }

    private static void zoneBasedUsesZoneOwner() {
        ElevatorSystemTest t = new ElevatorSystemTest(new ZoneBasedStrategy());
        String b = t.building(3);
        // floors 0-10 with 3 elevators: E1 owns 0-3, E2 owns 4-7, E3 owns 8-10
        assertEquals(t.elevator(b, 2), t.floorPanel.pressButton(b, 9, Direction.DOWN).assignedElevatorId());
        assertEquals(t.elevator(b, 1), t.floorPanel.pressButton(b, 5, Direction.UP).assignedElevatorId());
        assertEquals(t.elevator(b, 0), t.floorPanel.pressButton(b, 2, Direction.UP).assignedElevatorId());

        // with E3 out of service, its zone falls back to another elevator
        t.controller.startMaintenance(t.elevator(b, 2));
        HallCallView call = t.floorPanel.pressButton(b, 10, Direction.DOWN);
        assertTrue(call.assignedElevatorId() != null, "zone call should still be assigned");
        assertTrue(!call.assignedElevatorId().equals(t.elevator(b, 2)), "E3 is in maintenance");
        t.controller.runUntilIdle(b, 100);
        assertEquals(RequestStatus.COMPLETED, t.status(call));
    }

    private static void strategySwitchAtRuntime() {
        ElevatorSystemTest t = new ElevatorSystemTest();
        String b = t.building(3);
        // nearest: all idle at floor 0, tie broken by name -> E1
        assertEquals(t.elevator(b, 0), t.floorPanel.pressButton(b, 9, Direction.DOWN).assignedElevatorId());
        t.controller.setSelectionStrategy(StrategyType.ZONE_BASED);
        assertEquals(t.elevator(b, 2), t.floorPanel.pressButton(b, 8, Direction.DOWN).assignedElevatorId());
    }

    private static void factoryBuildsEveryType() {
        assertEquals(NearestElevatorStrategy.class, SelectionStrategyFactory.create(StrategyType.NEAREST).getClass());
        assertEquals(RoundRobinStrategy.class, SelectionStrategyFactory.create(StrategyType.ROUND_ROBIN).getClass());
        assertEquals(LeastBusyStrategy.class, SelectionStrategyFactory.create(StrategyType.LEAST_BUSY).getClass());
        assertEquals(ZoneBasedStrategy.class, SelectionStrategyFactory.create(StrategyType.ZONE_BASED).getClass());
        // a value read from config or the command line
        assertEquals(ZoneBasedStrategy.class, SelectionStrategyFactory.create(" zone_based ").getClass());
        assertThrows(() -> SelectionStrategyFactory.create("fastest"));
        // every enum constant must be wired up, so a new type cannot be forgotten in the factory
        for (StrategyType type : StrategyType.values()) {
            assertTrue(SelectionStrategyFactory.create(type) != null, type + " has no strategy");
        }
    }

    // --- requests and validation ---

    private static void duplicatePressIsIgnored() {
        ElevatorSystemTest t = new ElevatorSystemTest();
        String b = t.building(2);
        HallCallView first = t.floorPanel.pressButton(b, 4, Direction.UP);
        HallCallView second = t.floorPanel.pressButton(b, 4, Direction.UP);
        assertEquals(first.id(), second.id());
        // once served, the button can be pressed again and makes a new request
        t.controller.runUntilIdle(b, 100);
        assertTrue(!first.id().equals(t.floorPanel.pressButton(b, 4, Direction.UP).id()), "served call must not be reused");
    }

    private static void invalidRequestsRejected() {
        ElevatorSystemTest t = new ElevatorSystemTest();
        String b = t.building(1);
        assertThrows(() -> t.floorPanel.pressButton(b, 11, Direction.UP));
        assertThrows(() -> t.floorPanel.pressButton(b, 10, Direction.UP));
        assertThrows(() -> t.floorPanel.pressButton(b, 0, Direction.DOWN));
        assertThrows(() -> t.floorPanel.pressButton(b, 3, Direction.IDLE));
        assertThrows(() -> t.floorPanel.pressButton("no-such-building", 3, Direction.UP));
        assertThrows(() -> t.elevatorPanel.pressFloorButton(t.elevator(b, 0), -1));
        assertThrows(() -> t.elevatorPanel.reportLoad(t.elevator(b, 0), -1));
        assertThrows(() -> new Building("bad", 5, 5, 5, 1));
        assertThrows(() -> new Building("bad", 0, 5, 9, 1));
        assertThrows(() -> new Building("bad", 0, 5, 0, 0));
    }

    private static void requestLifecycleIsEnforced() {
        ExternalRequest call = new ExternalRequest("b", 3, Direction.UP, 0, 1);
        assertThrows(() -> call.complete(1));       // QUEUED -> COMPLETED skips assignment
        assertThrows(call::requeue);                // only ASSIGNED calls can be requeued
        call.assignTo("e1");
        assertThrows(() -> call.assignTo("e2"));    // already assigned
        call.complete(4);
        assertEquals(4L, call.waitTicks());
        assertThrows(call::cancel);                 // finished calls stay finished
    }

    private static void stateMachinesAreEnforced() {
        Elevator car = new Elevator("E1", "b", 0, 8);
        assertThrows(() -> car.transitionTo(ElevatorState.DOORS_CLOSING)); // doors were never opened
        car.transitionTo(ElevatorState.DOORS_OPEN);
        assertThrows(() -> car.moveOneFloor(Direction.UP));                // never move with the doors open
        assertThrows(() -> car.addHallCall(3, Direction.IDLE));

        ElevatorSystemTest t = new ElevatorSystemTest();
        String b = t.building(1);
        assertThrows(() -> t.controller.clearEmergency(b));                // not in an emergency
        t.controller.stop(b);
        t.controller.runUntilIdle(b, 10);
        assertEquals(SystemState.STOPPED, t.controller.getBuilding(b).systemState());
        assertThrows(() -> t.controller.emergencyRecall(b));               // nothing to recall when STOPPED
        t.controller.start(b);
        assertEquals(SystemState.RUNNING, t.controller.getBuilding(b).systemState());
    }

    private static void snapshotsAreImmutable() {
        ElevatorSystemTest t = new ElevatorSystemTest();
        String b = t.building(1);
        String e1 = t.elevator(b, 0);
        CarCallView carCall = t.elevatorPanel.pressFloorButton(e1, 3);
        ElevatorView before = t.controller.getElevator(e1);
        t.controller.runUntilIdle(b, 100);
        assertEquals(0, before.currentFloor());
        assertEquals(List.of(3), before.carCalls());
        assertThrows(() -> before.carCalls().add(5), UnsupportedOperationException.class);
        assertEquals(RequestStatus.PENDING, carCall.status());
        assertEquals(RequestStatus.COMPLETED, t.controller.getCarCall(carCall.id()).status());
        assertEquals(3, t.controller.getElevator(e1).currentFloor());
    }

    // --- availability ---

    private static void maintenanceRequeuesHallCalls() {
        ElevatorSystemTest t = new ElevatorSystemTest();
        String b = t.building(2);
        HallCallView call = t.floorPanel.pressButton(b, 5, Direction.UP);
        String original = call.assignedElevatorId();
        CarCallView carCall = t.elevatorPanel.pressFloorButton(original, 9);

        t.controller.startMaintenance(original);
        HallCallView moved = t.controller.getHallCall(call.id());
        assertTrue(!original.equals(moved.assignedElevatorId()), "call should move to the other elevator");
        assertEquals(RequestStatus.ASSIGNED, moved.status());
        assertEquals(RequestStatus.CANCELLED, t.controller.getCarCall(carCall.id()).status());
        t.controller.runUntilIdle(b, 100);
        assertEquals(RequestStatus.COMPLETED, t.status(call));

        ElevatorView broken = t.controller.getElevator(original);
        assertEquals(ElevatorState.MAINTENANCE, broken.state());
        assertEquals(0, broken.currentFloor());
        assertThrows(() -> t.elevatorPanel.pressFloorButton(original, 3));
        t.controller.endMaintenance(original);
        assertEquals(ElevatorState.IDLE, t.controller.getElevator(original).state());
    }

    private static void fullCarBypassesHallCalls() {
        ElevatorSystemTest t = new ElevatorSystemTest();
        String b = t.building(2);
        String e1 = t.elevator(b, 0);
        String e2 = t.elevator(b, 1);
        t.elevatorPanel.pressFloorButton(e2, 10);
        t.controller.runUntilIdle(b, 100);             // E2 parked at the top
        t.elevatorPanel.pressFloorButton(e1, 1);
        t.elevatorPanel.pressFloorButton(e1, 10);
        t.runUntil(b, () -> t.controller.getElevator(e1).state() == ElevatorState.DOORS_OPEN); // E1 loading at 1
        HallCallView pickup = t.floorPanel.pressButton(b, 4, Direction.UP);
        assertEquals(e1, pickup.assignedElevatorId()); // on E1's way up
        t.elevatorPanel.reportLoad(e1, 8);             // car is now full

        assertEquals(e2, t.controller.getHallCall(pickup.id()).assignedElevatorId());
        assertTrue(t.log.stream().anyMatch(l -> l.contains("E1: full, handed back 1 hall call")), "release logged");
        assertEquals(e2, t.floorPanel.pressButton(b, 3, Direction.UP).assignedElevatorId()); // full cars get no new calls
        t.controller.runUntilIdle(b, 100);
        assertEquals(RequestStatus.COMPLETED, t.status(pickup));
        assertEquals(10, t.controller.getElevator(e1).currentFloor()); // its passengers still reach their floor
    }

    private static void overloadedCarHoldsDoors() {
        ElevatorSystemTest t = new ElevatorSystemTest();
        String b = t.building(1);
        String e1 = t.elevator(b, 0);
        t.elevatorPanel.pressFloorButton(e1, 0);
        t.controller.tick(b);
        assertEquals(ElevatorState.DOORS_OPEN, t.controller.getElevator(e1).state());
        t.elevatorPanel.reportLoad(e1, 9);
        t.elevatorPanel.pressFloorButton(e1, 5);
        t.ticks(b, 5);
        assertEquals(ElevatorState.DOORS_OPEN, t.controller.getElevator(e1).state());
        assertEquals(0, t.controller.getElevator(e1).currentFloor());
        assertTrue(t.log.stream().anyMatch(l -> l.contains("doors held open at floor 0 (overloaded 9/8)")), "hold logged");
        t.elevatorPanel.reportLoad(e1, 4);
        t.controller.runUntilIdle(b, 100);
        assertEquals(5, t.controller.getElevator(e1).currentFloor());
    }

    // --- building lifecycle ---

    private static void gracefulStop() {
        ElevatorSystemTest t = new ElevatorSystemTest();
        String b = t.building(1);
        HallCallView call = t.floorPanel.pressButton(b, 6, Direction.DOWN);
        t.controller.stop(b);
        assertEquals(SystemState.STOPPING, t.controller.getBuilding(b).systemState());
        assertThrows(() -> t.floorPanel.pressButton(b, 2, Direction.UP));
        t.controller.runUntilIdle(b, 100);
        assertEquals(RequestStatus.COMPLETED, t.status(call));
        assertEquals(SystemState.STOPPED, t.controller.getBuilding(b).systemState());
        assertTrue(t.log.stream().anyMatch(l -> l.contains("system stopped")), "system should report STOPPED");
    }

    private static void emergencyRecall() {
        ElevatorSystemTest t = new ElevatorSystemTest();
        String b = t.building("Recall Tower", 0, 10, 2, 2);
        String e1 = t.elevator(b, 0);
        String e2 = t.elevator(b, 1);
        t.elevatorPanel.pressFloorButton(e1, 9);
        t.elevatorPanel.pressFloorButton(e2, 1);
        t.ticks(b, 5); // E1 is on its way up, E2 has stopped at 1
        HallCallView waiting = t.floorPanel.pressButton(b, 8, Direction.DOWN);
        CarCallView riding = t.elevatorPanel.pressFloorButton(e2, 7);

        t.controller.emergencyRecall(b);
        assertEquals(RequestStatus.CANCELLED, t.status(waiting));
        assertEquals(RequestStatus.CANCELLED, t.controller.getCarCall(riding.id()).status());
        assertThrows(() -> t.floorPanel.pressButton(b, 3, Direction.UP));
        assertThrows(() -> t.elevatorPanel.pressFloorButton(e1, 4));

        t.controller.runUntilIdle(b, 100);
        for (ElevatorView car : t.controller.getElevators(b)) {
            assertEquals(2, car.currentFloor());
            assertEquals(ElevatorState.IDLE, car.state());
        }
        int e1StopsAbove2 = (int) t.stops().stream().filter(s -> s.startsWith("E1@") && !s.equals("E1@2")).count();
        assertEquals(0, e1StopsAbove2); // non-stop: E1 never reached its old destination

        t.controller.clearEmergency(b);
        HallCallView after = t.floorPanel.pressButton(b, 5, Direction.UP);
        t.controller.runUntilIdle(b, 100);
        assertEquals(RequestStatus.COMPLETED, t.status(after));
    }

    private static void maintenanceEndDuringEmergency() {
        ElevatorSystemTest t = new ElevatorSystemTest();
        String b = t.building("Recall Tower", 0, 10, 5, 2);
        String e2 = t.elevator(b, 1);
        t.controller.startMaintenance(e2);
        t.controller.emergencyRecall(b);
        t.controller.runUntilIdle(b, 100);
        assertEquals(0, t.controller.getElevator(e2).currentFloor()); // ignored while in maintenance
        t.controller.endMaintenance(e2);
        t.controller.runUntilIdle(b, 100);
        assertEquals(5, t.controller.getElevator(e2).currentFloor());
    }

    // --- observability ---

    private static void waitTimeMetrics() {
        ElevatorSystemTest t = new ElevatorSystemTest();
        String b = t.building(1);
        String other = t.building(1);
        t.floorPanel.pressButton(b, 3, Direction.UP);  // E1 at 0: 3 moves, doors open on the 4th tick
        t.controller.runUntilIdle(b, 100);
        t.floorPanel.pressButton(b, 1, Direction.UP);  // E1 at 3: 2 moves, doors open on the 3rd tick
        t.controller.runUntilIdle(b, 100);
        WaitTimeMetrics.Stats stats = t.controller.getWaitTimeStats(b);
        assertEquals(2L, stats.served());
        assertEquals(4L, stats.maxWaitTicks());
        assertEquals(3.5, stats.averageWaitTicks());
        assertEquals(0L, t.controller.getWaitTimeStats(other).served());
    }

    private static void failingListenerIsIsolated() {
        ElevatorSystemTest t = new ElevatorSystemTest();
        t.system.subscribe(event -> {
            throw new IllegalStateException("broken display");
        });
        String b = t.building(1);
        HallCallView call = t.floorPanel.pressButton(b, 4, Direction.UP);
        t.controller.runUntilIdle(b, 100);
        assertEquals(RequestStatus.COMPLETED, t.status(call));
    }

    // --- concurrency ---

    private static void concurrentDuplicatePresses() throws Exception {
        ElevatorSystemTest t = new ElevatorSystemTest();
        String b = t.building(3);
        Set<String> ids = ConcurrentHashMap.newKeySet();
        runConcurrently(16, i -> ids.add(t.floorPanel.pressButton(b, 6, Direction.UP).id()));
        assertEquals(1, ids.size());
    }

    private static void concurrentPressesWhileTicking() throws Exception {
        ElevatorSystemTest t = new ElevatorSystemTest();
        String b = t.building(3);
        Queue<HallCallView> hallCalls = new ConcurrentLinkedQueue<>();
        AtomicBoolean pressing = new AtomicBoolean(true);

        Thread clock = new Thread(() -> {
            while (pressing.get()) {
                t.controller.tick(b);
            }
        });
        clock.start();
        try {
            runConcurrently(8, thread -> {
                Random random = new Random(thread);
                for (int i = 0; i < 50; i++) {
                    int floor = random.nextInt(11);
                    Direction direction = floor == 10 || (floor > 0 && random.nextBoolean()) ? Direction.DOWN : Direction.UP;
                    hallCalls.add(t.floorPanel.pressButton(b, floor, direction));
                }
            });
        } finally {
            pressing.set(false);
            clock.join();
        }

        t.controller.runUntilIdle(b, 1000);
        assertEquals(400, hallCalls.size());
        assertTrue(hallCalls.stream().allMatch(r -> t.status(r) == RequestStatus.COMPLETED),
                "every hall call should be completed");
    }

    private static void buildingsDoNotBlockEachOther() throws Exception {
        ElevatorSystemTest t = new ElevatorSystemTest();
        String slow = t.building(1);
        String fast = t.building(1);
        CountDownLatch slowTickStarted = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        // a listener that stalls inside one of the slow building's ticks, while that building's lock is held
        t.system.subscribe(event -> {
            if (event.buildingId().equals(slow) && event instanceof ElevatorEvent.Moved) {
                slowTickStarted.countDown();
                try {
                    release.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        });
        t.floorPanel.pressButton(slow, 5, Direction.UP);
        Thread stalled = new Thread(() -> t.controller.tick(slow));
        stalled.start();
        try {
            assertTrue(slowTickStarted.await(5, TimeUnit.SECONDS), "slow building should be mid-tick");
            ExecutorService other = Executors.newSingleThreadExecutor();
            try {
                Future<HallCallView> served = other.submit(() -> {
                    HallCallView call = t.floorPanel.pressButton(fast, 3, Direction.UP);
                    t.controller.runUntilIdle(fast, 100);
                    return t.controller.getHallCall(call.id());
                });
                assertEquals(RequestStatus.COMPLETED, served.get(5, TimeUnit.SECONDS).status());
            } finally {
                other.shutdownNow();
            }
        } finally {
            release.countDown();
            stalled.join();
        }
    }

    private static void realTimeClockServesCalls() throws Exception {
        ElevatorSystemTest t = new ElevatorSystemTest();
        String b = t.building(2);
        RealTimeClock clock = RealTimeClock.start(t.controller, b, Duration.ofMillis(2));
        try {
            HallCallView call = t.floorPanel.pressButton(b, 7, Direction.DOWN);
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (t.status(call) != RequestStatus.COMPLETED && System.nanoTime() < deadline) {
                Thread.sleep(5);
            }
            assertEquals(RequestStatus.COMPLETED, t.status(call));
        } finally {
            clock.close();
        }
    }

    // --- helpers ---

    private interface ThreadTask {
        void run(int threadIndex) throws Exception;
    }

    /** Starts {@code threads} threads at the same instant and rethrows the first failure. */
    private static void runConcurrently(int threads, ThreadTask task) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                int index = i;
                futures.add(pool.submit(() -> {
                    start.await();
                    task.run(index);
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> future : futures) {
                future.get(30, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }
    }

    private String building(int elevators) {
        return building("Test Tower", 0, 10, 0, elevators);
    }

    private String building(String name, int minFloor, int maxFloor, int elevators) {
        return building(name, minFloor, maxFloor, minFloor, elevators);
    }

    private String building(String name, int minFloor, int maxFloor, int recallFloor, int elevators) {
        String id = controller.createBuilding(name, minFloor, maxFloor, recallFloor, elevators, 8).id();
        controller.start(id);
        return id;
    }

    private String elevator(String buildingId, int index) {
        return controller.getElevators(buildingId).get(index).elevatorId();
    }

    private RequestStatus status(HallCallView call) {
        return controller.getHallCall(call.id()).status();
    }

    private void runUntil(String buildingId, BooleanSupplier condition) {
        for (int i = 0; i < 100 && !condition.getAsBoolean(); i++) {
            controller.tick(buildingId);
        }
        assertTrue(condition.getAsBoolean(), "condition not reached within 100 ticks");
    }

    private void ticks(String buildingId, int count) {
        for (int i = 0; i < count; i++) {
            controller.tick(buildingId);
        }
    }

    private List<String> stops() {
        List<String> stops = new ArrayList<>();
        synchronized (log) {
            for (String line : log) {
                Matcher m = STOP.matcher(line);
                if (m.find()) {
                    stops.add(m.group(1) + "@" + m.group(2));
                }
            }
        }
        return stops;
    }

    private interface TestCase {
        void run() throws Exception;
    }

    private static int run(String name, TestCase test) {
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

    private static void assertThrows(Runnable action, Class<? extends Throwable> type) {
        try {
            action.run();
        } catch (Throwable e) {
            if (type.isInstance(e)) {
                return;
            }
            throw new AssertionError("expected " + type.getSimpleName() + " but got " + e);
        }
        throw new AssertionError("expected " + type.getSimpleName());
    }
}
