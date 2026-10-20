package software.ulpgc.register.io;

import software.ulpgc.register.model.TrackingEvent;

import java.io.*;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

public class FileEventStore implements EventStore {
    private final File root;

    public FileEventStore(File root) {
        this.root = root;
    }

    @Override
    public void save(TrackingEvent event) throws IOException {
        try (OutputStream output = new FileOutputStream(prepareDailyLogFile(event), true)) {
            EventWriter.write(event, output);
        }
    }

    private File prepareDailyLogFile(TrackingEvent event) throws IOException {
        LocalDate date = event.ts().atZone(ZoneOffset.UTC).toLocalDate();
        File directory = new File(root, String.valueOf(date.getYear()));
        if (!directory.isDirectory() && !directory.mkdirs())
            throw new IOException("Could not create directory " + directory);
        return new File(directory, date.format(DateTimeFormatter.BASIC_ISO_DATE) + ".log");
    }
}
