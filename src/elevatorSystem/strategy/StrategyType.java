package elevatorSystem.strategy;

/** The built-in ways of choosing which elevator answers a hall call. */
public enum StrategyType {
    NEAREST,
    ROUND_ROBIN,
    LEAST_BUSY,
    ZONE_BASED
}
