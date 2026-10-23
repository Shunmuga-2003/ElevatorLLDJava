# Elevator System — Low Level Design (Java)

A low-level design of a multi-elevator controller for a building, implemented in plain Java 17 with no external dependencies.
It models hall calls (UP/DOWN buttons on each floor) and car calls (floor buttons inside the cabin), dispatches each hall call to the best elevator, and moves every elevator with the **LOOK** scheduling algorithm on a tick-based simulation clock.

## Features

- Multiple elevators per building, with configurable floor range (e.g. basement `0` to `10`) and capacity
- **Direction-aware dispatching** — a hall call goes to the elevator that can reach the caller in the fewest floors, preferring one already travelling towards the caller in the same direction
- **LOOK scheduling** — an elevator keeps moving in its direction while it has stops ahead, then reverses. A DOWN call is not answered by an elevator passing on its way up
- Request lifecycle tracking: `QUEUED → ASSIGNED → COMPLETED` for hall calls, `PENDING → COMPLETED / CANCELLED` for car calls
- Duplicate button presses are merged into the existing request
- **Maintenance mode** — taking an elevator out of service hands its hall calls to another elevator
- **Graceful shutdown** — `RUNNING → STOPPING → STOPPED`: new requests are rejected and outstanding ones are finished first
- Input validation (non-existent floors, UP from the top floor, DOWN from the bottom floor, …)

## Architecture

The code is layered like a typical backend service: **controller → service → repository → entity**.

```
src/
├── Main.java                         demo: runs four scenarios and prints every elevator move
└── elevatorSystem/
    ├── ElevatorSystem.java           composition root (wires everything together)
    ├── Entity/                       Building, Elevator, ExternalRequest, InternalRequest + enums
    ├── Repository/                   repository interfaces
    │   └── impl/                     in-memory implementations (ConcurrentHashMap)
    ├── service/
    │   ├── BuildingService           creates buildings and their elevators, system state
    │   ├── ElevatorService           elevator lookup, state updates, maintenance
    │   ├── RequestService            validates/creates requests, completes them on arrival
    │   ├── DispatcherService         assigns hall calls to elevators (uses a strategy)
    │   ├── ElevatorSchedulerService  LOOK algorithm: where to stop, which way to go next
    │   └── MovementService           advances one elevator by one tick
    ├── strategy/
    │   ├── ElevatorSelectionStrategy
    │   └── NearestElevatorStrategy
    └── controller/
        ├── ElevatorController        start/stop, maintenance, simulation clock
        ├── FloorPanelController      hall UP/DOWN buttons
        └── ElevatorPanelController   floor buttons inside the cabin
test/
└── elevatorSystem/ElevatorSystemTest.java
```

### Class diagram

```mermaid
classDiagram
    class Building {
        id, name
        minFloor, maxFloor
        SystemState systemState
    }
    class Elevator {
        elevatorId, name, buildingId
        currentFloor
        Direction direction
        ElevatorState elevatorState
        carCalls, upHallCalls, downHallCalls
    }
    class ExternalRequest {
        floorNumber
        Direction direction
        RequestStatus status
        assignedElevatorId
    }
    class InternalRequest {
        elevatorId
        destinationFloor
        RequestStatus status
    }
    class ElevatorSelectionStrategy {
        <<interface>>
        selectElevator(candidates, request, building)
    }

    Building "1" --> "*" Elevator
    ExternalRequest --> Elevator : assigned to
    InternalRequest --> Elevator : made inside

    FloorPanelController --> RequestService
    FloorPanelController --> DispatcherService
    ElevatorPanelController --> RequestService
    ElevatorController --> MovementService
    ElevatorController --> DispatcherService
    DispatcherService --> ElevatorSelectionStrategy
    NearestElevatorStrategy ..|> ElevatorSelectionStrategy
    DispatcherService --> ElevatorSchedulerService
    MovementService --> ElevatorSchedulerService
    MovementService --> RequestService
```

### Request flow

1. Someone on floor 5 presses **UP** → `FloorPanelController` → `RequestService` validates and stores an `ExternalRequest` (`QUEUED`).
2. `DispatcherService` asks the `ElevatorSelectionStrategy` for the best elevator, marks the request `ASSIGNED` and adds floor 5 to that elevator's `upHallCalls`.
3. Each `ElevatorController.tick()`, `MovementService` asks `ElevatorSchedulerService` whether to stop at the current floor; if not, it moves one floor in the LOOK direction.
4. On reaching floor 5, the stop is cleared, the request is marked `COMPLETED` and the doors open.
5. The passenger presses **9** → `ElevatorPanelController` creates an `InternalRequest` and adds 9 to `carCalls`.

### Design patterns

| Pattern | Where |
| --- | --- |
| Strategy | `ElevatorSelectionStrategy` — swap the dispatch rule at runtime via `DispatcherService.setSelectionStrategy` |
| Repository | `Repository/*` interfaces hide storage; the in-memory impls can be replaced with a database |
| Dependency injection (manual) | every service receives its dependencies through the constructor; `ElevatorSystem` is the composition root |
| State | `ElevatorState` / `SystemState` drive what an elevator or building may do next |
| Facade | `ElevatorController` exposes a small API over the services |

### Cost function (`NearestElevatorStrategy`)

| Elevator situation | Cost (floors to reach caller) |
| --- | --- |
| Idle | `|current − caller|` |
| Moving towards the caller in the caller's direction | `|current − caller|` |
| Moving away, caller wants the opposite direction | to the end of the sweep, then back to the caller |
| Moving away, caller wants the same direction | full sweep, back to the other end, then to the caller |

Ties go to the elevator with fewer pending stops.

## Running

Requires JDK 17+.

**IntelliJ IDEA:** open the folder and run `Main` (demo) or `ElevatorSystemTest` (tests).

**Command line:**

```bash
# demo
javac -d out $(find src -name "*.java")
java -cp out Main

# tests
javac -d out $(find src test -name "*.java")
java -cp out elevatorSystem.ElevatorSystemTest
```

On Windows PowerShell, replace the `$(find …)` part with `(Get-ChildItem -Recurse -Filter *.java src, test).FullName`.

### Sample output

```
=== 1. Someone on floor 5 wants to go up to floor 9 ===
t=001 E1: moving UP 0 -> 1
...
t=005 E1: moving UP 4 -> 5
t=006 E1: stopped at floor 5, doors opening
t=007 E1: doors closing at floor 5
t=008 E1: moving UP 5 -> 6
...
t=012 E1: stopped at floor 9, doors opening
t=013 E1: doors closing at floor 9
t=014 E1: idle at floor 9
```

## Possible extensions

- Load/weight sensing per stop (the `currentLoad` / `elevatorCapacity` fields already exclude full elevators from dispatch)
- A real-time, multi-threaded clock instead of the deterministic tick loop
- Additional strategies (e.g. zoning, round-robin, energy-saving)
- Emergency stop / fire mode
