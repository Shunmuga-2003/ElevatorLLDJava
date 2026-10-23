package elevatorSystem.Entity;

import java.util.UUID;

/** A hall call: someone on a floor pressed UP or DOWN. */
public class ExternalRequest {
    private String id;
    private String buildingId;
    private int floorNumber;
    private Direction direction;
    private long timestamp;
    private RequestStatus requestStatus;
    private String assignedElevatorId;

    public ExternalRequest(String buildingId, int floorNumber, Direction direction) {
        this.id = UUID.randomUUID().toString();
        this.buildingId = buildingId;
        this.floorNumber = floorNumber;
        this.direction = direction;
        this.timestamp = System.nanoTime();
        this.requestStatus = RequestStatus.QUEUED;
    }

    public boolean isActive() {
        return requestStatus == RequestStatus.QUEUED || requestStatus == RequestStatus.ASSIGNED;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getBuildingId() {
        return buildingId;
    }

    public void setBuildingId(String buildingId) {
        this.buildingId = buildingId;
    }

    public int getFloorNumber() {
        return floorNumber;
    }

    public void setFloorNumber(int floorNumber) {
        this.floorNumber = floorNumber;
    }

    public Direction getDirection() {
        return direction;
    }

    public void setDirection(Direction direction) {
        this.direction = direction;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public RequestStatus getRequestStatus() {
        return requestStatus;
    }

    public void setRequestStatus(RequestStatus requestStatus) {
        this.requestStatus = requestStatus;
    }

    public String getAssignedElevatorId() {
        return assignedElevatorId;
    }

    public void setAssignedElevatorId(String assignedElevatorId) {
        this.assignedElevatorId = assignedElevatorId;
    }
}
