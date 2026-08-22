package com.zuehlke.securesoftwaredevelopment.domain;

import java.util.Collections;
import java.util.List;

public class TechnicianAvailability {
    private final Technician technician;
    private final List<String> availableStartTimes;

    public TechnicianAvailability(Technician technician, List<String> availableStartTimes) {
        this.technician = technician;
        this.availableStartTimes = Collections.unmodifiableList(availableStartTimes);
    }

    public Technician getTechnician() {
        return technician;
    }

    public List<String> getAvailableStartTimes() {
        return availableStartTimes;
    }
}
