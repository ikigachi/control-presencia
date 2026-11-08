package software.ulpgc.attendance.employee.application.domain.model;

import software.ulpgc.attendance.application.domain.model.Employee;

import java.time.Instant;
import java.util.Objects;

public record EmployeeRegistration(Employee employee, Instant registeredAt, Instant deactivatedAt) {
    public EmployeeRegistration {
        Objects.requireNonNull(employee);
        Objects.requireNonNull(registeredAt);
        if (!employee.id().matches("[A-Za-z0-9][A-Za-z0-9._-]*"))
            throw new IllegalArgumentException("Employee id must start with a letter or digit and use only letters, digits, dots, underscores or hyphens");
        if (deactivatedAt != null && deactivatedAt.isBefore(registeredAt))
            throw new IllegalArgumentException("Deactivation cannot precede registration");
    }

    public boolean active() {
        return deactivatedAt == null;
    }

    public EmployeeRegistration deactivate(Instant timestamp) {
        return active() ? new EmployeeRegistration(employee, registeredAt, Objects.requireNonNull(timestamp)) : this;
    }
}
