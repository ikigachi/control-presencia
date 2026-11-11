package software.ulpgc.attendance.employeeadmin.application;

import java.io.IOException;
import java.util.List;

public interface EmployeeClient {
    List<EmployeeView> list() throws IOException, InterruptedException;

    EmployeeView register(String id, String name) throws IOException, InterruptedException;

    EmployeeView deactivate(String id) throws IOException, InterruptedException;
}
