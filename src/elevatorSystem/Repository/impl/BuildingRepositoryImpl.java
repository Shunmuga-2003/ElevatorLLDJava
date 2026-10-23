package elevatorSystem.Repository.impl;

import elevatorSystem.Entity.Building;
import elevatorSystem.Repository.BuildingRepository;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class BuildingRepositoryImpl implements BuildingRepository {
    private final Map<String,Building> buildingMap=new ConcurrentHashMap<>();
    @Override
    public Building save(Building building) {
        String buildingId=building.getId();
        buildingMap.put(buildingId,building);
        return building;
    }
    @Override
    public Optional<Building> getBuildingById(String buildingId) {
        return Optional.ofNullable(buildingMap.get(buildingId));
    }
    @Override
    public List<Building> findAll() {
        return new ArrayList<>(buildingMap.values());
    }

    @Override
    public void deleteByBuildingId(String buildingId) {
        buildingMap.remove(buildingId);
    }
}
