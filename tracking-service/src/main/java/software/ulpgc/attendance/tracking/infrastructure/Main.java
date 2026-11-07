package software.ulpgc.attendance.tracking.infrastructure;

import software.ulpgc.attendance.tracking.application.ports.input.DeviceReader;
import software.ulpgc.attendance.tracking.application.usecases.RegisterPresenceUseCase;
import software.ulpgc.attendance.tracking.infrastructure.adapters.input.SimulatedTrackingEventParser;
import software.ulpgc.attendance.tracking.infrastructure.adapters.output.ConsoleEventPublisher;
import software.ulpgc.attendance.tracking.infrastructure.configuration.TrackingConfiguration;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.time.Clock;

public class Main {
    public static void main(String[] args) {
        int status = run(args, System.out, System.err, Clock.systemUTC());
        if (status != 0)
            System.exit(status);
    }

    static int run(String[] args, PrintStream output, PrintStream errors, Clock clock) {
        if (args.length != 2) {
            errors.println("Usage: Main <configuration.json> <employee-id;in|out>");
            return 1;
        }

        try {
            TrackingConfiguration configuration = TrackingConfiguration.load(Path.of(args[0]));
            DeviceReader simulatedDevice = () -> args[1];
            new RegisterPresenceUseCase(
                    simulatedDevice,
                    new SimulatedTrackingEventParser(configuration.building(), clock),
                    new ConsoleEventPublisher(output)
            ).execute();
            return 0;
        } catch (IOException | IllegalArgumentException e) {
            errors.println("Tracking simulation failed: " + e.getMessage());
            return 1;
        }
    }
}
