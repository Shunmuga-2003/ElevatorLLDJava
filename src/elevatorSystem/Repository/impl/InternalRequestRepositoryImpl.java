package elevatorSystem.Repository.impl;

import elevatorSystem.Entity.InternalRequest;
import elevatorSystem.Entity.RequestStatus;
import elevatorSystem.Repository.InternalRequestRepository;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class InternalRequestRepositoryImpl implements InternalRequestRepository {
    private final Map<String,InternalRequest> internalRequestMap=new ConcurrentHashMap<>();

    @Override
    public InternalRequest save(InternalRequest request) {
        internalRequestMap.put(request.getId(),request);
        return request;
    }

    @Override
    public List<InternalRequest> findByElevator(String elevatorId) {
        return internalRequestMap.values().stream().
                filter(r-> Objects.equals(r.getElevatorId(), elevatorId)).collect(Collectors.toList());
    }

    @Override
    public List<InternalRequest> findPendingByElevator(String elevatorId) {
        return internalRequestMap.values().stream().
                filter(e->e.getElevatorId().equals(elevatorId))
                .filter(e->e.getRequestStatus().equals(RequestStatus.PENDING))
                .collect(Collectors.toList());
    }

    @Override
    public List<InternalRequest> findAll() {
        return new ArrayList<>(internalRequestMap.values());
    }

    @Override
    public Optional<InternalRequest> findInternalRequestById(String internalRequestId) {
        return Optional.ofNullable(internalRequestMap.get(internalRequestId));
    }

    @Override
    public void updateRequestStatus(String requestId, RequestStatus status) {
        InternalRequest request=internalRequestMap.get(requestId);
        if(request!=null){
            request.setRequestStatus(status);
        }
    }
}
