package software.ulpgc.attendance.storer.infrastructure.adapters.output;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import software.ulpgc.attendance.application.domain.model.TrackingEvent;
import software.ulpgc.attendance.application.domain.model.TrackingType;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FileEventStoreTest {
    @TempDir
    Path directory;

    @Test
    void preservesLegacyFormatAndExistingEventsAfterReopening() throws IOException {
        Path root = directory.resolve("store");
        new FileEventStore(root.toFile()).save(new TrackingEvent(
                Instant.parse("2026-11-05T09:00:00Z"), "employee-001", "hotel-001", TrackingType.ENTRY));
        new FileEventStore(root.toFile()).save(new TrackingEvent(
                Instant.parse("2026-11-05T17:00:00Z"), "employee-001", "hotel-001", TrackingType.EXIT));

        assertEquals(List.of(
                "employee-001;hotel-001;2026-11-05T09:00:00Z;ENTRY",
                "employee-001;hotel-001;2026-11-05T17:00:00Z;EXIT"
        ), Files.readAllLines(root.resolve("2026/20261105.log"), StandardCharsets.UTF_8));
    }

    @Test
    void partitionsByUtcDateAtTheYearBoundary() throws IOException {
        new FileEventStore(directory.toFile()).save(new TrackingEvent(
                Instant.parse("2027-01-01T00:30:00+01:00"), "employee-001", "hotel-001", TrackingType.ENTRY));

        assertTrue(Files.exists(directory.resolve("2026/20261231.log")));
        assertFalse(Files.exists(directory.resolve("2027/20270101.log")));
    }

    @ParameterizedTest
    @ValueSource(strings = {";", "\r", "\n"})
    void rejectsUnsafeIdentifiersWithoutCreatingOrChangingTheLog(String separator) throws IOException {
        Path root = directory.resolve("store");
        FileEventStore store = new FileEventStore(root.toFile());
        Instant timestamp = Instant.parse("2026-11-05T09:00:00Z");
        var rejected = List.of(
                new TrackingEvent(timestamp, "employee" + separator + "001", "hotel-001", TrackingType.ENTRY),
                new TrackingEvent(timestamp, "employee-001", "hotel" + separator + "001", TrackingType.ENTRY)
        );

        for (TrackingEvent event : rejected)
            assertThrows(IllegalArgumentException.class, () -> store.save(event));
        assertFalse(Files.exists(root));

        store.save(new TrackingEvent(timestamp, "employee-001", "hotel-001", TrackingType.ENTRY));
        Path log = root.resolve("2026/20261105.log");
        byte[] original = Files.readAllBytes(log);
        for (TrackingEvent event : rejected)
            assertThrows(IllegalArgumentException.class, () -> store.save(event));
        assertArrayEquals(original, Files.readAllBytes(log));
    }

    @Test
    void rejectsAnUnsafeEventBeforeWritingToTheProvidedStream() {
        TrackingEvent event = new TrackingEvent(Instant.EPOCH, "employee-001", "hotel;west", TrackingType.ENTRY);
        var output = new ByteArrayOutputStream();

        assertThrows(IllegalArgumentException.class, () -> EventWriter.writeEvent(event, output));
        assertEquals(0, output.size());
    }

    @Test
    void reportsStorageFailureWithoutChangingTheBlockingFile() throws IOException {
        Path blocked = directory.resolve("blocked");
        Files.writeString(blocked, "Existing data", StandardCharsets.UTF_8);
        FileEventStore store = new FileEventStore(blocked.toFile());
        TrackingEvent event = new TrackingEvent(Instant.parse("2026-11-05T09:00:00Z"),
                "employee-001", "hotel-001", TrackingType.ENTRY);

        assertThrows(IOException.class, () -> store.save(event));
        assertEquals("Existing data", Files.readString(blocked, StandardCharsets.UTF_8));
    }
}
