package elevatorSystem.repository;

import elevatorSystem.entity.Direction;
import elevatorSystem.entity.ExternalRequest;

import java.util.List;
import java.util.Optional;

/**
 * Storage for hall calls. Callers must {@link #save} after every status change, so that an implementation
 * backed by a database (or by secondary indexes, like the in-memory one) always sees the current status.
 */
public interface ExternalRequestRepository {
    ExternalRequest save(ExternalRequest request);

    Optional<ExternalRequest> findById(String requestId);

    /** The lit button at this floor and direction, if any. In SQL: a unique partial index on active calls. */
    Optional<ExternalRequest> findActive(String buildingId, int floor, Direction direction);

    List<ExternalRequest> findActiveByBuilding(String buildingId);

    /** Queued calls, oldest first, so they are dispatched in the order they were made. */
    List<ExternalRequest> findQueued(String buildingId);

    List<ExternalRequest> findAssignedTo(String buildingId, String elevatorId);
}
