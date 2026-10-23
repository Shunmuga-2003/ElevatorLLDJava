package elevatorSystem.Entity;

import java.util.UUID;

/** A car call: someone inside an elevator pressed a destination floor. */
public class InternalRequest {
    private String id;
    private String elevatorId;
    private String buildingId;
    private int destinationFloor;
    private long timestamp;
    private RequestStatus requestStatus;

    public InternalRequest(String elevatorId, String buildingId, int destinationFloor) {
        this.id = UUID.randomUUID().toString();
        this.elevatorId = elevatorId;
        this.buildingId = buildingId;
        this.destinationFloor = destinationFloor;
        this.timestamp = System.nanoTime();
        this.requestStatus = RequestStatus.PENDING;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getElevatorId() {
        return elevatorId;
    }

    public void setElevatorId(String elevatorId) {
        this.elevatorId = elevatorId;
    }

    public String getBuildingId() {
        return buildingId;
    }

    public void setBuildingId(String buildingId) {
        this.buildingId = buildingId;
    }

    public int getDestinationFloor() {
        return destinationFloor;
    }

    public void setDestinationFloor(int destinationFloor) {
        this.destinationFloor = destinationFloor;
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
}
