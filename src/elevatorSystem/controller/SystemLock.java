package elevatorSystem.controller;

import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * One lock shared by every controller of an {@code ElevatorSystem}. Button presses can arrive from
 * many threads while the clock thread is moving elevators; holding this lock around each controller
 * call means the elevators' stop sets and request statuses are only ever changed by one thread at a time.
 *
 * Fair, so a steady stream of button presses cannot starve the clock (or the other way round).
 * Reentrant, so a controller method may call another locked method of the same system.
 */
public class SystemLock {
    private final ReentrantLock lock = new ReentrantLock(true);

    public <T> T call(Supplier<T> action) {
        lock.lock();
        try {
            return action.get();
        } finally {
            lock.unlock();
        }
    }

    public void run(Runnable action) {
        lock.lock();
        try {
            action.run();
        } finally {
            lock.unlock();
        }
    }
}
