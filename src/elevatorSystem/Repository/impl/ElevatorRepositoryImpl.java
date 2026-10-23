package elevatorSystem.Repository.impl;

import elevatorSystem.Entity.Elevator;
import elevatorSystem.Repository.ElevatorRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class ElevatorRepositoryImpl implements ElevatorRepository {
    private final Map<String,Elevator> elevatorMap=new ConcurrentHashMap<>();
    @Override
    public Elevator save(Elevator elevator) {
        elevatorMap.put(elevator.getElevatorId(),elevator);
        return elevator;
    }

    @Override
    public List<Elevator> findAll() {
        return new ArrayList<>(elevatorMap.values());
    }

    @Override
    public List<Elevator> findAvailableElevators(String buildingId) {
        return elevatorMap.values().stream()
                .filter(e->e.getBuildingId().equals(buildingId))
                .filter(Elevator::isAvailable)
                .collect(Collectors.toList());
    }

    @Override
    public List<Elevator> findByBuilding(String buildingId) {
        return elevatorMap.values().stream()
                .filter(e->e.getBuildingId().equals(buildingId))
                .collect(Collectors.toList());
    }

    @Override
    public Optional<Elevator> findElevatorById(String elevatorId) {
        return Optional.ofNullable(elevatorMap.get(elevatorId));
    }

    @Override
    public void deleteById(String elevatorId){
        elevatorMap.remove(elevatorId);
    }
}
