package elevatorSystem.entity;

public enum RequestStatus {
    QUEUED,     // hall call waiting for an elevator to be assigned
    ASSIGNED,   // hall call assigned to an elevator
    PENDING,    // car call waiting to be served
    COMPLETED,
    CANCELLED;

    public boolean isTerminal() {
        return this == COMPLETED || this == CANCELLED;
    }
}
