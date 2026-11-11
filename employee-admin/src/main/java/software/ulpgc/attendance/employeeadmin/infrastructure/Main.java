package software.ulpgc.attendance.employeeadmin.infrastructure;

import software.ulpgc.attendance.employeeadmin.infrastructure.adapters.input.EmployeeAdminServer;
import software.ulpgc.attendance.employeeadmin.infrastructure.adapters.output.HttpEmployeeClient;

import java.io.IOException;
import java.net.http.HttpClient;
import java.time.Duration;

public class Main {
    public static void main(String[] args) {
        try {
            start(EmployeeAdminConfiguration.load(args, System::getenv));
        } catch (IOException | IllegalArgumentException e) {
            System.err.println("Could not start employee administration: " + e.getMessage());
            System.exit(1);
        }
    }

    private static void start(EmployeeAdminConfiguration configuration) throws IOException {
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        EmployeeAdminServer admin = null;
        try {
            var employees = new HttpEmployeeClient(http, configuration.serviceUrl(), configuration.apiToken());
            admin = new EmployeeAdminServer(employees, configuration.webPassword(), configuration.port());
            admin.start();
            EmployeeAdminServer runningServer = admin;
            Runtime.getRuntime().addShutdownHook(new Thread(() -> stop(runningServer, http)));
            System.out.println("Employee administration: http://127.0.0.1:" + admin.port() + "/");
        } catch (IOException | RuntimeException e) {
            stop(admin, http);
            throw e;
        }
    }

    private static void stop(EmployeeAdminServer admin, HttpClient http) {
        try {
            if (admin != null)
                admin.close();
        } finally {
            http.shutdownNow();
        }
    }
}
