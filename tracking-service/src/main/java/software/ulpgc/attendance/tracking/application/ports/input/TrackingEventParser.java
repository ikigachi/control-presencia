package software.ulpgc.attendance.tracking.application.ports.input;

import software.ulpgc.attendance.application.domain.model.TrackingEvent;

public interface TrackingEventParser {
    TrackingEvent parse(String input);
}
