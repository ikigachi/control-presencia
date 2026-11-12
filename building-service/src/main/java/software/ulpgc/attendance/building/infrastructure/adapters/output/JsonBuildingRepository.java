package software.ulpgc.attendance.building.infrastructure.adapters.output;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.Strictness;
import software.ulpgc.attendance.application.domain.model.Building;
import software.ulpgc.attendance.building.application.domain.model.BuildingRegistration;
import software.ulpgc.attendance.building.application.ports.output.BuildingRepository;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static java.nio.file.StandardCopyOption.ATOMIC_MOVE;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

public class JsonBuildingRepository implements BuildingRepository {
    private final Gson json = new GsonBuilder().setStrictness(Strictness.STRICT).setPrettyPrinting().create();
    private final Path file;
    private Map<String, BuildingRegistration> registrations;

    public JsonBuildingRepository(Path file) throws IOException {
        this.file = file.toAbsolutePath().normalize();
        if (this.file.getFileName() == null)
            throw new IllegalArgumentException("Building data path must name a file");
        registrations = load();
    }

    @Override
    public Optional<BuildingRegistration> findById(String id) {
        return Optional.ofNullable(registrations.get(id));
    }

    @Override
    public List<BuildingRegistration> findAll() {
        return List.copyOf(registrations.values());
    }

    @Override
    public void save(BuildingRegistration registration) throws IOException {
        Map<String, BuildingRegistration> updated = new LinkedHashMap<>(registrations);
        updated.put(registration.building().id(), registration);
        String snapshot = json.toJson(updated.values().stream().map(StoredBuilding::from).toList());

        Path directory = file.getParent();
        Files.createDirectories(directory);
        Path temporary = Files.createTempFile(directory, ".buildings-", ".tmp");
        try {
            Files.writeString(temporary, snapshot, StandardCharsets.UTF_8);
            Files.move(temporary, file, ATOMIC_MOVE, REPLACE_EXISTING);
        } catch (IOException failure) {
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            throw failure;
        }
        registrations = updated;
    }

    private Map<String, BuildingRegistration> load() throws IOException {
        Map<String, BuildingRegistration> restored = new LinkedHashMap<>();
        if (Files.notExists(file))
            return restored;

        String contents = Files.readString(file, StandardCharsets.UTF_8);
        try {
            JsonElement stored = json.fromJson(contents, JsonElement.class);
            if (stored == null || !stored.isJsonArray())
                throw new IllegalArgumentException("Expected an array of buildings");

            for (JsonElement entry : stored.getAsJsonArray()) {
                BuildingRegistration registration = StoredBuilding.parse(entry).toRegistration();
                if (restored.putIfAbsent(registration.building().id(), registration) != null)
                    throw new IllegalArgumentException("Building data contains a duplicate identifier");
            }
            return restored;
        } catch (JsonParseException | DateTimeParseException | IllegalArgumentException e) {
            throw new IOException("Invalid building data in " + file, e);
        }
    }

    private record StoredBuilding(String id, String name, String registeredAt, String deactivatedAt) {
        private static StoredBuilding parse(JsonElement entry) {
            if (!entry.isJsonObject())
                throw new IllegalArgumentException("Expected an building object");
            JsonObject record = entry.getAsJsonObject();
            JsonElement deactivatedAt = record.get("deactivatedAt");
            return new StoredBuilding(text(record, "id"), text(record, "name"), text(record, "registeredAt"),
                    deactivatedAt == null || deactivatedAt.isJsonNull() ? null : text(record, "deactivatedAt"));
        }

        private static String text(JsonObject record, String field) {
            JsonElement value = record.get(field);
            if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString())
                throw new IllegalArgumentException("Stored building field '" + field + "' must be a string");
            return value.getAsString();
        }

        private static StoredBuilding from(BuildingRegistration registration) {
            return new StoredBuilding(registration.building().id(), registration.building().name(),
                    registration.registeredAt().toString(),
                    registration.active() ? null : registration.deactivatedAt().toString());
        }

        private BuildingRegistration toRegistration() {
            return new BuildingRegistration(new Building(id, name), Instant.parse(registeredAt),
                    deactivatedAt == null ? null : Instant.parse(deactivatedAt));
        }
    }
}
