package com.zuehlke.securesoftwaredevelopment.service;

import com.zuehlke.securesoftwaredevelopment.domain.Technician;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Component
public class MockTechnicianDirectory implements TechnicianDirectory {
    private final List<Technician> technicians = Collections.unmodifiableList(Arrays.asList(
            new Technician("marko.markovic", "Marko Markovic", "marko.markovic@securecar.test"),
            new Technician("ana.anic", "Ana Anic", "ana.anic@securecar.test"),
            new Technician("nikola.nikolic", "Nikola Nikolic", "nikola.nikolic@securecar.test")
    ));

    @Override
    public List<Technician> findAll() {
        return technicians;
    }
}
