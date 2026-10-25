package elevatorSystem.entity;

import java.util.EnumSet;
import java.util.Set;

/** Lifecycle of a building's elevator group. Illegal transitions are rejected by {@link Building#transitionTo}. */
public enum SystemState {
    STOPPED,    // not accepting requests, every car parked
    RUNNING,    // normal service
    STOPPING,   // graceful shutdown: no new requests, outstanding ones are finished
    EMERGENCY;  // fire recall: every request cancelled, every car sent to the recall floor

    public boolean canTransitionTo(SystemState next) {
        return next == this || allowedNext().contains(next);
    }

    public boolean acceptsRequests() {
        return this == RUNNING;
    }

    private Set<SystemState> allowedNext() {
        switch (this) {
            case STOPPED:
                return EnumSet.of(RUNNING);
            case RUNNING:
                return EnumSet.of(STOPPING, EMERGENCY);
            case STOPPING:
                return EnumSet.of(STOPPED, EMERGENCY);
            case EMERGENCY:
                return EnumSet.of(RUNNING);
            default:
                throw new AssertionError(this);
        }
    }
}
