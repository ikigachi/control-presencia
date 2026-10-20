package software.ulpgc.register.io;

import software.ulpgc.register.model.TrackingEvent;

import java.io.IOException;

public interface EventStore {
    void save(TrackingEvent event) throws IOException;
}
