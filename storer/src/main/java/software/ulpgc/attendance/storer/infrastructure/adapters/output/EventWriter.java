package software.ulpgc.attendance.storer.infrastructure.adapters.output;

import software.ulpgc.attendance.application.domain.model.TrackingEvent;

import java.io.*;
import java.nio.charset.StandardCharsets;

public class EventWriter {
    public static void writeEvent(TrackingEvent event, OutputStream output) throws IOException {
        writeLine(formatEvent(event), output);
    }

    static String formatEvent(TrackingEvent event) {
        validateIdentifier(event.employee(), "Employee");
        validateIdentifier(event.building(), "Building");
        return event.employee() + ";" + event.building() + ";"
                + event.ts() + ";" + event.type().name();
    }

    static void writeLine(String line, OutputStream output) throws IOException {
        Writer writer = new OutputStreamWriter(output, StandardCharsets.UTF_8);
        writer.write(line + "\n");
        writer.flush();
    }

    private static void validateIdentifier(String identifier, String field) {
        if (identifier.indexOf(';') >= 0 || identifier.indexOf('\r') >= 0 || identifier.indexOf('\n') >= 0)
            throw new IllegalArgumentException(field + " id cannot contain semicolons or line breaks in a text log");
    }
}
