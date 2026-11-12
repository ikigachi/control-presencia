package software.ulpgc.attendance.building.infrastructure;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.javalin.Javalin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import software.ulpgc.attendance.application.domain.model.Building;
import software.ulpgc.attendance.building.application.usecases.BuildingDirectory;
import software.ulpgc.attendance.building.infrastructure.adapters.input.BuildingApi;
import software.ulpgc.attendance.building.infrastructure.adapters.output.JsonBuildingRepository;

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

class BuildingServiceTest {
    private static final String TEST_TOKEN = "test-token";
    private static final String BUILDINGS = "/api/buildings";
    private static final String BUILDING = BUILDINGS + "/hotel-001";
    private static final String NEW_BUILDING = "{\"id\":\"hotel-001\",\"name\":\"Test Hotel\"}";
    private static final String ANNEX = "{\"id\":\"hotel-east.2\",\"name\":\"Hotel Annex\"}";
    private static final Instant REGISTRATION = Instant.parse("2026-11-08T09:00:00Z");
    private static final Instant DEACTIVATION = REGISTRATION.plusSeconds(60);

    @TempDir
    Path directory;

    @Test
    void preservesRegistrationAndDeactivationAcrossRestarts() throws Exception {
        Path data = directory.resolve("buildings.json");
        try (HttpClient client = HttpClient.newHttpClient()) {
            Javalin server = start(data, REGISTRATION);
            try {
                var created = request(client, server, "POST", BUILDINGS, NEW_BUILDING, TEST_TOKEN);
                assertEquals(201, created.statusCode());
                assertEquals(BUILDING, created.headers().firstValue("Location").orElseThrow());
                assertTrue(object(created).get("active").getAsBoolean());
                assertTrue(object(created).get("deactivatedAt").isJsonNull());
                assertEquals(201, request(client, server, "POST", BUILDINGS, ANNEX, TEST_TOKEN).statusCode());
            } finally {
                server.stop();
            }

            server = start(data, DEACTIVATION);
            try {
                var found = request(client, server, "GET", BUILDING, null, TEST_TOKEN);
                assertEquals(200, found.statusCode());
                assertEquals(REGISTRATION.toString(), object(found).get("registeredAt").getAsString());
                var deactivated = request(client, server, "POST", BUILDING + "/deactivate", null, TEST_TOKEN);
                assertEquals(200, deactivated.statusCode());
                assertFalse(object(deactivated).get("active").getAsBoolean());
                assertEquals(DEACTIVATION.toString(), object(deactivated).get("deactivatedAt").getAsString());
            } finally {
                server.stop();
            }

            server = start(data, DEACTIVATION.plusSeconds(60));
            try {
                var inactive = request(client, server, "GET", BUILDING, null, TEST_TOKEN);
                assertEquals(200, inactive.statusCode());
                assertFalse(object(inactive).get("active").getAsBoolean());
                byte[] beforeRejectedChanges = Files.readAllBytes(data);
                var repeated = request(client, server, "POST", BUILDING + "/deactivate", null, TEST_TOKEN);
                assertEquals(200, repeated.statusCode());
                assertEquals(DEACTIVATION.toString(), object(repeated).get("deactivatedAt").getAsString());
                var duplicate = request(client, server, "POST", BUILDINGS, NEW_BUILDING, TEST_TOKEN);
                assertEquals(409, duplicate.statusCode());
                assertEquals("building_exists", object(duplicate).get("code").getAsString());
                assertArrayEquals(beforeRejectedChanges, Files.readAllBytes(data));

                var listed = request(client, server, "GET", BUILDINGS, null, TEST_TOKEN);
                assertEquals(200, listed.statusCode());
                var records = JsonParser.parseString(listed.body()).getAsJsonArray();
                assertEquals(2, records.size());
                assertFalse(records.get(0).getAsJsonObject().get("active").getAsBoolean());
                assertEquals("hotel-east.2", records.get(1).getAsJsonObject().get("id").getAsString());
                assertTrue(records.get(1).getAsJsonObject().get("active").getAsBoolean());
            } finally {
                server.stop();
            }
        }
    }

    @Test
    void rejectsUnauthorizedInvalidAndUnknownRequestsWithoutCreatingData() throws Exception {
        Path data = directory.resolve("buildings.json");
        Javalin server = start(data, REGISTRATION);
        try (HttpClient client = HttpClient.newHttpClient()) {
            var unauthorized = request(client, server, "POST", BUILDINGS, NEW_BUILDING, null);
            assertEquals(401, unauthorized.statusCode());
            assertEquals("Bearer", unauthorized.headers().firstValue("WWW-Authenticate").orElseThrow());
            assertEquals(401, request(client, server, "GET", BUILDINGS, null, "wrong-token").statusCode());
            assertEquals(400, request(client, server, "POST", BUILDINGS, "{bad json}", TEST_TOKEN).statusCode());
            assertEquals(400, request(client, server, "POST", BUILDINGS,
                    "{\"id\":17,\"name\":\"Test Hotel\"}", TEST_TOKEN).statusCode());
            assertEquals(400, request(client, server, "POST", BUILDINGS,
                    "{\"id\":\"invalid/id\",\"name\":\"Test Hotel\"}", TEST_TOKEN).statusCode());
            assertEquals(400, request(client, server, "POST", BUILDINGS,
                    "{\"id\":\"hotel-001\",\"name\":\" \"}", TEST_TOKEN).statusCode());
            assertEquals(404, request(client, server, "GET", BUILDING, null, TEST_TOKEN).statusCode());
            assertEquals(404, request(client, server, "POST", BUILDING + "/deactivate", null, TEST_TOKEN).statusCode());
            assertFalse(Files.exists(data));
        } finally {
            server.stop();
        }
    }

    @Test
    void doesNotChangeTheDirectoryWhenStorageFails() throws IOException {
        Path data = directory.resolve("buildings.json");
        BuildingDirectory buildings = directory(data, REGISTRATION);
        Files.createDirectory(data);
        assertThrows(IOException.class, () -> buildings.register(new Building("hotel-001", "Test Hotel")));
        assertTrue(buildings.list().isEmpty());
        assertTrue(Files.isDirectory(data));
    }

    @Test
    void keepsABuildingActiveWhenDeactivationCannotBeSaved() throws IOException {
        Path data = directory.resolve("buildings.json");
        var repository = new JsonBuildingRepository(data);
        var original = new BuildingDirectory(repository, Clock.fixed(REGISTRATION, ZoneOffset.UTC))
                .register(new Building("hotel-001", "Test Hotel"));
        Path backup = directory.resolve("original.json");
        Files.move(data, backup);
        Files.createDirectory(data);
        Files.writeString(data.resolve("blocking-file"), "Keep this file");
        var buildings = new BuildingDirectory(repository, Clock.fixed(DEACTIVATION, ZoneOffset.UTC));

        assertThrows(IOException.class, () -> buildings.deactivate("hotel-001"));
        assertEquals(original, buildings.find("hotel-001"));
        assertEquals(original, new JsonBuildingRepository(backup).findById("hotel-001").orElseThrow());
        assertEquals("Keep this file", Files.readString(data.resolve("blocking-file")));
        try (var files = Files.list(directory)) {
            assertFalse(files.anyMatch(path -> path.getFileName().toString().endsWith(".tmp")));
        }
    }

    @Test
    void rejectsCorruptedStorageWithoutOverwritingIt() throws IOException {
        Path data = Files.writeString(directory.resolve("buildings.json"), "{invalid");
        assertThrows(IOException.class, () -> directory(data, REGISTRATION));
        assertEquals("{invalid", Files.readString(data));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "[{\"id\":17,\"name\":\"Test Hotel\",\"registeredAt\":\"2026-11-08T09:00:00Z\"}]",
            "[{\"id\":\"hotel-001\",\"name\":true,\"registeredAt\":\"2026-11-08T09:00:00Z\"}]",
            "[{\"id\":\"hotel-001\",\"name\":\"Test Hotel\"}]",
            "[{\"id\":\"hotel-001\",\"name\":\"Test Hotel\",\"registeredAt\":\"2026-11-08T09:00:00Z\",\"deactivatedAt\":\"2026-11-07T09:00:00Z\"}]",
            "[null]",
            "null"
    })
    void rejectsInvalidStoredRecordsWithoutChangingTheFile(String contents) throws IOException {
        Path data = Files.writeString(directory.resolve("buildings.json"), contents);
        assertThrows(IOException.class, () -> directory(data, REGISTRATION));
        assertEquals(contents, Files.readString(data));
    }

    private static Javalin start(Path data, Instant now) throws IOException {
        return new BuildingApi(directory(data, now), TEST_TOKEN).createServer().start("127.0.0.1", 0);
    }

    private static BuildingDirectory directory(Path data, Instant now) throws IOException {
        return new BuildingDirectory(new JsonBuildingRepository(data), Clock.fixed(now, ZoneOffset.UTC));
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
