package software.ulpgc.attendance.employeeadmin.infrastructure.adapters.output;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.Strictness;
import software.ulpgc.attendance.employeeadmin.application.EmployeeClient;
import software.ulpgc.attendance.employeeadmin.application.EmployeeRequestException;
import software.ulpgc.attendance.employeeadmin.application.EmployeeView;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public class HttpEmployeeClient implements EmployeeClient {
    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private final Gson json = new GsonBuilder().setStrictness(Strictness.STRICT).create();
    private final HttpClient http;
    private final URI employees;
    private final String token;

    public HttpEmployeeClient(HttpClient http, URI serviceUrl, String token) {
        String scheme = serviceUrl.getScheme();
        if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))
                || serviceUrl.getHost() == null || serviceUrl.getUserInfo() != null
                || serviceUrl.getRawQuery() != null || serviceUrl.getRawFragment() != null)
            throw new IllegalArgumentException("Employee service URL must be an HTTP or HTTPS base URL");
        if (token == null || token.isBlank() || token.chars().anyMatch(Character::isWhitespace))
            throw new IllegalArgumentException("ATTENDANCE_ADMIN_TOKEN must be set and contain no whitespace");

        String baseUrl = serviceUrl.toASCIIString();
        this.employees = URI.create(baseUrl + (baseUrl.endsWith("/") ? "" : "/") + "api/employees");
        this.http = http;
        this.token = token;
    }

    @Override
    public List<EmployeeView> list() throws IOException, InterruptedException {
        JsonElement response = send(request(employees).GET().build(), 200);
        if (!response.isJsonArray())
            throw new IOException("Employee service did not return a list");

        List<EmployeeView> records = new ArrayList<>();
        for (JsonElement item : response.getAsJsonArray())
            records.add(employee(item));
        return List.copyOf(records);
    }

    @Override
    public EmployeeView register(String id, String name) throws IOException, InterruptedException {
        JsonObject body = new JsonObject();
        body.addProperty("id", id);
        body.addProperty("name", name);
        HttpRequest request = request(employees).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.toJson(body))).build();
        return employee(send(request, 201));
    }

    @Override
    public EmployeeView deactivate(String id) throws IOException, InterruptedException {
        String encodedId = URLEncoder.encode(id, StandardCharsets.UTF_8).replace("+", "%20");
        URI uri = URI.create(employees + "/" + encodedId + "/deactivate");
        return employee(send(request(uri).POST(HttpRequest.BodyPublishers.noBody()).build(), 200));
    }

    private HttpRequest.Builder request(URI uri) {
        return HttpRequest.newBuilder(uri).timeout(TIMEOUT)
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/json");
    }

    private JsonElement send(HttpRequest request, int expectedStatus) throws IOException, InterruptedException {
        HttpResponse<String> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IOException("Could not reach the employee service. Check its URL and that it is running.", e);
        }
        if (response.statusCode() != expectedStatus) {
            var reason = switch (response.statusCode()) {
                case 400 -> EmployeeRequestException.Reason.INVALID_INPUT;
                case 404 -> EmployeeRequestException.Reason.NOT_FOUND;
                case 409 -> EmployeeRequestException.Reason.CONFLICT;
                default -> EmployeeRequestException.Reason.UNAVAILABLE;
            };
            throw new EmployeeRequestException(reason, errorMessage(response));
        }

        try {
            JsonElement body = json.fromJson(response.body(), JsonElement.class);
            if (body == null || body.isJsonNull())
                throw new IOException("Employee service returned an empty response");
            return body;
        } catch (JsonParseException e) {
            throw new IOException("Employee service returned invalid JSON", e);
        }
    }

    private String errorMessage(HttpResponse<String> response) {
        if (response.statusCode() == 401)
            return "The employee API rejected the token. Check ATTENDANCE_ADMIN_TOKEN.";
        try {
            JsonObject body = json.fromJson(response.body(), JsonObject.class);
            return text(body, "message") + " (HTTP " + response.statusCode() + ")";
        } catch (JsonParseException | IOException e) {
            return "Employee service returned HTTP " + response.statusCode();
        }
    }

    private static EmployeeView employee(JsonElement value) throws IOException {
        if (!value.isJsonObject())
            throw new IOException("Employee service returned an invalid employee record");
        JsonObject record = value.getAsJsonObject();
        JsonElement active = record.get("active");
        if (active == null || !active.isJsonPrimitive() || !active.getAsJsonPrimitive().isBoolean())
            throw new IOException("Employee service returned an invalid employee status");
        JsonElement deactivatedAt = record.get("deactivatedAt");
        return new EmployeeView(text(record, "id"), text(record, "name"), active.getAsBoolean(),
                text(record, "registeredAt"), deactivatedAt == null || deactivatedAt.isJsonNull()
                        ? null : text(record, "deactivatedAt"));
    }

    private static String text(JsonObject record, String field) throws IOException {
        JsonElement value = record == null ? null : record.get(field);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString())
            throw new IOException("Employee service returned an invalid '" + field + "' field");
        return value.getAsString();
    }
}
