package software.ulpgc.attendance.employeeadmin.infrastructure.adapters.input;

import software.ulpgc.attendance.employeeadmin.application.EmployeeClient;
import software.ulpgc.attendance.employeeadmin.application.EmployeeRequestException;
import software.ulpgc.attendance.employeeadmin.application.EmployeeView;
import software.ulpgc.attendance.employeeadmin.infrastructure.adapters.input.EmployeeAdminPage.RegistrationDraft;

import java.io.IOException;
import java.util.List;

final class EmployeeAdminController {
    enum Action { REGISTER, DEACTIVATE }

    private final EmployeeClient employees;
    private final EmployeeAdminPage page;
    private final String formToken;

    EmployeeAdminController(EmployeeClient employees, EmployeeAdminPage page, String formToken) {
        this.employees = employees;
        this.page = page;
        this.formToken = formToken;
    }

    Response directory(String notice) {
        return renderDirectory(200, notice, "", RegistrationDraft.EMPTY);
    }

    Response invalidSubmission() {
        return renderDirectory(400, "", "Invalid form submission.", RegistrationDraft.EMPTY);
    }

    Response submit(EmployeeForm form, Action action) {
        if (!formToken.equals(form.token()))
            return renderDirectory(403, "", "This form has expired. Reload the page and try again.", RegistrationDraft.EMPTY);

        RegistrationDraft draft = action == Action.REGISTER
                ? new RegistrationDraft(form.id(), form.name()) : RegistrationDraft.EMPTY;
        if (form.id().isBlank() || (action == Action.REGISTER && form.name().isBlank()))
            return renderDirectory(400, "", action == Action.REGISTER
                    ? "Enter an employee ID and a name." : "Select an employee.", draft);

        try {
            switch (action) {
                case REGISTER -> employees.register(form.id(), form.name());
                case DEACTIVATE -> employees.deactivate(form.id());
            }
        } catch (EmployeeRequestException e) {
            int status = switch (e.reason()) {
                case INVALID_INPUT -> 400;
                case NOT_FOUND -> 404;
                case CONFLICT -> 409;
                case UNAVAILABLE -> 502;
            };
            return renderDirectory(status, "", message(e), draft);
        } catch (IOException e) {
            return renderDirectory(502, "", message(e), draft);
        } catch (InterruptedException e) {
            return interrupted(draft);
        }

        String location = action == Action.REGISTER ? "/?notice=registered" : "/?notice=deactivated";
        return new Response(303, "", location);
    }

    private Response renderDirectory(int status, String notice, String error, RegistrationDraft draft) {
        List<EmployeeView> records;
        try {
            records = employees.list();
        } catch (IOException e) {
            String problem = error.isEmpty() ? message(e) : error;
            return new Response(status == 200 ? 502 : status,
                    page.renderUnavailable(formToken, notice, problem, draft), null);
        } catch (InterruptedException e) {
            return interrupted(draft);
        }
        return new Response(status, page.render(records, formToken, notice, error, draft), null);
    }

    private Response interrupted(RegistrationDraft draft) {
        Thread.currentThread().interrupt();
        return new Response(503, page.renderUnavailable(formToken, "", "Request interrupted.", draft), null);
    }

    private static String message(IOException error) {
        return error.getMessage() == null || error.getMessage().isBlank()
                ? "Could not complete the employee request. Please try again." : error.getMessage();
    }

    record Response(int status, String html, String location) {
    }
}
