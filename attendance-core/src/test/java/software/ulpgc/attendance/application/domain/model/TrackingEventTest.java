package software.ulpgc.attendance.application.domain.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class TrackingEventTest {
    private final Instant timestamp = Instant.parse("2026-11-05T09:00:00Z");

    @Test
    void retainsIdentifiersWithoutRequiringMasterDataObjects() {
        TrackingEvent event = new TrackingEvent(timestamp, "employee-001", "hotel-001", TrackingType.ENTRY);

        assertEquals(timestamp, event.ts());
        assertEquals("employee-001", event.employee());
        assertEquals("hotel-001", event.building());
        assertEquals(TrackingType.ENTRY, event.type());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void rejectsMissingEmployeeIdentity(String employee) {
        assertThrows(IllegalArgumentException.class,
                () -> new TrackingEvent(timestamp, employee, "hotel-001", TrackingType.ENTRY));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void rejectsMissingBuildingIdentity(String building) {
        assertThrows(IllegalArgumentException.class,
                () -> new TrackingEvent(timestamp, "employee-001", building, TrackingType.ENTRY));
    }

    @Test
    void rejectsMissingTimestamp() {
        assertThrows(IllegalArgumentException.class,
                () -> new TrackingEvent(null, "employee-001", "hotel-001", TrackingType.ENTRY));
    }

    @Test
    void rejectsMissingType() {
        assertThrows(IllegalArgumentException.class,
                () -> new TrackingEvent(timestamp, "employee-001", "hotel-001", null));
    }
}
