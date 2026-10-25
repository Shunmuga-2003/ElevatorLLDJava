package elevatorSystem.repository.impl;

import elevatorSystem.entity.Elevator;
import elevatorSystem.repository.ElevatorRepository;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class ElevatorRepositoryImpl implements ElevatorRepository {
    private final Map<String, Elevator> byId = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Elevator>> byBuilding = new ConcurrentHashMap<>();

    @Override
    public Elevator save(Elevator elevator) {
        byId.put(elevator.getElevatorId(), elevator);
        byBuilding.computeIfAbsent(elevator.getBuildingId(), id -> new ConcurrentHashMap<>())
                .put(elevator.getElevatorId(), elevator);
        return elevator;
    }

    @Override
    public Optional<Elevator> findById(String elevatorId) {
        return Optional.ofNullable(byId.get(elevatorId));
    }

    @Override
    public List<Elevator> findByBuilding(String buildingId) {
        return byBuilding.getOrDefault(buildingId, Map.of()).values().stream()
                .sorted(Comparator.comparing(Elevator::getName))
                .collect(Collectors.toList());
    }
}
