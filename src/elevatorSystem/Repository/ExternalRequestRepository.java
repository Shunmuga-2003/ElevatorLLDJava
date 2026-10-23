package elevatorSystem.Repository;

import elevatorSystem.Entity.ExternalRequest;
import elevatorSystem.Entity.RequestStatus;

import java.util.List;
import java.util.Optional;

public interface ExternalRequestRepository {
    ExternalRequest save(ExternalRequest request);
    List<ExternalRequest> findByBuilding(String buildingId);
    List<ExternalRequest> findQueuedRequests(String buildingId);
    List<ExternalRequest> findAssignedByElevator(String elevatorId);
    void updateRequestStatus(String requestId, RequestStatus status);
    List<ExternalRequest> findAll();
    Optional<ExternalRequest> findExternalRequestById(String externalRequestId);
}
