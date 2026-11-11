package software.ulpgc.attendance.employeeadmin.infrastructure.adapters.input;

import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

record EmployeeForm(String token, String id, String name) {
    private static final int MAX_BYTES = 8192;

    static EmployeeForm read(HttpExchange exchange) throws IOException {
        String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
        if (contentType == null || !contentType.split(";", 2)[0].strip().equalsIgnoreCase("application/x-www-form-urlencoded"))
            throw new IllegalArgumentException("Expected a URL-encoded form");

        byte[] body = exchange.getRequestBody().readNBytes(MAX_BYTES + 1);
        if (body.length > MAX_BYTES)
            throw new IllegalArgumentException("Form is too large");

        Map<String, String> fields = decode(new String(body, StandardCharsets.UTF_8));
        return new EmployeeForm(fields.getOrDefault("formToken", ""),
                fields.getOrDefault("id", ""), fields.getOrDefault("name", ""));
    }

    private static Map<String, String> decode(String body) {
        Map<String, String> fields = new HashMap<>();
        for (String pair : body.split("&", -1)) {
            String[] parts = pair.split("=", 2);
            if (parts.length != 2)
                throw new IllegalArgumentException("Invalid form field");
            String name = URLDecoder.decode(parts[0], StandardCharsets.UTF_8);
            String value = URLDecoder.decode(parts[1], StandardCharsets.UTF_8);
            if (fields.putIfAbsent(name, value) != null)
                throw new IllegalArgumentException("Duplicate form field");
        }
        return fields;
    }
}
