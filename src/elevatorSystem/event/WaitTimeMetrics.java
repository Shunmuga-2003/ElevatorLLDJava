package elevatorSystem.event;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks how long callers wait, per building: the number of hall calls served and the average and worst
 * wait in ticks (button press to doors opening). Stats are immutable values replaced atomically,
 * so they can be read from any thread while the clock is running.
 */
public class WaitTimeMetrics implements ElevatorEventListener {
    private final Map<String, Stats> statsByBuilding = new ConcurrentHashMap<>();

    public record Stats(long served, long totalWaitTicks, long maxWaitTicks) {
        static final Stats EMPTY = new Stats(0, 0, 0);

        public double averageWaitTicks() {
            return served == 0 ? 0 : (double) totalWaitTicks / served;
        }

        Stats plus(long waitTicks) {
            return new Stats(served + 1, totalWaitTicks + waitTicks, Math.max(maxWaitTicks, waitTicks));
        }
    }

    @Override
    public void onEvent(ElevatorEvent event) {
        if (event instanceof ElevatorEvent.HallCallCompleted completed) {
            statsByBuilding.merge(completed.buildingId(), Stats.EMPTY.plus(completed.waitTicks()),
                    (current, single) -> current.plus(completed.waitTicks()));
        }
    }

    public Stats statsFor(String buildingId) {
        return statsByBuilding.getOrDefault(buildingId, Stats.EMPTY);
    }
}
