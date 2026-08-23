package com.zuehlke.securesoftwaredevelopment.service;

import com.zuehlke.securesoftwaredevelopment.domain.Technician;

import java.util.List;
import java.util.Optional;

public interface TechnicianDirectory {
    List<Technician> findAll();

    List<Technician> search(String query);

    Optional<Technician> findById(String uid);
}
