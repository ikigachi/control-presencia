package software.ulpgc.attendance.employeeadmin.infrastructure;

import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import software.ulpgc.attendance.employeeadmin.application.EmployeeClient;
import software.ulpgc.attendance.employeeadmin.application.EmployeeView;
import software.ulpgc.attendance.employeeadmin.infrastructure.adapters.input.EmployeeAdminServer;
import software.ulpgc.attendance.employeeadmin.infrastructure.adapters.output.HttpEmployeeClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

class EmployeeAdminTest {
    private static final String EMPLOYEES = "/hotel/api/employees";
    private static final String API_TOKEN = "test-upstream-token";
    private static final String WEB_PASSWORD = "test-web-password";
    private static final String ACTIVE = """
            {"id":"employee-001","name":"<script>alert(1)</script>","active":true,"registeredAt":"2026-11-09T09:00:00Z","deactivatedAt":null}
            """.strip();
    private static final String INACTIVE = ACTIVE.replace("\"active\":true", "\"active\":false")
            .replace("\"deactivatedAt\":null", "\"deactivatedAt\":\"2026-11-09T10:00:00Z\"");
    private static final Pattern FORM_TOKEN = Pattern.compile("name=\"formToken\" value=\"([^\"]+)\"");

    @Test
    void servesFormsThatRegisterAndDeactivateWithoutJavaScript() throws Exception {
        var employee = new AtomicReference<String>();
        var requests = new AtomicInteger();
        HttpServer api = startApi(employee, requests);
        try (HttpClient http = HttpClient.newHttpClient();
             EmployeeAdminServer admin = startAdmin(http, api, API_TOKEN)) {
            var initial = request(http, admin, "/", null, true);
            assertEquals(200, initial.statusCode());
            assertTrue(initial.body().contains("No employees to display."));
            String formToken = extractFormToken(initial.body());

            var registered = request(http, admin, "/employees/register", "formToken=" + formToken
                    + "&id=employee-001&name=" + encode("<script>alert(1)</script>"), true);
            assertEquals(303, registered.statusCode());
            String location = registered.headers().firstValue("Location").orElseThrow();
            var directory = request(http, admin, location, null, true);
            assertTrue(directory.body().contains("Employee registered."));
            assertTrue(directory.body().contains("&lt;script&gt;alert(1)&lt;/script&gt;"));
            assertFalse(directory.body().contains("<script>alert(1)</script>"));
            assertFalse(directory.body().contains(API_TOKEN));
            assertTrue(directory.body().contains("action=\"/employees/deactivate\""));
            assertTrue(directory.body().contains("name=\"name\" placeholder=\"Example Employee\" value=\"\""));

            var duplicate = request(http, admin, "/employees/register", "formToken=" + formToken
                    + "&id=employee-001&name=" + encode("<script>alert(1)</script>"), true);
            assertEquals(409, duplicate.statusCode());
            assertTrue(duplicate.body().contains("Employee id already exists"));
            assertTrue(duplicate.body().contains(">Active</span>"));
            assertTrue(duplicate.body().contains("name=\"id\" placeholder=\"employee-001\" value=\"employee-001\""));
            assertTrue(duplicate.body().contains("value=\"&lt;script&gt;alert(1)&lt;/script&gt;\""));
            assertFalse(duplicate.body().contains("<script>alert(1)</script>"));
            assertEquals(1, requests.get());

            var deactivated = request(http, admin, "/employees/deactivate",
                    "formToken=" + formToken + "&id=employee-001", true);
            assertEquals(303, deactivated.statusCode());
            directory = request(http, admin, deactivated.headers().firstValue("Location").orElseThrow(), null, true);
            assertTrue(directory.body().contains("Employee deactivated."));
            assertTrue(directory.body().contains("employee-001"));
            assertTrue(directory.body().contains(">Inactive</span>"));
            assertFalse(directory.body().contains("action=\"/employees/deactivate\""));
            assertEquals(2, requests.get());
        } finally {
            api.stop(0);
        }
    }

    @Test
    void rejectsUnauthenticatedOrForgedRequestsWithoutChangingEmployees() throws Exception {
        var employee = new AtomicReference<String>();
        var requests = new AtomicInteger();
        HttpServer api = startApi(employee, requests);
        try (HttpClient http = HttpClient.newHttpClient();
             EmployeeAdminServer admin = startAdmin(http, api, API_TOKEN)) {
            var unauthorized = request(http, admin, "/", null, false);
            assertEquals(401, unauthorized.statusCode());
            assertTrue(unauthorized.headers().firstValue("WWW-Authenticate").orElseThrow().startsWith("Basic "));
            assertEquals(401, request(http, admin, "/employees/register", "id=employee-001&name=Test", false).statusCode());
            assertEquals(403, request(http, admin, "/employees/register",
                    "formToken=invalid&id=employee-001&name=Test", true).statusCode());
            var page = request(http, admin, "/", null, true);
            String token = extractFormToken(page.body());
            assertEquals(400, request(http, admin, "/employees/register",
                    "formToken=" + token + "&id=employee-001&id=employee-002&name=Test", true).statusCode());
            assertEquals(405, request(http, admin, "/employees/deactivate", null, true).statusCode());
            assertEquals(0, requests.get());
            assertNull(employee.get());

            var script = request(http, admin, "/filter.js", null, true);
            assertEquals(200, script.statusCode());
            assertTrue(script.headers().firstValue("Content-Type").orElseThrow().startsWith("text/javascript"));
            assertFalse(script.body().contains(API_TOKEN));
            var styles = request(http, admin, "/styles.css", null, true);
            assertEquals(200, styles.statusCode());
            assertTrue(styles.headers().firstValue("Content-Type").orElseThrow().startsWith("text/css"));
        } finally {
            api.stop(0);
        }
    }

    @Test
    void showsAnApiCredentialErrorInTheWebPage() throws Exception {
        HttpServer api = startApi(new AtomicReference<>(), new AtomicInteger());
        try (HttpClient http = HttpClient.newHttpClient();
             EmployeeAdminServer admin = startAdmin(http, api, "wrong-upstream-token")) {
            var result = request(http, admin, "/", null, true);
            assertEquals(502, result.statusCode());
            assertTrue(result.body().contains("The employee API rejected the token."));
            assertFalse(result.body().contains("wrong-upstream-token"));
            assertTrue(result.body().contains("<dd>—</dd>"));
            assertTrue(result.body().contains("<p class=\"empty\" hidden>No employees to display."));

            var registration = request(http, admin, "/employees/register",
                    "formToken=" + extractFormToken(result.body()) + "&id=employee-002&name=" + encode("Test & Employee"), true);
            assertEquals(502, registration.statusCode());
            assertTrue(registration.body().contains("name=\"id\" placeholder=\"employee-001\" value=\"employee-002\""));
            assertTrue(registration.body().contains("value=\"Test &amp; Employee\""));
        } finally {
            api.stop(0);
        }
    }

    private static EmployeeAdminServer startAdmin(HttpClient http, HttpServer api, String apiToken) throws IOException {
        URI url = URI.create("http://127.0.0.1:" + api.getAddress().getPort() + "/hotel");
        var admin = new EmployeeAdminServer(new HttpEmployeeClient(http, url, apiToken), WEB_PASSWORD, 0);
        admin.start();
        return admin;
    }

    @ParameterizedTest
    @ValueSource(strings = {"invalid JSON", "null", "{}", "[null]", "[{\"id\":17}]",
            "[{\"id\":\"employee-001\",\"name\":\"Test\",\"active\":\"true\",\"registeredAt\":\"2026-11-09T09:00:00Z\"}]"})
    void reportsInvalidServiceResponsesWithoutShowingAnEmptyDirectory(String response) throws Exception {
        HttpServer api = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        api.createContext(EMPLOYEES, exchange -> {
            try (exchange) {
                send(exchange, 200, response);
            }
        });
        api.start();
        try (HttpClient http = HttpClient.newHttpClient();
             EmployeeAdminServer admin = startAdmin(http, api, API_TOKEN)) {
            var page = request(http, admin, "/", null, true);
            assertEquals(502, page.statusCode());
            assertTrue(page.body().contains("<dd>—</dd>"));
            assertTrue(page.body().contains("<p class=\"empty\" hidden>No employees to display."));
            assertFalse(page.body().contains(API_TOKEN));
        } finally {
            api.stop(0);
        }
    }

    @Test
    void servesResourcesWhileTheDirectoryIsWaitingForTheService() throws Exception {
        var listingStarted = new CountDownLatch(1);
        var releaseListing = new CountDownLatch(1);
        EmployeeClient employees = new EmployeeClient() {
            @Override
            public List<EmployeeView> list() throws InterruptedException {
                listingStarted.countDown();
                if (!releaseListing.await(10, TimeUnit.SECONDS))
                    throw new AssertionError("Directory request was not released");
                return List.of();
            }

            @Override
            public EmployeeView register(String id, String name) {
                throw new AssertionError("Registration was not requested");
            }

            @Override
            public EmployeeView deactivate(String id) {
                throw new AssertionError("Deactivation was not requested");
            }
        };
        try (HttpClient http = HttpClient.newHttpClient();
             var admin = new EmployeeAdminServer(employees, WEB_PASSWORD, 0);
             var requests = Executors.newSingleThreadExecutor()) {
            admin.start();
            var directory = requests.submit(() -> request(http, admin, "/", null, true));
            try {
                assertTrue(listingStarted.await(3, TimeUnit.SECONDS));
                assertEquals(200, request(http, admin, "/styles.css", null, true).statusCode());
            } finally {
                releaseListing.countDown();
            }
            assertEquals(200, directory.get(5, TimeUnit.SECONDS).statusCode());
        }
    }

    private static HttpServer startApi(AtomicReference<String> employee, AtomicInteger writes) throws IOException {
        HttpServer api = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        api.createContext(EMPLOYEES, exchange -> respond(exchange, employee, writes));
        api.start();
        return api;
    }

    private static void respond(HttpExchange exchange, AtomicReference<String> employee, AtomicInteger writes)
            throws IOException {
        try (exchange) {
            String authorization = exchange.getRequestHeaders().getFirst("Authorization");
            String path = exchange.getRequestURI().getPath();
            String method = exchange.getRequestMethod();
            if (!("Bearer " + API_TOKEN).equals(authorization)) {
                send(exchange, 401, "{\"message\":\"A valid bearer token is required\"}");
            } else if ("GET".equals(method) && EMPLOYEES.equals(path)) {
                String current = employee.get();
                send(exchange, 200, current == null ? "[]" : "[" + current + "]");
            } else if ("POST".equals(method) && EMPLOYEES.equals(path)) {
                var body = JsonParser.parseString(new String(exchange.getRequestBody().readAllBytes(),
                        StandardCharsets.UTF_8)).getAsJsonObject();
                if (!"employee-001".equals(body.get("id").getAsString())
                        || !"<script>alert(1)</script>".equals(body.get("name").getAsString())) {
                    send(exchange, 400, "{\"message\":\"Unexpected registration data\"}");
                    return;
                }
                if (employee.get() != null) {
                    send(exchange, 409, "{\"message\":\"Employee id already exists\"}");
                    return;
                }
                writes.incrementAndGet();
                employee.set(ACTIVE);
                send(exchange, 201, ACTIVE);
            } else if ("POST".equals(method) && (EMPLOYEES + "/employee-001/deactivate").equals(path)) {
                writes.incrementAndGet();
                employee.set(INACTIVE);
                send(exchange, 200, INACTIVE);
            } else {
                send(exchange, 404, "{\"message\":\"Unknown route\"}");
            }
        }
    }

    private static void send(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
    }

    private static HttpResponse<String> request(HttpClient http, EmployeeAdminServer admin, String path,
                                                String form, boolean authorized) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + admin.port() + path))
                .timeout(Duration.ofSeconds(5));
        if (authorized)
            request.header("Authorization", "Basic " + Base64.getEncoder()
                    .encodeToString(("admin:" + WEB_PASSWORD).getBytes(StandardCharsets.UTF_8)));
        if (form != null)
            request.header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(form));
        else
            request.GET();
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static String extractFormToken(String page) {
        var matcher = FORM_TOKEN.matcher(page);
        assertTrue(matcher.find(), "Expected a form token");
        return matcher.group(1);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
