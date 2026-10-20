package software.ulpgc.register.io;

import software.ulpgc.register.model.TrackingEvent;

public interface EventStore {
    void register(TrackingEvent event);
}
