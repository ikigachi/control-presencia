package software.ulpgc.attendance.employeeadmin.infrastructure;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class EmployeeAdminConfigurationTest {
    @Test
    void readsTheSameLaunchArgumentsWithoutPrintingSecrets() {
        var environment = Map.of("ATTENDANCE_ADMIN_TOKEN", "test-api-token",
                "ATTENDANCE_ADMIN_WEB_PASSWORD", "test-web-password");
        var configuration = EmployeeAdminConfiguration.load(
                new String[]{"http://127.0.0.1:8081", "8082"}, environment::get);

        assertEquals(URI.create("http://127.0.0.1:8081"), configuration.serviceUrl());
        assertEquals(8082, configuration.port());
        assertEquals("test-api-token", configuration.apiToken());
        assertEquals("test-web-password", configuration.webPassword());
        assertFalse(configuration.toString().contains("test-api-token"));
        assertFalse(configuration.toString().contains("test-web-password"));
    }

    @Test
    void reportsIncorrectPortAndMissingPasswordClearly() {
        var badPort = assertThrows(IllegalArgumentException.class, () -> EmployeeAdminConfiguration.load(
                new String[]{"http://127.0.0.1:8081", "not-a-port"}, key -> null));
        assertEquals("Port must be an integer between 1 and 65535", badPort.getMessage());

        var missingPassword = assertThrows(IllegalArgumentException.class, () -> EmployeeAdminConfiguration.load(
                new String[]{"http://127.0.0.1:8081", "8082"}, Map.of("ATTENDANCE_ADMIN_TOKEN", "test-api-token")::get));
        assertEquals("ATTENDANCE_ADMIN_WEB_PASSWORD must be set", missingPassword.getMessage());
    }
}
