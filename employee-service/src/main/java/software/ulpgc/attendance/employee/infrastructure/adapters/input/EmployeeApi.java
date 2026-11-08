package software.ulpgc.attendance.employee.infrastructure.adapters.input;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.Strictness;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.UnauthorizedResponse;
import software.ulpgc.attendance.application.domain.model.Employee;
import software.ulpgc.attendance.employee.application.domain.model.EmployeeRegistration;
import software.ulpgc.attendance.employee.application.usecases.EmployeeDirectory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public class EmployeeApi {
    private final Gson json = new GsonBuilder().setStrictness(Strictness.STRICT).serializeNulls().create();
    private final EmployeeDirectory employees;
    private final byte[] token;

    public EmployeeApi(EmployeeDirectory employees, String token) {
        if (token == null || token.isBlank() || token.chars().anyMatch(Character::isWhitespace))
            throw new IllegalArgumentException("ATTENDANCE_ADMIN_TOKEN must be set and contain no whitespace");
        this.employees = employees;
        this.token = token.getBytes(StandardCharsets.UTF_8);
    }

    public Javalin createServer() {
        return Javalin.create(config -> {
            config.http.maxRequestSize = 16_384;
            config.routes.before(this::requireToken);
            config.routes.post("/api/employees", this::register);
            config.routes.get("/api/employees", ctx ->
                    respond(ctx, 200, employees.list().stream().map(EmployeeResponse::from).toList()));
            config.routes.get("/api/employees/{id}", ctx ->
                    respond(ctx, 200, EmployeeResponse.from(employees.find(ctx.pathParam("id")))));
            config.routes.post("/api/employees/{id}/deactivate", ctx ->
                    respond(ctx, 200, EmployeeResponse.from(employees.deactivate(ctx.pathParam("id")))));

            config.routes.exception(UnauthorizedResponse.class, (error, ctx) -> {
                ctx.header("WWW-Authenticate", "Bearer");
                respond(ctx, 401, new ErrorResponse("unauthorized", "A valid bearer token is required"));
            });
            config.routes.exception(JsonParseException.class, (error, ctx) ->
                    respond(ctx, 400, new ErrorResponse("invalid_input", "Invalid employee JSON")));
            config.routes.exception(IllegalArgumentException.class, (error, ctx) ->
                    respond(ctx, 400, new ErrorResponse("invalid_input", error.getMessage())));
            config.routes.exception(EmployeeDirectory.DuplicateEmployeeException.class, (error, ctx) ->
                    respond(ctx, 409, new ErrorResponse("employee_exists", "Employee id already exists")));
            config.routes.exception(EmployeeDirectory.EmployeeNotFoundException.class, (error, ctx) ->
                    respond(ctx, 404, new ErrorResponse("employee_not_found", "Employee not found")));
            config.routes.exception(IOException.class, (error, ctx) ->
                    respond(ctx, 500, new ErrorResponse("storage_error", "Could not save employee data")));
        });
    }

    private void requireToken(Context ctx) {
        String authorization = ctx.header("Authorization");
        if (authorization == null || !authorization.regionMatches(true, 0, "Bearer ", 0, 7)
                || !MessageDigest.isEqual(token, authorization.substring(7).getBytes(StandardCharsets.UTF_8)))
            throw new UnauthorizedResponse();
    }

    private void register(Context ctx) throws IOException {
        JsonObject body = json.fromJson(ctx.body(), JsonObject.class);
        EmployeeRegistration registration = employees.register(new Employee(field(body, "id"), field(body, "name")));
        ctx.header("Location", "/api/employees/" + registration.employee().id());
        respond(ctx, 201, EmployeeResponse.from(registration));
    }

    private static String field(JsonObject body, String name) {
        JsonElement value = body == null ? null : body.get(name);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString())
            throw new IllegalArgumentException("Employee field '" + name + "' must be a string");
        return value.getAsString();
    }

    private void respond(Context ctx, int status, Object body) {
        ctx.status(status).contentType("application/json").result(json.toJson(body));
    }

    private record ErrorResponse(String code, String message) {
    }

    private record EmployeeResponse(String id, String name, boolean active, String registeredAt, String deactivatedAt) {
        private static EmployeeResponse from(EmployeeRegistration registration) {
            return new EmployeeResponse(registration.employee().id(), registration.employee().name(),
                    registration.active(), registration.registeredAt().toString(),
                    registration.active() ? null : registration.deactivatedAt().toString());
        }
    }
}
