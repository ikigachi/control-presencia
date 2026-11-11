package software.ulpgc.attendance.employeeadmin.infrastructure;

import java.net.URI;
import java.util.function.Function;

record EmployeeAdminConfiguration(URI serviceUrl, int port, String apiToken, String webPassword) {
    static EmployeeAdminConfiguration load(String[] arguments, Function<String, String> environment) {
        if (arguments.length != 2)
            throw new IllegalArgumentException("Usage: employee-admin <employee-service-url> <port>");

        URI serviceUrl;
        try {
            serviceUrl = URI.create(arguments[0]);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid employee service URL", e);
        }

        return new EmployeeAdminConfiguration(serviceUrl, parsePort(arguments[1]),
                required(environment, "ATTENDANCE_ADMIN_TOKEN"),
                required(environment, "ATTENDANCE_ADMIN_WEB_PASSWORD"));
    }

    private static int parsePort(String value) {
        try {
            int port = Integer.parseInt(value);
            if (port >= 1 && port <= 65535)
                return port;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Port must be an integer between 1 and 65535", e);
        }
        throw new IllegalArgumentException("Port must be an integer between 1 and 65535");
    }

    private static String required(Function<String, String> environment, String name) {
        String value = environment.apply(name);
        if (value == null || value.isBlank())
            throw new IllegalArgumentException(name + " must be set");
        return value;
    }

    @Override
    public String toString() {
        return "EmployeeAdminConfiguration[port=" + port + "]";
    }
}
