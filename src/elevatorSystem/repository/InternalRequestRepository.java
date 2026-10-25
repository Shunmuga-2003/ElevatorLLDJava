package elevatorSystem.repository;

import elevatorSystem.entity.InternalRequest;

import java.util.List;
import java.util.Optional;

/** Storage for car calls. Callers must {@link #save} after every status change. */
public interface InternalRequestRepository {
    InternalRequest save(InternalRequest request);

    Optional<InternalRequest> findById(String requestId);

    List<InternalRequest> findPendingByElevator(String buildingId, String elevatorId);

    List<InternalRequest> findPendingByBuilding(String buildingId);
}
