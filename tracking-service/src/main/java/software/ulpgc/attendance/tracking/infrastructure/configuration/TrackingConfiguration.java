package software.ulpgc.attendance.tracking.infrastructure.configuration;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.Strictness;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public record TrackingConfiguration(String building) {
    private static final Gson JSON = new GsonBuilder().setStrictness(Strictness.STRICT).create();

    public TrackingConfiguration {
        if (building == null || building.isBlank())
            throw new IllegalArgumentException("Configuration requires a nonblank building identifier");
    }

    public static TrackingConfiguration load(Path path) throws IOException {
        String contents = Files.readString(path, StandardCharsets.UTF_8);
        try {
            JsonObject settings = JSON.fromJson(contents, JsonObject.class);
            JsonElement building = settings == null ? null : settings.get("building");
            if (building == null || !building.isJsonPrimitive() || !building.getAsJsonPrimitive().isString())
                throw new IllegalArgumentException("Configuration field 'building' must be a string");
            return new TrackingConfiguration(building.getAsString());
        } catch (JsonParseException e) {
            throw new IllegalArgumentException("Invalid tracking configuration JSON", e);
        }
    }
}
