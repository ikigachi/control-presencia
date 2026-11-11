package software.ulpgc.attendance.employee.infrastructure.adapters.output;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.Strictness;
import software.ulpgc.attendance.application.domain.model.Employee;
import software.ulpgc.attendance.employee.application.domain.model.EmployeeRegistration;
import software.ulpgc.attendance.employee.application.ports.output.EmployeeRepository;

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

public class JsonEmployeeRepository implements EmployeeRepository {
    private final Gson json = new GsonBuilder().setStrictness(Strictness.STRICT).setPrettyPrinting().create();
    private final Path file;
    private Map<String, EmployeeRegistration> registrations;

    public JsonEmployeeRepository(Path file) throws IOException {
        this.file = file.toAbsolutePath().normalize();
        if (this.file.getFileName() == null)
            throw new IllegalArgumentException("Employee data path must name a file");
        registrations = load();
    }

    @Override
    public Optional<EmployeeRegistration> findById(String id) {
        return Optional.ofNullable(registrations.get(id));
    }

    @Override
    public List<EmployeeRegistration> findAll() {
        return List.copyOf(registrations.values());
    }

    @Override
    public void save(EmployeeRegistration registration) throws IOException {
        Map<String, EmployeeRegistration> updated = new LinkedHashMap<>(registrations);
        updated.put(registration.employee().id(), registration);
        String snapshot = json.toJson(updated.values().stream().map(StoredEmployee::from).toList());

        Path directory = file.getParent();
        Files.createDirectories(directory);
        Path temporary = Files.createTempFile(directory, ".employees-", ".tmp");
        try {
            Files.writeString(temporary, snapshot, StandardCharsets.UTF_8);
            Files.move(temporary, file, ATOMIC_MOVE, REPLACE_EXISTING);
            registrations = updated;
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private Map<String, EmployeeRegistration> load() throws IOException {
        Map<String, EmployeeRegistration> restored = new LinkedHashMap<>();
        if (Files.notExists(file))
            return restored;

        String contents = Files.readString(file, StandardCharsets.UTF_8);
        try {
            JsonElement stored = json.fromJson(contents, JsonElement.class);
            if (stored == null || !stored.isJsonArray())
                throw new IllegalArgumentException("Expected an array of employees");

            for (JsonElement entry : stored.getAsJsonArray()) {
                EmployeeRegistration registration = StoredEmployee.parse(entry).toRegistration();
                if (restored.putIfAbsent(registration.employee().id(), registration) != null)
                    throw new IllegalArgumentException("Employee data contains a duplicate identifier");
            }
            return restored;
        } catch (JsonParseException | DateTimeParseException | IllegalArgumentException e) {
            throw new IOException("Invalid employee data in " + file, e);
        }
    }

    private record StoredEmployee(String id, String name, String registeredAt, String deactivatedAt) {
        private static StoredEmployee parse(JsonElement entry) {
            if (!entry.isJsonObject())
                throw new IllegalArgumentException("Expected an employee object");
            JsonObject record = entry.getAsJsonObject();
            JsonElement deactivatedAt = record.get("deactivatedAt");
            return new StoredEmployee(text(record, "id"), text(record, "name"), text(record, "registeredAt"),
                    deactivatedAt == null || deactivatedAt.isJsonNull() ? null : text(record, "deactivatedAt"));
        }

        private static String text(JsonObject record, String field) {
            JsonElement value = record.get(field);
            if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString())
                throw new IllegalArgumentException("Stored employee field '" + field + "' must be a string");
            return value.getAsString();
        }

        private static StoredEmployee from(EmployeeRegistration registration) {
            return new StoredEmployee(registration.employee().id(), registration.employee().name(),
                    registration.registeredAt().toString(),
                    registration.active() ? null : registration.deactivatedAt().toString());
        }

        private EmployeeRegistration toRegistration() {
            if (registeredAt == null)
                throw new IllegalArgumentException("Registration timestamp is required");
            return new EmployeeRegistration(new Employee(id, name), Instant.parse(registeredAt),
                    deactivatedAt == null ? null : Instant.parse(deactivatedAt));
        }
    }
}
