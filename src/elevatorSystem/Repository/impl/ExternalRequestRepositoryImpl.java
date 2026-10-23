package elevatorSystem.Repository.impl;

import elevatorSystem.Entity.ExternalRequest;
import elevatorSystem.Entity.RequestStatus;
import elevatorSystem.Repository.ExternalRequestRepository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class ExternalRequestRepositoryImpl implements ExternalRequestRepository {
    private final Map<String,ExternalRequest> externalRequestMap=new ConcurrentHashMap<>();
    @Override
    public ExternalRequest save(ExternalRequest request) {
        externalRequestMap.put(request.getId(),request);
        return request;
    }
    @Override
    public List<ExternalRequest> findByBuilding(String buildingId) {
        return externalRequestMap.values().stream().filter(e->e.getBuildingId().equals(buildingId))
                .collect(Collectors.toList());
    }
    @Override
    public List<ExternalRequest> findQueuedRequests(String buildingId) {
        // oldest first, so requests are dispatched in the order they were made
        return externalRequestMap.values().stream().filter(e->e.getBuildingId().equals(buildingId))
                .filter(e->e.getRequestStatus().equals(RequestStatus.QUEUED))
                .sorted(Comparator.comparingLong(ExternalRequest::getTimestamp))
                .collect(Collectors.toList());
    }
    @Override
    public List<ExternalRequest> findAssignedByElevator(String elevatorId) {
        return externalRequestMap.values().stream().filter(e->Objects.equals(e.getAssignedElevatorId(),elevatorId))
                .filter(e->e.getRequestStatus().equals(RequestStatus.ASSIGNED)).collect(Collectors.toList());
    }
    @Override
    public void updateRequestStatus(String requestId, RequestStatus status){
        ExternalRequest request=externalRequestMap.get(requestId);
        if(request!=null){
            request.setRequestStatus(status);
        }
    }
    @Override
    public List<ExternalRequest> findAll() {
        return new ArrayList<>(externalRequestMap.values());
    }
    @Override
    public Optional<ExternalRequest> findExternalRequestById(String externalRequestId) {
        return Optional.ofNullable(externalRequestMap.get(externalRequestId));
    }
}
