package elevatorSystem.controller;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * One lock per building. Everything a tick or a button press touches (cars, requests, the clock) belongs to a
 * single building, so buildings never share mutable state and can run fully in parallel, while operations on
 * the same building are serialised.
 *
 * Fair, so a steady stream of button presses cannot starve that building's clock (or the other way round).
 * Reentrant, so a controller method may call another locked method of the same building.
 */
public class BuildingLocks {
    private final Map<String, ReentrantLock> locks = new ConcurrentHashMap<>();

    public <T> T call(String buildingId, Supplier<T> action) {
        ReentrantLock lock = locks.computeIfAbsent(buildingId, id -> new ReentrantLock(true));
        lock.lock();
        try {
            return action.get();
        } finally {
            lock.unlock();
        }
    }

    public void run(String buildingId, Runnable action) {
        call(buildingId, () -> {
            action.run();
            return null;
        });
    }
}
