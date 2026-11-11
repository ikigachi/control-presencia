package software.ulpgc.attendance.employeeadmin.infrastructure.adapters.input;

import com.sun.net.httpserver.BasicAuthenticator;
import com.sun.net.httpserver.HttpServer;
import software.ulpgc.attendance.employeeadmin.application.EmployeeClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class EmployeeAdminServer implements AutoCloseable {
    private final HttpServer server;
    private final ExecutorService requests;

    public EmployeeAdminServer(EmployeeClient employees, String password, int port) throws IOException {
        if (password == null || password.isBlank())
            throw new IllegalArgumentException("ATTENDANCE_ADMIN_WEB_PASSWORD must be set");

        byte[] expectedPassword = password.getBytes(StandardCharsets.UTF_8);
        var resources = EmployeeAdminResources.load();
        var controller = new EmployeeAdminController(employees, new EmployeeAdminPage(resources), UUID.randomUUID().toString());
        var handler = new EmployeeAdminHandler(controller, resources);
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        requests = Executors.newFixedThreadPool(4);
        server.setExecutor(requests);
        server.createContext("/", handler).setAuthenticator(new BasicAuthenticator("Employee administration", StandardCharsets.UTF_8) {
            @Override
            public boolean checkCredentials(String username, String suppliedPassword) {
                return "admin".equals(username) && MessageDigest.isEqual(expectedPassword,
                        suppliedPassword.getBytes(StandardCharsets.UTF_8));
            }
        });
    }

    public void start() {
        server.start();
    }

    public int port() {
        return server.getAddress().getPort();
    }

    @Override
    public void close() {
        try {
            server.stop(0);
        } finally {
            requests.shutdownNow();
        }
    }
}
