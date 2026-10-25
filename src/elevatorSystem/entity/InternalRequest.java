package elevatorSystem.entity;

import java.util.UUID;

/**
 * A car call: someone inside an elevator pressed a destination floor.
 * Lifecycle: {@code PENDING -> COMPLETED}, or {@code CANCELLED} if the car leaves service or is recalled.
 */
public class InternalRequest {
    private final String id;
    private final String elevatorId;
    private final String buildingId;
    private final int destinationFloor;
    private final long createdAtTick;
    private RequestStatus status = RequestStatus.PENDING;
    private long completedAtTick = -1;

    public InternalRequest(String elevatorId, String buildingId, int destinationFloor, long createdAtTick) {
        this.id = UUID.randomUUID().toString();
        this.elevatorId = elevatorId;
        this.buildingId = buildingId;
        this.destinationFloor = destinationFloor;
        this.createdAtTick = createdAtTick;
    }

    public boolean isActive() {
        return status == RequestStatus.PENDING;
    }

    public void complete(long tick) {
        requirePending("complete");
        status = RequestStatus.COMPLETED;
        completedAtTick = tick;
    }

    public void cancel() {
        requirePending("cancel");
        status = RequestStatus.CANCELLED;
    }

    private void requirePending(String action) {
        if (status != RequestStatus.PENDING) {
            throw new IllegalStateException("Cannot " + action + " a " + status + " car call");
        }
    }

    public String getId() {
        return id;
    }

    public String getElevatorId() {
        return elevatorId;
    }

    public String getBuildingId() {
        return buildingId;
    }

    public int getDestinationFloor() {
        return destinationFloor;
    }

    public long getCreatedAtTick() {
        return createdAtTick;
    }

    public RequestStatus getStatus() {
        return status;
    }

    public long getCompletedAtTick() {
        return completedAtTick;
    }
}
