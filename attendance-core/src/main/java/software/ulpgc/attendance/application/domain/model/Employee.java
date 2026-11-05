package software.ulpgc.attendance.application.domain.model;

public record Employee(String id, String name) {
    public Employee {
        if (id == null || id.isBlank())
            throw new IllegalArgumentException("Employee id is required");
        if (name == null || name.isBlank())
            throw new IllegalArgumentException("Employee name is required");
    }
}
