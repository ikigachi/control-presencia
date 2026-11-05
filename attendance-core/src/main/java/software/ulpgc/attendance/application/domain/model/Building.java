package software.ulpgc.attendance.application.domain.model;

public record Building(String id, String name) {
    public Building {
        if (id == null || id.isBlank())
            throw new IllegalArgumentException("Building id is required");
        if (name == null || name.isBlank())
            throw new IllegalArgumentException("Building name is required");
    }
}
