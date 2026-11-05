package software.ulpgc.attendance.storer.application.ports.output;

import software.ulpgc.attendance.application.domain.model.TrackingEvent;

import java.io.IOException;

public interface EventStore {
    void save(TrackingEvent event) throws IOException;
}
