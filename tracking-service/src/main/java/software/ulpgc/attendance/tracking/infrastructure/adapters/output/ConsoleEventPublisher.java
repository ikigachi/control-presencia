package software.ulpgc.attendance.tracking.infrastructure.adapters.output;

import com.google.gson.Gson;
import software.ulpgc.attendance.application.domain.model.TrackingEvent;
import software.ulpgc.attendance.tracking.application.ports.output.EventPublisher;

import java.io.IOException;
import java.io.PrintStream;

public class ConsoleEventPublisher implements EventPublisher {
    private final PrintStream output;
    private final Gson json = new Gson();

    public ConsoleEventPublisher(PrintStream output) {
        this.output = output;
    }

    @Override
    public void publish(TrackingEvent event) throws IOException {
        String type = switch (event.type()) {
            case ENTRY -> "in";
            case EXIT -> "out";
        };
        output.println(json.toJson(new EventMessage(event.ts().toString(), event.employee(), event.building(), type)));
        if (output.checkError())
            throw new IOException("Could not write the simulated event");
    }

    private record EventMessage(String ts, String employee, String building, String type) {
    }
}
