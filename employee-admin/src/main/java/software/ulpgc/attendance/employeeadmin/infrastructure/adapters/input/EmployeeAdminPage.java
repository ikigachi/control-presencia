package software.ulpgc.attendance.employeeadmin.infrastructure.adapters.input;

import software.ulpgc.attendance.employeeadmin.application.EmployeeView;

import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class EmployeeAdminPage {
    private static final Pattern SLOT = Pattern.compile("\\{\\{([a-zA-Z]+)}}");
    private final String layout;
    private final String row;
    private final String deactivateForm;

    EmployeeAdminPage(EmployeeAdminResources resources) {
        layout = resources.layout();
        row = resources.row();
        deactivateForm = resources.deactivateForm();
    }

    String render(List<EmployeeView> records, String formToken, String notice, String error, RegistrationDraft draft) {
        return render(records, formToken, notice, error, true, draft);
    }

    String renderUnavailable(String formToken, String notice, String error, RegistrationDraft draft) {
        return render(List.of(), formToken, notice,
                error == null || error.isBlank() ? "The employee directory is unavailable." : error, false, draft);
    }

    private String render(List<EmployeeView> records, String formToken, String notice, String error, boolean available,
                          RegistrationDraft draft) {
        StringBuilder rows = new StringBuilder();
        int active = 0;
        for (EmployeeView employee : records) {
            String action = "";
            if (employee.active()) {
                active++;
                action = fill(deactivateForm, Map.of("id", escape(employee.id()), "formToken", formToken));
            }
            rows.append(fill(row, Map.of(
                    "id", escape(employee.id()),
                    "name", escape(employee.name()),
                    "state", employee.active() ? "active" : "inactive",
                    "status", employee.active() ? "Active" : "Inactive",
                    "registered", escape(employee.registeredAt()),
                    "deactivated", employee.deactivatedAt() == null ? "—" : escape(employee.deactivatedAt()),
                    "action", action)));
        }
        boolean hasError = error != null && !error.isEmpty();
        var values = new HashMap<>(Map.of(
                "formToken", formToken,
                "rows", rows.toString(),
                "total", available ? Integer.toString(records.size()) : "—",
                "active", available ? Integer.toString(active) : "—",
                "inactive", available ? Integer.toString(records.size() - active) : "—",
                "notice", escape(notice),
                "noticeHidden", notice.isEmpty() ? "hidden" : "",
                "error", hasError ? escape(error) : "",
                "errorHidden", hasError ? "" : "hidden",
                "emptyHidden", available && records.isEmpty() ? "" : "hidden"));
        values.put("draftId", escape(draft.id()));
        values.put("draftName", escape(draft.name()));
        return fill(layout, values);
    }

    private static String fill(String template, Map<String, String> values) {
        return SLOT.matcher(template).replaceAll(match -> Matcher.quoteReplacement(values.get(match.group(1))));
    }

    private static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }

    record RegistrationDraft(String id, String name) {
        static final RegistrationDraft EMPTY = new RegistrationDraft("", "");
    }
}
