package software.ulpgc.attendance.employeeadmin.infrastructure.adapters.input;

import org.junit.jupiter.api.Test;
import software.ulpgc.attendance.employeeadmin.application.EmployeeClient;
import software.ulpgc.attendance.employeeadmin.application.EmployeeRequestException;
import software.ulpgc.attendance.employeeadmin.application.EmployeeView;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static software.ulpgc.attendance.employeeadmin.infrastructure.adapters.input.EmployeeAdminController.Action.REGISTER;

class EmployeeAdminControllerTest {
    private static final String TOKEN = "test-form-token";
    private static final EmployeeForm FORM = new EmployeeForm(TOKEN, "employee-002", "Test & Employee");

    @Test
    void explainsFailuresWithoutMessagesAndKeepsTheRegistrationDraft() throws IOException {
        var employees = new FailingClient(new IOException());
        var response = controller(employees).submit(FORM, REGISTER);

        assertEquals(502, response.status());
        assertNull(response.location());
        assertTrue(response.html().contains("Could not complete the employee request. Please try again."));
        assertTrue(response.html().contains("value=\"employee-002\""));
        assertTrue(response.html().contains("value=\"Test &amp; Employee\""));
    }

    @Test
    void preservesTheConflictWhenReloadingTheDirectoryAlsoFails() throws IOException {
        var employees = new FailingClient(new EmployeeRequestException(
                EmployeeRequestException.Reason.CONFLICT, "Employee id already exists"));
        employees.directoryUnavailable = true;

        var response = controller(employees).submit(FORM, REGISTER);

        assertEquals(409, response.status());
        assertTrue(response.html().contains("Employee id already exists"));
        assertTrue(response.html().contains("<dd>—</dd>"));
        assertTrue(response.html().contains("value=\"Test &amp; Employee\""));
    }

    @Test
    void preservesInterruptionWithoutStartingAnotherRequest() throws IOException {
        var employees = new FailingClient(null);
        try {
            var response = controller(employees).submit(FORM, REGISTER);

            assertEquals(503, response.status());
            assertTrue(Thread.currentThread().isInterrupted());
            assertEquals(0, employees.listCalls);
            assertTrue(response.html().contains("value=\"employee-002\""));
        } finally {
            Thread.interrupted();
        }
    }

    private static EmployeeAdminController controller(EmployeeClient employees) throws IOException {
        return new EmployeeAdminController(employees, new EmployeeAdminPage(EmployeeAdminResources.load()), TOKEN);
    }

    private static final class FailingClient implements EmployeeClient {
        private final IOException failure;
        private boolean directoryUnavailable;
        private int listCalls;

        private FailingClient(IOException failure) {
            this.failure = failure;
        }

        @Override
        public List<EmployeeView> list() throws IOException {
            listCalls++;
            if (directoryUnavailable)
                throw new IOException("Directory unavailable");
            return List.of();
        }

        @Override
        public EmployeeView register(String id, String name) throws IOException, InterruptedException {
            if (failure == null)
                throw new InterruptedException();
            throw failure;
        }

        @Override
        public EmployeeView deactivate(String id) {
            throw new AssertionError("Deactivation was not requested");
        }
    }
}
