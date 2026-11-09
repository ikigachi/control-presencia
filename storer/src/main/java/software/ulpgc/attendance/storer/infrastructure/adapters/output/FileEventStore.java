package software.ulpgc.attendance.storer.infrastructure.adapters.output;

import software.ulpgc.attendance.application.domain.model.TrackingEvent;
import software.ulpgc.attendance.storer.application.ports.output.EventStore;

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
        String line = EventWriter.formatEvent(event);
        try (OutputStream output = new FileOutputStream(prepareDailyLogFile(event), true)) {
            EventWriter.writeLine(line, output);
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
