package elevatorSystem.repository.impl;

import elevatorSystem.entity.Direction;
import elevatorSystem.entity.ExternalRequest;
import elevatorSystem.entity.RequestStatus;
import elevatorSystem.repository.ExternalRequestRepository;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * In-memory hall-call store. Only active calls are indexed by building, so lookups cost in proportion to
 * the calls in flight rather than every call ever made; finished calls stay retrievable by id.
 */
public class ExternalRequestRepositoryImpl implements ExternalRequestRepository {
    private final Map<String, ExternalRequest> byId = new ConcurrentHashMap<>();
    private final Map<String, Map<String, ExternalRequest>> activeByBuilding = new ConcurrentHashMap<>();
    private final Map<ButtonKey, String> activeByButton = new ConcurrentHashMap<>();

    private record ButtonKey(String buildingId, int floor, Direction direction) {
    }

    @Override
    public ExternalRequest save(ExternalRequest request) {
        byId.put(request.getId(), request);
        Map<String, ExternalRequest> active =
                activeByBuilding.computeIfAbsent(request.getBuildingId(), id -> new ConcurrentHashMap<>());
        ButtonKey button = new ButtonKey(request.getBuildingId(), request.getFloorNumber(), request.getDirection());
        if (request.isActive()) {
            active.put(request.getId(), request);
            activeByButton.put(button, request.getId());
        } else {
            active.remove(request.getId());
            activeByButton.remove(button, request.getId());
        }
        return request;
    }

    @Override
    public Optional<ExternalRequest> findById(String requestId) {
        return Optional.ofNullable(byId.get(requestId));
    }

    @Override
    public Optional<ExternalRequest> findActive(String buildingId, int floor, Direction direction) {
        return Optional.ofNullable(activeByButton.get(new ButtonKey(buildingId, floor, direction))).map(byId::get);
    }

    @Override
    public List<ExternalRequest> findActiveByBuilding(String buildingId) {
        return active(buildingId).collect(Collectors.toList());
    }

    @Override
    public List<ExternalRequest> findQueued(String buildingId) {
        return active(buildingId)
                .filter(r -> r.getStatus() == RequestStatus.QUEUED)
                .sorted(Comparator.comparingLong(ExternalRequest::getSequence))
                .collect(Collectors.toList());
    }

    @Override
    public List<ExternalRequest> findAssignedTo(String buildingId, String elevatorId) {
        return active(buildingId)
                .filter(r -> r.getStatus() == RequestStatus.ASSIGNED && elevatorId.equals(r.getAssignedElevatorId()))
                .collect(Collectors.toList());
    }

    private Stream<ExternalRequest> active(String buildingId) {
        return activeByBuilding.getOrDefault(buildingId, Map.of()).values().stream();
    }
}
