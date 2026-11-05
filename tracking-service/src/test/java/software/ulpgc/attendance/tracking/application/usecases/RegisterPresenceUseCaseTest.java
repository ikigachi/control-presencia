package software.ulpgc.attendance.tracking.application.usecases;

import org.junit.jupiter.api.Test;
import software.ulpgc.attendance.application.domain.model.TrackingEvent;
import software.ulpgc.attendance.application.domain.model.TrackingType;
import software.ulpgc.attendance.tracking.application.ports.output.EventPublisher;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RegisterPresenceUseCaseTest {
    @Test
    void publishesTheParsedEvent() throws IOException {
        TrackingEvent event = new TrackingEvent(Instant.parse("2026-11-05T09:00:00Z"),
                "employee-001", "hotel-001", TrackingType.ENTRY);
        List<TrackingEvent> published = new ArrayList<>();
        RegisterPresenceUseCase useCase = new RegisterPresenceUseCase(
                () -> "employee-001",
                input -> {
                    assertEquals("employee-001", input);
                    return event;
                },
                published::add);

        useCase.execute();

        assertEquals(List.of(event), published);
    }

    @Test
    void doesNotPublishRejectedInput() {
        List<TrackingEvent> published = new ArrayList<>();
        RegisterPresenceUseCase useCase = new RegisterPresenceUseCase(
                () -> "invalid",
                input -> { throw new IllegalArgumentException("Invalid device input"); },
                published::add);

        assertThrows(IllegalArgumentException.class, useCase::execute);
        assertTrue(published.isEmpty());
    }

    @Test
    void doesNotPublishWhenDeviceReadingFails() {
        List<TrackingEvent> published = new ArrayList<>();
        RegisterPresenceUseCase useCase = new RegisterPresenceUseCase(
                () -> { throw new IOException("Device unavailable"); },
                input -> { throw new AssertionError("Parser must not run after a read failure"); },
                published::add);

        assertThrows(IOException.class, useCase::execute);
        assertTrue(published.isEmpty());
    }

    @Test
    void reportsPublicationFailure() {
        TrackingEvent event = new TrackingEvent(Instant.parse("2026-11-05T09:00:00Z"),
                "employee-001", "hotel-001", TrackingType.ENTRY);
        EventPublisher publisher = value -> { throw new IOException("Publisher unavailable"); };
        RegisterPresenceUseCase useCase = new RegisterPresenceUseCase(
                () -> "employee-001", input -> event, publisher);

        IOException error = assertThrows(IOException.class, useCase::execute);
        assertEquals("Publisher unavailable", error.getMessage());
    }
}
