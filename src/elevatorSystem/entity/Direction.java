package elevatorSystem.entity;

public enum Direction {
    UP,
    DOWN,
    IDLE;

    public Direction opposite() {
        return this == UP ? DOWN : this == DOWN ? UP : IDLE;
    }
}
