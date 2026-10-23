package elevatorSystem.Repository;

import elevatorSystem.Entity.InternalRequest;
import elevatorSystem.Entity.RequestStatus;

import java.util.List;
import java.util.Optional;

public interface InternalRequestRepository {
    InternalRequest save(InternalRequest request);
    List<InternalRequest> findByElevator(String elevatorId);
    List<InternalRequest> findPendingByElevator(String elevatorId);
    List<InternalRequest> findAll();
    Optional<InternalRequest> findInternalRequestById(String internalRequestId);
    void updateRequestStatus(String requestId, RequestStatus status);
}
