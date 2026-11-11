package software.ulpgc.attendance.employeeadmin.infrastructure.adapters.input;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

record EmployeeAdminResources(String layout, String row, String deactivateForm, String styles, String script) {
    static EmployeeAdminResources load() throws IOException {
        return new EmployeeAdminResources(read("index.html"), read("employee-row.html"),
                read("deactivate-form.html"), read("styles.css"), read("filter.js"));
    }

    private static String read(String name) throws IOException {
        try (var stream = EmployeeAdminResources.class.getResourceAsStream("/employee-admin/" + name)) {
            if (stream == null)
                throw new IOException("Missing employee administration resource: " + name);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
