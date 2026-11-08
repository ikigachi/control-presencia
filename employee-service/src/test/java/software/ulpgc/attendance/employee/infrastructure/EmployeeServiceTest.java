package software.ulpgc.attendance.employee.infrastructure;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.javalin.Javalin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import software.ulpgc.attendance.application.domain.model.Employee;
import software.ulpgc.attendance.employee.application.usecases.EmployeeDirectory;
import software.ulpgc.attendance.employee.infrastructure.adapters.input.EmployeeApi;
import software.ulpgc.attendance.employee.infrastructure.adapters.output.JsonEmployeeRepository;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

class EmployeeServiceTest {
    private static final String TEST_TOKEN = "test-token";
    private static final String EMPLOYEES = "/api/employees";
    private static final String EMPLOYEE = EMPLOYEES + "/employee-001";
    private static final String NEW_EMPLOYEE = "{\"id\":\"employee-001\",\"name\":\"Test Employee\"}";
    private static final Instant REGISTRATION = Instant.parse("2026-11-08T09:00:00Z");
    private static final Instant DEACTIVATION = REGISTRATION.plusSeconds(60);

    @TempDir
    Path directory;

    @Test
    void preservesRegistrationAndDeactivationAcrossRestarts() throws Exception {
        Path data = directory.resolve("employees.json");
        try (HttpClient client = HttpClient.newHttpClient()) {
            Javalin server = start(data, REGISTRATION);
            try {
                var created = request(client, server, "POST", EMPLOYEES, NEW_EMPLOYEE, TEST_TOKEN);
                assertEquals(201, created.statusCode());
                assertEquals(EMPLOYEE, created.headers().firstValue("Location").orElseThrow());
                assertTrue(object(created).get("active").getAsBoolean());
                assertTrue(object(created).get("deactivatedAt").isJsonNull());
            } finally {
                server.stop();
            }

            server = start(data, DEACTIVATION);
            try {
                var found = request(client, server, "GET", EMPLOYEE, null, TEST_TOKEN);
                assertEquals(200, found.statusCode());
                assertEquals(REGISTRATION.toString(), object(found).get("registeredAt").getAsString());
                var deactivated = request(client, server, "POST", EMPLOYEE + "/deactivate", null, TEST_TOKEN);
                assertEquals(200, deactivated.statusCode());
                assertFalse(object(deactivated).get("active").getAsBoolean());
                assertEquals(DEACTIVATION.toString(), object(deactivated).get("deactivatedAt").getAsString());
            } finally {
                server.stop();
            }

            server = start(data, DEACTIVATION.plusSeconds(60));
            try {
                var repeated = request(client, server, "POST", EMPLOYEE + "/deactivate", null, TEST_TOKEN);
                assertEquals(200, repeated.statusCode());
                assertEquals(DEACTIVATION.toString(), object(repeated).get("deactivatedAt").getAsString());
                var duplicate = request(client, server, "POST", EMPLOYEES, NEW_EMPLOYEE, TEST_TOKEN);
                assertEquals(409, duplicate.statusCode());
                assertEquals("employee_exists", object(duplicate).get("code").getAsString());

                var listed = request(client, server, "GET", EMPLOYEES, null, TEST_TOKEN);
                assertEquals(200, listed.statusCode());
                var records = JsonParser.parseString(listed.body()).getAsJsonArray();
                assertEquals(1, records.size());
                assertFalse(records.get(0).getAsJsonObject().get("active").getAsBoolean());
            } finally {
                server.stop();
            }
        }
    }

    @Test
    void rejectsUnauthorizedInvalidAndUnknownRequestsWithoutCreatingData() throws Exception {
        Path data = directory.resolve("employees.json");
        Javalin server = start(data, REGISTRATION);
        try (HttpClient client = HttpClient.newHttpClient()) {
            var unauthorized = request(client, server, "POST", EMPLOYEES, NEW_EMPLOYEE, null);
            assertEquals(401, unauthorized.statusCode());
            assertEquals("Bearer", unauthorized.headers().firstValue("WWW-Authenticate").orElseThrow());
            assertEquals(401, request(client, server, "GET", EMPLOYEES, null, "wrong-token").statusCode());
            assertEquals(400, request(client, server, "POST", EMPLOYEES, "{bad json}", TEST_TOKEN).statusCode());
            assertEquals(400, request(client, server, "POST", EMPLOYEES,
                    "{\"id\":17,\"name\":\"Test Employee\"}", TEST_TOKEN).statusCode());
            assertEquals(400, request(client, server, "POST", EMPLOYEES,
                    "{\"id\":\"invalid/id\",\"name\":\"Test Employee\"}", TEST_TOKEN).statusCode());
            assertEquals(404, request(client, server, "GET", EMPLOYEE, null, TEST_TOKEN).statusCode());
            assertEquals(404, request(client, server, "POST", EMPLOYEE + "/deactivate", null, TEST_TOKEN).statusCode());
            assertFalse(Files.exists(data));
        } finally {
            server.stop();
        }
    }

    @Test
    void doesNotChangeTheDirectoryWhenStorageFails() throws IOException {
        Path data = directory.resolve("employees.json");
        EmployeeDirectory employees = directory(data, REGISTRATION);
        Files.createDirectory(data);
        assertThrows(IOException.class, () -> employees.register(new Employee("employee-001", "Test Employee")));
        assertTrue(employees.list().isEmpty());
        assertTrue(Files.isDirectory(data));
    }

    @Test
    void rejectsCorruptedStorageWithoutOverwritingIt() throws IOException {
        Path data = Files.writeString(directory.resolve("employees.json"), "{invalid");
        assertThrows(IOException.class, () -> directory(data, REGISTRATION));
        assertEquals("{invalid", Files.readString(data));
    }

    private static Javalin start(Path data, Instant now) throws IOException {
        return new EmployeeApi(directory(data, now), TEST_TOKEN).createServer().start("127.0.0.1", 0);
    }

    private static EmployeeDirectory directory(Path data, Instant now) throws IOException {
        return new EmployeeDirectory(new JsonEmployeeRepository(data), Clock.fixed(now, ZoneOffset.UTC));
    }

    private static JsonObject object(HttpResponse<String> response) {
        return JsonParser.parseString(response.body()).getAsJsonObject();
    }

    private static HttpResponse<String> request(HttpClient client, Javalin server, String method,
                                                String route, String json, String token) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + server.port() + route))
                .timeout(Duration.ofSeconds(5));
        if (token != null)
            request.header("Authorization", "Bearer " + token);
        if (json != null)
            request.header("Content-Type", "application/json");
        request.method(method, json == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(json));
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
}
