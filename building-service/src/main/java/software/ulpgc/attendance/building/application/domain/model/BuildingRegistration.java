package software.ulpgc.attendance.building.application.domain.model;

import software.ulpgc.attendance.application.domain.model.Building;

import java.time.Instant;
import java.util.Objects;

public record BuildingRegistration(Building building, Instant registeredAt, Instant deactivatedAt) {
    public BuildingRegistration {
        Objects.requireNonNull(building);
        Objects.requireNonNull(registeredAt);
        if (!building.id().matches("[A-Za-z0-9][A-Za-z0-9._-]*"))
            throw new IllegalArgumentException("Building id must start with a letter or digit and use only letters, digits, dots, underscores or hyphens");
        if (deactivatedAt != null && deactivatedAt.isBefore(registeredAt))
            throw new IllegalArgumentException("Deactivation cannot precede registration");
    }

    public boolean active() {
        return deactivatedAt == null;
    }

    public BuildingRegistration deactivate(Instant timestamp) {
        return active() ? new BuildingRegistration(building, registeredAt, Objects.requireNonNull(timestamp)) : this;
    }
}
