package elevatorSystem.strategy;

import elevatorSystem.Entity.Building;
import elevatorSystem.Entity.Elevator;
import elevatorSystem.Entity.ExternalRequest;

import java.util.List;
import java.util.Optional;

/**
 * Splits the floors into equal bands, one per elevator: with floors 0-10 and three elevators,
 * E1 serves 0-3, E2 serves 4-7 and E3 serves 8-10. A hall call goes to the elevator that owns
 * the caller's zone. If that elevator is unavailable (maintenance, full), the fallback strategy
 * chooses among the others.
 *
 * Zones follow the elevator numbering E1..En that {@code BuildingService} gives each building.
 */
public class ZoneBasedStrategy implements ElevatorSelectionStrategy {
    private final ElevatorSelectionStrategy fallback;

    public ZoneBasedStrategy() {
        this(new NearestElevatorStrategy());
    }

    public ZoneBasedStrategy(ElevatorSelectionStrategy fallback) {
        this.fallback = fallback;
    }

    @Override
    public Optional<Elevator> selectElevator(List<Elevator> candidates, ExternalRequest request, Building building) {
        String owner = "E" + (zoneOf(request.getFloorNumber(), building) + 1);
        return candidates.stream()
                .filter(e -> e.getName().equals(owner))
                .findFirst()
                .or(() -> fallback.selectElevator(candidates, request, building));
    }

    /** @return the zero-based zone index of {@code floor}. */
    int zoneOf(int floor, Building building) {
        int floors = building.getMaxFloor() - building.getMinFloor() + 1;
        return (floor - building.getMinFloor()) * building.getTotalElevators() / floors;
    }
}
