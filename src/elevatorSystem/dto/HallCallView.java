package elevatorSystem.dto;

import elevatorSystem.entity.Direction;
import elevatorSystem.entity.ExternalRequest;
import elevatorSystem.entity.RequestStatus;

/** Immutable snapshot of a hall call. {@code assignedElevatorId} is null while queued. */
public record HallCallView(String id, String buildingId, int floor, Direction direction, RequestStatus status,
                           String assignedElevatorId, long createdAtTick, long completedAtTick) {

    public static HallCallView of(ExternalRequest r) {
        return new HallCallView(r.getId(), r.getBuildingId(), r.getFloorNumber(), r.getDirection(), r.getStatus(),
                r.getAssignedElevatorId(), r.getCreatedAtTick(), r.getCompletedAtTick());
    }
}
