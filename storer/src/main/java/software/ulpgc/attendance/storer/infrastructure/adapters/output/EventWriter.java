package software.ulpgc.attendance.storer.infrastructure.adapters.output;

import software.ulpgc.attendance.application.domain.model.TrackingEvent;

import java.io.*;
import java.nio.charset.StandardCharsets;

public class EventWriter {
    public static void writeEvent(TrackingEvent event, OutputStream output) throws IOException {
        Writer writer = new OutputStreamWriter(output, StandardCharsets.UTF_8);
        writer.write(event.employee() + ";" + event.building() + ";"
                     + event.ts() + ";" + event.type().name() + "\n");
        writer.flush();
    }
}
