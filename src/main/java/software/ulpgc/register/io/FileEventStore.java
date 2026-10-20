package software.ulpgc.register.io;

import software.ulpgc.register.model.TrackingEvent;
import java.util.List;
import java.io.File;

public class FileEventStore implements EventStore{
    private final File root;

    public FileEventStore(File root) {
        this.root = root;
    }

    @Override
    public void register(TrackingEvent event) {

    }
}
