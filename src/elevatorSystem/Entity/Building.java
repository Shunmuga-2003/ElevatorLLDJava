package elevatorSystem.Entity;

import java.util.UUID;

public class Building {
    private String id;
    private String name;
    private int minFloor;
    private int maxFloor;
    private int totalElevators;
    private SystemState systemState;

    public Building(String name, int minFloor, int maxFloor, int totalElevators) {
        if (minFloor >= maxFloor) {
            throw new IllegalArgumentException("minFloor must be lower than maxFloor");
        }
        if (totalElevators <= 0) {
            throw new IllegalArgumentException("A building needs at least one elevator");
        }
        this.id = UUID.randomUUID().toString();
        this.name = name;
        this.minFloor = minFloor;
        this.maxFloor = maxFloor;
        this.totalElevators = totalElevators;
        this.systemState = SystemState.STOPPED;
    }

    public boolean isValidFloor(int floor) {
        return floor >= minFloor && floor <= maxFloor;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getMinFloor() {
        return minFloor;
    }

    public void setMinFloor(int minFloor) {
        this.minFloor = minFloor;
    }

    public int getMaxFloor() {
        return maxFloor;
    }

    public void setMaxFloor(int maxFloor) {
        this.maxFloor = maxFloor;
    }

    public int getTotalElevators() {
        return totalElevators;
    }

    public void setTotalElevators(int totalElevators) {
        this.totalElevators = totalElevators;
    }

    public SystemState getSystemState() {
        return systemState;
    }

    public void setSystemState(SystemState systemState) {
        this.systemState = systemState;
    }
}
