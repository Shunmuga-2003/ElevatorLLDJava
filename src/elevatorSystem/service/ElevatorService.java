package elevatorSystem.service;

import elevatorSystem.Entity.Direction;
import elevatorSystem.Entity.Elevator;
import elevatorSystem.Entity.ElevatorState;
import elevatorSystem.Repository.ElevatorRepository;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class ElevatorService {
    private final ElevatorRepository elevatorRepository;
    private final RequestService requestService;
    private final ElevatorSchedulerService schedulerService;

    public ElevatorService(ElevatorRepository elevatorRepository,
                           RequestService requestService,
                           ElevatorSchedulerService schedulerService) {
        this.elevatorRepository = elevatorRepository;
        this.requestService = requestService;
        this.schedulerService = schedulerService;
    }

    public Elevator createElevator(String name, String buildingId, int startFloor, int capacity) {
        Elevator elevator = new Elevator(name, buildingId, startFloor, capacity);
        elevatorRepository.save(elevator);
        return elevator;
    }

    public Elevator getElevator(String elevatorId) {
        return elevatorRepository.findElevatorById(elevatorId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown elevator " + elevatorId));
    }

    public List<Elevator> findByBuilding(String buildingId) {
        return elevatorRepository.findByBuilding(buildingId).stream()
                .sorted(Comparator.comparing(Elevator::getName))
                .collect(Collectors.toList());
    }

    public void updateElevatorState(String elevatorId, ElevatorState elevatorState){
        elevatorRepository.findElevatorById(elevatorId).ifPresent(elevator -> {
            elevator.setElevatorState(elevatorState);
            elevatorRepository.save(elevator);
        });
    }

    public void updateElevatorFloor(String elevatorId,int floor){
        elevatorRepository.findElevatorById(elevatorId).ifPresent(elevator -> {
            elevator.setCurrentFloor(floor);
            elevatorRepository.save(elevator);
        });
    }

    /** Takes an elevator out of service. Its hall calls are re-queued so another elevator picks them up. */
    public void startMaintenance(String elevatorId) {
        Elevator elevator = getElevator(elevatorId);
        requestService.releaseRequests(elevator);
        schedulerService.clearStops(elevator);
        elevator.setActive(false);
        elevator.setDirection(Direction.IDLE);
        elevator.setElevatorState(ElevatorState.MAINTENANCE);
        elevatorRepository.save(elevator);
    }

    public void endMaintenance(String elevatorId) {
        Elevator elevator = getElevator(elevatorId);
        elevator.setActive(true);
        elevator.setElevatorState(ElevatorState.STOPPED);
        elevatorRepository.save(elevator);
    }
}
