package software.ulpgc.attendance.building.application.usecases;

import software.ulpgc.attendance.application.domain.model.Building;
import software.ulpgc.attendance.building.application.domain.model.BuildingRegistration;
import software.ulpgc.attendance.building.application.ports.output.BuildingRepository;

import java.io.IOException;
import java.time.Clock;
import java.util.List;

public class BuildingDirectory {
    private final BuildingRepository repository;
    private final Clock clock;

    public BuildingDirectory(BuildingRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public synchronized BuildingRegistration register(Building building) throws IOException {
        BuildingRegistration registration = new BuildingRegistration(building, clock.instant(), null);
        if (repository.findById(building.id()).isPresent())
            throw new DuplicateBuildingException();

        repository.save(registration);
        return registration;
    }

    public synchronized BuildingRegistration find(String id) {
        return repository.findById(id).orElseThrow(BuildingNotFoundException::new);
    }

    public synchronized List<BuildingRegistration> list() {
        return repository.findAll();
    }

    public synchronized BuildingRegistration deactivate(String id) throws IOException {
        BuildingRegistration current = find(id);
        if (!current.active())
            return current;

        BuildingRegistration deactivated = current.deactivate(clock.instant());
        repository.save(deactivated);
        return deactivated;
    }

    public static class DuplicateBuildingException extends RuntimeException {
    }

    public static class BuildingNotFoundException extends RuntimeException {
    }
}
