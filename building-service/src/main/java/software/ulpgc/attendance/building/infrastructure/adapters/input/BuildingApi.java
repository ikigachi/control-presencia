package software.ulpgc.attendance.building.infrastructure.adapters.input;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.Strictness;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.UnauthorizedResponse;
import software.ulpgc.attendance.application.domain.model.Building;
import software.ulpgc.attendance.building.application.domain.model.BuildingRegistration;
import software.ulpgc.attendance.building.application.usecases.BuildingDirectory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public class BuildingApi {
    private final Gson json = new GsonBuilder().setStrictness(Strictness.STRICT).serializeNulls().create();
    private final BuildingDirectory buildings;
    private final byte[] token;

    public BuildingApi(BuildingDirectory buildings, String token) {
        if (token == null || token.isBlank() || token.chars().anyMatch(Character::isWhitespace))
            throw new IllegalArgumentException("ATTENDANCE_ADMIN_TOKEN must be set and contain no whitespace");
        this.buildings = buildings;
        this.token = token.getBytes(StandardCharsets.UTF_8);
    }

    public Javalin createServer() {
        return Javalin.create(config -> {
            config.http.maxRequestSize = 16_384;
            config.routes.before(this::requireToken);
            config.routes.post("/api/buildings", this::register);
            config.routes.get("/api/buildings", ctx ->
                    respond(ctx, 200, buildings.list().stream().map(BuildingResponse::from).toList()));
            config.routes.get("/api/buildings/{id}", ctx ->
                    respond(ctx, 200, BuildingResponse.from(buildings.find(ctx.pathParam("id")))));
            config.routes.post("/api/buildings/{id}/deactivate", ctx ->
                    respond(ctx, 200, BuildingResponse.from(buildings.deactivate(ctx.pathParam("id")))));

            config.routes.exception(UnauthorizedResponse.class, (error, ctx) -> {
                ctx.header("WWW-Authenticate", "Bearer");
                respond(ctx, 401, new ErrorResponse("unauthorized", "A valid bearer token is required"));
            });
            config.routes.exception(JsonParseException.class, (error, ctx) ->
                    respond(ctx, 400, new ErrorResponse("invalid_input", "Invalid building JSON")));
            config.routes.exception(IllegalArgumentException.class, (error, ctx) ->
                    respond(ctx, 400, new ErrorResponse("invalid_input", error.getMessage())));
            config.routes.exception(BuildingDirectory.DuplicateBuildingException.class, (error, ctx) ->
                    respond(ctx, 409, new ErrorResponse("building_exists", "Building id already exists")));
            config.routes.exception(BuildingDirectory.BuildingNotFoundException.class, (error, ctx) ->
                    respond(ctx, 404, new ErrorResponse("building_not_found", "Building not found")));
            config.routes.exception(IOException.class, (error, ctx) ->
                    respond(ctx, 500, new ErrorResponse("storage_error", "Could not save building data")));
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
        BuildingRegistration registration = buildings.register(new Building(field(body, "id"), field(body, "name")));
        ctx.header("Location", "/api/buildings/" + registration.building().id());
        respond(ctx, 201, BuildingResponse.from(registration));
    }

    private static String field(JsonObject body, String name) {
        JsonElement value = body == null ? null : body.get(name);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString())
            throw new IllegalArgumentException("Building field '" + name + "' must be a string");
        return value.getAsString();
    }

    private void respond(Context ctx, int status, Object body) {
        ctx.status(status).contentType("application/json").result(json.toJson(body));
    }

    private record ErrorResponse(String code, String message) {
    }

    private record BuildingResponse(String id, String name, boolean active, String registeredAt, String deactivatedAt) {
        private static BuildingResponse from(BuildingRegistration registration) {
            return new BuildingResponse(registration.building().id(), registration.building().name(),
                    registration.active(), registration.registeredAt().toString(),
                    registration.active() ? null : registration.deactivatedAt().toString());
        }
    }
}
