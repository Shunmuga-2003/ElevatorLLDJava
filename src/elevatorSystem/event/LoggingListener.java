package elevatorSystem.event;

import java.util.function.Consumer;

/** Turns every event into a log line such as {@code t=005 E1: moving UP 4 -> 5}. */
public class LoggingListener implements ElevatorEventListener {
    private final Consumer<String> sink;

    public LoggingListener(Consumer<String> sink) {
        this.sink = sink;
    }

    @Override
    public void onEvent(ElevatorEvent event) {
        sink.accept(String.format("t=%03d %s: %s", event.tick(), event.source(), event.describe()));
    }
}
