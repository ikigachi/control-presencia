package software.ulpgc.attendance.application.domain.model;

import java.time.Instant;

public record TrackingEvent(Instant ts, String employee, String building, TrackingType type) {
    public TrackingEvent {
        if (ts == null)
            throw new IllegalArgumentException("Tracking timestamp is required");
        if (employee == null || employee.isBlank())
            throw new IllegalArgumentException("Employee id is required");
        if (building == null || building.isBlank())
            throw new IllegalArgumentException("Building id is required");
        if (type == null)
            throw new IllegalArgumentException("Tracking type is required");
    }
}
