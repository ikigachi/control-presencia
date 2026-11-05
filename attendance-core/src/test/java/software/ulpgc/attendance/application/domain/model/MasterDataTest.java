package software.ulpgc.attendance.application.domain.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class MasterDataTest {
    @Test
    void representsAnEmployeeAndAHotelBuilding() {
        Employee employee = new Employee("employee-001", "Test Employee");
        Building building = new Building("hotel-001", "Test Hotel");

        assertEquals("employee-001", employee.id());
        assertEquals("Test Employee", employee.name());
        assertEquals("hotel-001", building.id());
        assertEquals("Test Hotel", building.name());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void rejectsIncompleteMasterData(String missing) {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class, () -> new Employee(missing, "Test Employee")),
                () -> assertThrows(IllegalArgumentException.class, () -> new Employee("employee-001", missing)),
                () -> assertThrows(IllegalArgumentException.class, () -> new Building(missing, "Test Hotel")),
                () -> assertThrows(IllegalArgumentException.class, () -> new Building("hotel-001", missing))
        );
    }
}
