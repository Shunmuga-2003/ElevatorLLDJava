package elevatorSystem.entity;

import java.util.UUID;

/**
 * A hall call: someone on a floor pressed UP or DOWN.
 * Lifecycle: {@code QUEUED -> ASSIGNED -> COMPLETED}, back to {@code QUEUED} if its elevator leaves service,
 * or {@code CANCELLED} on an emergency recall. Status only changes through the methods below.
 */
public class ExternalRequest {
    private final String id;
    private final String buildingId;
    private final int floorNumber;
    private final Direction direction;
    private final long createdAtTick;
    private final long sequence; // tie-breaker for requests created in the same tick
    private RequestStatus status = RequestStatus.QUEUED;
    private String assignedElevatorId;
    private long completedAtTick = -1;

    public ExternalRequest(String buildingId, int floorNumber, Direction direction, long createdAtTick, long sequence) {
        this.id = UUID.randomUUID().toString();
        this.buildingId = buildingId;
        this.floorNumber = floorNumber;
        this.direction = direction;
        this.createdAtTick = createdAtTick;
        this.sequence = sequence;
    }

    public boolean isActive() {
        return status == RequestStatus.QUEUED || status == RequestStatus.ASSIGNED;
    }

    public void assignTo(String elevatorId) {
        require(RequestStatus.QUEUED, "assign");
        assignedElevatorId = elevatorId;
        status = RequestStatus.ASSIGNED;
    }

    /** Its elevator left service or became full: wait for another one. */
    public void requeue() {
        require(RequestStatus.ASSIGNED, "requeue");
        assignedElevatorId = null;
        status = RequestStatus.QUEUED;
    }

    public void complete(long tick) {
        require(RequestStatus.ASSIGNED, "complete");
        status = RequestStatus.COMPLETED;
        completedAtTick = tick;
    }

    public void cancel() {
        if (!isActive()) {
            throw new IllegalStateException("Cannot cancel a " + status + " hall call");
        }
        status = RequestStatus.CANCELLED;
    }

    /** Ticks between the button press and the doors opening for the caller. */
    public long waitTicks() {
        if (status != RequestStatus.COMPLETED) {
            throw new IllegalStateException("Hall call " + id + " is not completed");
        }
        return completedAtTick - createdAtTick;
    }

    private void require(RequestStatus expected, String action) {
        if (status != expected) {
            throw new IllegalStateException("Cannot " + action + " a " + status + " hall call");
        }
    }

    public String getId() {
        return id;
    }

    public String getBuildingId() {
        return buildingId;
    }

    public int getFloorNumber() {
        return floorNumber;
    }

    public Direction getDirection() {
        return direction;
    }

    public long getCreatedAtTick() {
        return createdAtTick;
    }

    public long getSequence() {
        return sequence;
    }

    public RequestStatus getStatus() {
        return status;
    }

    public String getAssignedElevatorId() {
        return assignedElevatorId;
    }

    public long getCompletedAtTick() {
        return completedAtTick;
    }
}
