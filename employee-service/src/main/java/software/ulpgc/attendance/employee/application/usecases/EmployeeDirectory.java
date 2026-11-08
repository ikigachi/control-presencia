package software.ulpgc.attendance.employee.application.usecases;

import software.ulpgc.attendance.application.domain.model.Employee;
import software.ulpgc.attendance.employee.application.domain.model.EmployeeRegistration;
import software.ulpgc.attendance.employee.application.ports.output.EmployeeRepository;

import java.io.IOException;
import java.time.Clock;
import java.util.List;

public class EmployeeDirectory {
    private final EmployeeRepository repository;
    private final Clock clock;

    public EmployeeDirectory(EmployeeRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public synchronized EmployeeRegistration register(Employee employee) throws IOException {
        EmployeeRegistration registration = new EmployeeRegistration(employee, clock.instant(), null);
        if (repository.findById(employee.id()).isPresent())
            throw new DuplicateEmployeeException();

        repository.save(registration);
        return registration;
    }

    public synchronized EmployeeRegistration find(String id) {
        return repository.findById(id).orElseThrow(EmployeeNotFoundException::new);
    }

    public synchronized List<EmployeeRegistration> list() {
        return repository.findAll();
    }

    public synchronized EmployeeRegistration deactivate(String id) throws IOException {
        EmployeeRegistration current = find(id);
        if (!current.active())
            return current;

        EmployeeRegistration deactivated = current.deactivate(clock.instant());
        repository.save(deactivated);
        return deactivated;
    }

    public static class DuplicateEmployeeException extends RuntimeException {
    }

    public static class EmployeeNotFoundException extends RuntimeException {
    }
}
