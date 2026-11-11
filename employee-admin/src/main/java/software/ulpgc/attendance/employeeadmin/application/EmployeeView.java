package software.ulpgc.attendance.employeeadmin.application;

public record EmployeeView(String id, String name, boolean active, String registeredAt, String deactivatedAt) {
}
