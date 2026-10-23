package elevatorSystem.Entity;

public enum RequestStatus {
    QUEUED,     // external request waiting for an elevator to be assigned
    ASSIGNED,   // external request assigned to an elevator
    PENDING,    // internal request waiting to be served
    COMPLETED,
    CANCELLED
}
