package software.ulpgc.attendance.building.infrastructure;

import software.ulpgc.attendance.building.application.usecases.BuildingDirectory;
import software.ulpgc.attendance.building.infrastructure.adapters.input.BuildingApi;
import software.ulpgc.attendance.building.infrastructure.adapters.output.JsonBuildingRepository;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;

public class Main {
    public static void main(String[] args) {
        if (args.length != 2) {
            System.err.println("Usage: building-service <port> <building-data.json>");
            System.exit(1);
            return;
        }

        try {
            int port = Integer.parseInt(args[0]);
            if (port < 1 || port > 65535)
                throw new IllegalArgumentException("Port must be between 1 and 65535");
            BuildingDirectory buildings = new BuildingDirectory(
                    new JsonBuildingRepository(Path.of(args[1])), Clock.systemUTC());
            new BuildingApi(buildings, System.getenv("ATTENDANCE_ADMIN_TOKEN"))
                    .createServer().start("127.0.0.1", port);
        } catch (IOException | IllegalArgumentException e) {
            System.err.println("Could not start building service: " + e.getMessage());
            System.exit(1);
        }
    }
}
