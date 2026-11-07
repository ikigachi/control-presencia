package software.ulpgc.attendance.tracking.infrastructure.adapters.input;

import software.ulpgc.attendance.application.domain.model.TrackingEvent;
import software.ulpgc.attendance.application.domain.model.TrackingType;
import software.ulpgc.attendance.tracking.application.ports.input.TrackingEventParser;

import java.time.Clock;

public class SimulatedTrackingEventParser implements TrackingEventParser {
    private final String building;
    private final Clock clock;

    public SimulatedTrackingEventParser(String building, Clock clock) {
        this.building = building;
        this.clock = clock;
    }

    @Override
    public TrackingEvent parse(String input) {
        String[] fields = input.split(";", -1);
        if (fields.length != 2)
            throw new IllegalArgumentException("Expected simulated input in the form employee-id;in or employee-id;out");

        TrackingType type = switch (fields[1]) {
            case "in" -> TrackingType.ENTRY;
            case "out" -> TrackingType.EXIT;
            default -> throw new IllegalArgumentException("Tracking type must be 'in' or 'out'");
        };
        return new TrackingEvent(clock.instant(), fields[0], building, type);
    }
}
