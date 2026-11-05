package software.ulpgc.attendance.tracking.application.usecases;

import software.ulpgc.attendance.application.domain.model.TrackingEvent;
import software.ulpgc.attendance.tracking.application.ports.input.DeviceReader;
import software.ulpgc.attendance.tracking.application.ports.input.TrackingEventParser;
import software.ulpgc.attendance.tracking.application.ports.output.EventPublisher;

import java.io.IOException;
import java.util.Objects;

public class RegisterPresenceUseCase {
    private final DeviceReader reader;
    private final TrackingEventParser parser;
    private final EventPublisher publisher;

    public RegisterPresenceUseCase(DeviceReader reader, TrackingEventParser parser, EventPublisher publisher) {
        this.reader = Objects.requireNonNull(reader);
        this.parser = Objects.requireNonNull(parser);
        this.publisher = Objects.requireNonNull(publisher);
    }

    public void execute() throws IOException {
        String input = reader.read();
        TrackingEvent event = Objects.requireNonNull(parser.parse(input), "Parsed event is required");
        publisher.publish(event);
    }
}
