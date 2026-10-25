package elevatorSystem.dto;

import elevatorSystem.entity.Building;
import elevatorSystem.entity.SystemState;

/** Immutable snapshot of a building. */
public record BuildingView(String id, String name, int minFloor, int maxFloor, int recallFloor,
                           int elevatorCount, SystemState systemState, long currentTick) {

    public static BuildingView of(Building b) {
        return new BuildingView(b.getId(), b.getName(), b.getMinFloor(), b.getMaxFloor(), b.getRecallFloor(),
                b.getElevatorCount(), b.getSystemState(), b.getCurrentTick());
    }
}
