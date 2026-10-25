package elevatorSystem.controller;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Drives a building's clock in real time instead of tick-by-tick from a test: one tick every {@code period}
 * on a dedicated thread. Button presses from other threads interleave safely through the building lock.
 * A failing tick is reported and the clock keeps running, so one bad event cannot freeze the elevators.
 */
public final class RealTimeClock implements AutoCloseable {
    private final ScheduledExecutorService executor;

    private RealTimeClock(ScheduledExecutorService executor) {
        this.executor = executor;
    }

    public static RealTimeClock start(ElevatorController controller, String buildingId, Duration period) {
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "elevator-clock-" + buildingId);
            thread.setDaemon(true);
            return thread;
        });
        executor.scheduleAtFixedRate(() -> {
            try {
                controller.tick(buildingId);
            } catch (RuntimeException e) {
                System.err.println("Tick failed for building " + buildingId + ": " + e);
            }
        }, 0, period.toNanos(), TimeUnit.NANOSECONDS);
        return new RealTimeClock(executor);
    }

    /** Stops the clock and waits for an in-flight tick to finish. */
    @Override
    public void close() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
