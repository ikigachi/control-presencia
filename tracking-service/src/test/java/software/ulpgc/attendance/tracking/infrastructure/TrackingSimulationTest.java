package software.ulpgc.attendance.tracking.infrastructure;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

class TrackingSimulationTest {
    @TempDir
    Path directory;

    private final Clock clock = Clock.fixed(Instant.parse("2026-11-07T09:00:00Z"), ZoneOffset.UTC);
    private final ByteArrayOutputStream output = new ByteArrayOutputStream();
    private final ByteArrayOutputStream errors = new ByteArrayOutputStream();

    @ParameterizedTest
    @ValueSource(strings = {"in", "out"})
    void printsAJsonEventUsingTheConfiguredBuilding(String type) throws IOException {
        int status = run(writeConfiguration("{\"building\":\"hotel-west\"}"), "employee-001;" + type);

        assertEquals(0, status);
        assertEquals("", errors.toString(StandardCharsets.UTF_8));
        JsonObject event = JsonParser.parseString(output.toString(StandardCharsets.UTF_8)).getAsJsonObject();
        assertEquals(4, event.size());
        assertEquals("2026-11-07T09:00:00Z", event.get("ts").getAsString());
        assertEquals("employee-001", event.get("employee").getAsString());
        assertEquals("hotel-west", event.get("building").getAsString());
        assertEquals(type, event.get("type").getAsString());
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"building\":17}", "{\"building\":\" \"}", "{building:\"hotel-001\"}"})
    void rejectsInvalidConfigurationWithoutPrintingAnEvent(String configuration) throws IOException {
        assertEquals(1, run(writeConfiguration(configuration), "employee-001;in"));
        assertEquals(0, output.size());
        assertTrue(errors.toString(StandardCharsets.UTF_8).startsWith("Tracking simulation failed:"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"employee-001;unknown", "employee-001", ";in"})
    void rejectsInvalidDeviceInputWithoutPrintingAnEvent(String input) throws IOException {
        assertEquals(1, run(writeConfiguration("{\"building\":\"hotel-001\"}"), input));
        assertEquals(0, output.size());
        assertTrue(errors.toString(StandardCharsets.UTF_8).startsWith("Tracking simulation failed:"));
    }

    @Test
    void reportsAMissingConfigurationFile() {
        assertEquals(1, run(directory.resolve("missing.json"), "employee-001;in"));
        assertEquals(0, output.size());
        assertTrue(errors.toString(StandardCharsets.UTF_8).contains("missing.json"));
    }

    private Path writeConfiguration(String json) throws IOException {
        return Files.writeString(directory.resolve("configuration.json"), json, StandardCharsets.UTF_8);
    }

    private int run(Path configuration, String input) {
        return Main.run(new String[]{configuration.toString(), input},
                new PrintStream(output, true, StandardCharsets.UTF_8),
                new PrintStream(errors, true, StandardCharsets.UTF_8), clock);
    }
}
