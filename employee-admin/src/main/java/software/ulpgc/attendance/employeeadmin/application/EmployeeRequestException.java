package software.ulpgc.attendance.employeeadmin.application;

import java.io.IOException;

public class EmployeeRequestException extends IOException {
    public enum Reason {
        INVALID_INPUT, NOT_FOUND, CONFLICT, UNAVAILABLE
    }

    private final Reason reason;

    public EmployeeRequestException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
