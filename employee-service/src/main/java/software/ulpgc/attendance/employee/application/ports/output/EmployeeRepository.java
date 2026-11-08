package software.ulpgc.attendance.employee.application.ports.output;

import software.ulpgc.attendance.employee.application.domain.model.EmployeeRegistration;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

public interface EmployeeRepository {
    Optional<EmployeeRegistration> findById(String id);

    List<EmployeeRegistration> findAll();

    void save(EmployeeRegistration registration) throws IOException;
}
