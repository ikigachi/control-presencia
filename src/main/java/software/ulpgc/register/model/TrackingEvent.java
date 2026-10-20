package software.ulpgc.register.model;

import java.time.Instant;

public record TrackingEvent (Instant ts,Employee employee, Place place,TrackingType type){
}
