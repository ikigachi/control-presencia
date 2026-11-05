package software.ulpgc.attendance.tracking.application.ports.output;

import software.ulpgc.attendance.application.domain.model.TrackingEvent;

import java.io.IOException;

public interface EventPublisher {
    void publish(TrackingEvent event) throws IOException;
}
