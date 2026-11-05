package software.ulpgc.attendance.tracking.application.ports.input;

import java.io.IOException;

public interface DeviceReader {
    String read() throws IOException;
}
