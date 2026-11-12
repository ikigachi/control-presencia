package software.ulpgc.attendance.building.application.ports.output;

import software.ulpgc.attendance.building.application.domain.model.BuildingRegistration;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

public interface BuildingRepository {
    Optional<BuildingRegistration> findById(String id);

    List<BuildingRegistration> findAll();

    void save(BuildingRegistration registration) throws IOException;
}
