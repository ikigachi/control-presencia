package software.ulpgc.attendance.employee.infrastructure;

import software.ulpgc.attendance.employee.application.usecases.EmployeeDirectory;
import software.ulpgc.attendance.employee.infrastructure.adapters.input.EmployeeApi;
import software.ulpgc.attendance.employee.infrastructure.adapters.output.JsonEmployeeRepository;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;

public class Main {
    public static void main(String[] args) {
        if (args.length != 2) {
            System.err.println("Usage: employee-service <port> <employee-data.json>");
            System.exit(1);
            return;
        }

        try {
            int port = Integer.parseInt(args[0]);
            if (port < 1 || port > 65535)
                throw new IllegalArgumentException("Port must be between 1 and 65535");
            EmployeeDirectory employees = new EmployeeDirectory(
                    new JsonEmployeeRepository(Path.of(args[1])), Clock.systemUTC());
            new EmployeeApi(employees, System.getenv("ATTENDANCE_ADMIN_TOKEN"))
                    .createServer().start("127.0.0.1", port);
        } catch (IOException | IllegalArgumentException e) {
            System.err.println("Could not start employee service: " + e.getMessage());
            System.exit(1);
        }
    }
}
