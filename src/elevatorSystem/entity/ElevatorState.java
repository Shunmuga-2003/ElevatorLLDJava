package elevatorSystem.entity;

import java.util.EnumSet;
import java.util.Set;

/**
 * Lifecycle of one elevator car. Each tick moves the car along exactly one edge of this graph;
 * {@link Elevator#transitionTo} rejects any edge that is not listed here, so an impossible
 * sequence (for example MOVING with the doors open) fails loudly instead of corrupting state.
 */
public enum ElevatorState {
    IDLE,           // parked, doors closed, nothing to do
    MOVING,         // travelling between floors, doors closed
    DOORS_OPEN,     // stopped at a floor with the doors open
    DOORS_CLOSING,  // stopped at a floor, doors closing
    MAINTENANCE;    // out of service

    public boolean canTransitionTo(ElevatorState next) {
        return next == this || allowedNext().contains(next);
    }

    private Set<ElevatorState> allowedNext() {
        switch (this) {
            case IDLE:
                return EnumSet.of(MOVING, DOORS_OPEN, MAINTENANCE);
            case MOVING:
                return EnumSet.of(DOORS_OPEN, IDLE, MAINTENANCE);
            case DOORS_OPEN:
                return EnumSet.of(DOORS_CLOSING, MAINTENANCE);
            case DOORS_CLOSING:
                return EnumSet.of(MOVING, IDLE, DOORS_OPEN, MAINTENANCE);
            case MAINTENANCE:
                return EnumSet.of(IDLE);
            default:
                throw new AssertionError(this);
        }
    }
}
