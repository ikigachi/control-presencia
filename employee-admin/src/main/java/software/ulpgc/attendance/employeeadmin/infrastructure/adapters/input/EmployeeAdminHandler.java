package software.ulpgc.attendance.employeeadmin.infrastructure.adapters.input;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import software.ulpgc.attendance.employeeadmin.infrastructure.adapters.input.EmployeeAdminController.Action;
import software.ulpgc.attendance.employeeadmin.infrastructure.adapters.input.EmployeeAdminController.Response;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

final class EmployeeAdminHandler implements HttpHandler {
    private static final String HTML = "text/html; charset=UTF-8";
    private static final String TEXT = "text/plain; charset=UTF-8";
    private final EmployeeAdminController controller;
    private final EmployeeAdminResources resources;

    EmployeeAdminHandler(EmployeeAdminController controller, EmployeeAdminResources resources) {
        this.controller = controller;
        this.resources = resources;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            exchange.getResponseHeaders().set("Cache-Control", "no-store");
            exchange.getResponseHeaders().set("Content-Security-Policy",
                    "default-src 'self'; base-uri 'none'; frame-ancestors 'none'; form-action 'self'");
            exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");

            switch (exchange.getRequestURI().getPath()) {
                case "/" -> {
                    if ("GET".equals(exchange.getRequestMethod()))
                        send(exchange, controller.directory(notice(exchange)));
                    else
                        rejectMethod(exchange, "GET");
                }
                case "/styles.css" -> serveAsset(exchange, "text/css; charset=UTF-8", resources.styles());
                case "/filter.js" -> serveAsset(exchange, "text/javascript; charset=UTF-8", resources.script());
                case "/employees/register" -> submit(exchange, Action.REGISTER);
                case "/employees/deactivate" -> submit(exchange, Action.DEACTIVATE);
                default -> send(exchange, 404, TEXT, "Page not found.");
            }
        }
    }

    private void submit(HttpExchange exchange, Action action) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            rejectMethod(exchange, "POST");
            return;
        }
        EmployeeForm form;
        try {
            form = EmployeeForm.read(exchange);
        } catch (IllegalArgumentException e) {
            send(exchange, controller.invalidSubmission());
            return;
        }
        send(exchange, controller.submit(form, action));
    }

    private static void serveAsset(HttpExchange exchange, String contentType, String contents) throws IOException {
        if ("GET".equals(exchange.getRequestMethod()))
            send(exchange, 200, contentType, contents);
        else
            rejectMethod(exchange, "GET");
    }

    private static String notice(HttpExchange exchange) {
        return switch (String.valueOf(exchange.getRequestURI().getRawQuery())) {
            case "notice=registered" -> "Employee registered.";
            case "notice=deactivated" -> "Employee deactivated.";
            default -> "";
        };
    }

    private static void rejectMethod(HttpExchange exchange, String allowed) throws IOException {
        exchange.getResponseHeaders().set("Allow", allowed);
        send(exchange, 405, TEXT, "Use " + allowed + " for this resource.");
    }

    private static void send(HttpExchange exchange, Response response) throws IOException {
        if (response.location() != null) {
            exchange.getResponseHeaders().set("Location", response.location());
            exchange.sendResponseHeaders(response.status(), -1);
        } else {
            send(exchange, response.status(), HTML, response.html());
        }
    }

    private static void send(HttpExchange exchange, int status, String contentType, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
    }
}
