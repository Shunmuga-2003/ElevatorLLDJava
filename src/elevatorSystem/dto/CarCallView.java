package elevatorSystem.dto;

import elevatorSystem.entity.InternalRequest;
import elevatorSystem.entity.RequestStatus;

/** Immutable snapshot of a car call. */
public record CarCallView(String id, String elevatorId, int destinationFloor, RequestStatus status,
                          long createdAtTick, long completedAtTick) {

    public static CarCallView of(InternalRequest r) {
        return new CarCallView(r.getId(), r.getElevatorId(), r.getDestinationFloor(), r.getStatus(),
                r.getCreatedAtTick(), r.getCompletedAtTick());
    }
}
