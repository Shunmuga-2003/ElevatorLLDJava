package elevatorSystem.repository.impl;

import elevatorSystem.entity.InternalRequest;
import elevatorSystem.repository.InternalRequestRepository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/** In-memory car-call store. Pending calls are indexed by building; finished ones stay retrievable by id. */
public class InternalRequestRepositoryImpl implements InternalRequestRepository {
    private final Map<String, InternalRequest> byId = new ConcurrentHashMap<>();
    private final Map<String, Map<String, InternalRequest>> pendingByBuilding = new ConcurrentHashMap<>();

    @Override
    public InternalRequest save(InternalRequest request) {
        byId.put(request.getId(), request);
        Map<String, InternalRequest> pending =
                pendingByBuilding.computeIfAbsent(request.getBuildingId(), id -> new ConcurrentHashMap<>());
        if (request.isActive()) {
            pending.put(request.getId(), request);
        } else {
            pending.remove(request.getId());
        }
        return request;
    }

    @Override
    public Optional<InternalRequest> findById(String requestId) {
        return Optional.ofNullable(byId.get(requestId));
    }

    @Override
    public List<InternalRequest> findPendingByElevator(String buildingId, String elevatorId) {
        return pendingByBuilding.getOrDefault(buildingId, Map.of()).values().stream()
                .filter(r -> r.getElevatorId().equals(elevatorId))
                .collect(Collectors.toList());
    }

    @Override
    public List<InternalRequest> findPendingByBuilding(String buildingId) {
        return List.copyOf(pendingByBuilding.getOrDefault(buildingId, Map.of()).values());
    }
}
