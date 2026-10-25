package elevatorSystem.strategy;

import elevatorSystem.entity.Building;
import elevatorSystem.entity.Elevator;
import elevatorSystem.entity.ExternalRequest;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Hands hall calls to the available elevators in turn (E1, E2, E3, E1, ...), ignoring where they are.
 * Spreads wear evenly across elevators, but can send a far-away elevator when a closer one is idle.
 */
public class RoundRobinStrategy implements ElevatorSelectionStrategy {
    private final Map<String, AtomicInteger> nextIndexByBuilding = new ConcurrentHashMap<>();

    @Override
    public Optional<Elevator> selectElevator(List<Elevator> candidates, ExternalRequest request, Building building) {
        if (candidates.isEmpty()) {
            return Optional.empty();
        }
        List<Elevator> ordered = candidates.stream()
                .sorted(Comparator.comparing(Elevator::getName))
                .collect(Collectors.toList());
        int next = nextIndexByBuilding.computeIfAbsent(building.getId(), id -> new AtomicInteger()).getAndIncrement();
        return Optional.of(ordered.get(Math.floorMod(next, ordered.size())));
    }
}
