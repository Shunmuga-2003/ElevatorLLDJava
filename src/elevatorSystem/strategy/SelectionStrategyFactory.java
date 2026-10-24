package elevatorSystem.strategy;

/**
 * Factory pattern: the only place that knows which class implements each {@link StrategyType}.
 * Callers (controllers, config, tests) ask for a type instead of constructing strategies themselves,
 * so adding a strategy means one new class, one enum constant and one line here.
 */
public final class SelectionStrategyFactory {

    private SelectionStrategyFactory() {
    }

    public static ElevatorSelectionStrategy create(StrategyType type) {
        switch (type) {
            case NEAREST:
                return new NearestElevatorStrategy();
            case ROUND_ROBIN:
                return new RoundRobinStrategy();
            case LEAST_BUSY:
                return new LeastBusyStrategy();
            case ZONE_BASED:
                return new ZoneBasedStrategy();
            default:
                throw new IllegalArgumentException("Unsupported strategy " + type);
        }
    }

    /** Parses a name such as {@code "zone_based"} (e.g. from a config file or command-line argument). */
    public static ElevatorSelectionStrategy create(String name) {
        try {
            return create(StrategyType.valueOf(name.trim().toUpperCase()));
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new IllegalArgumentException("Unknown strategy '" + name + "'; expected one of "
                    + java.util.Arrays.toString(StrategyType.values()));
        }
    }
}
