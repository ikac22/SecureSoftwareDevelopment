package com.zuehlke.securesoftwaredevelopment.service;

import com.zuehlke.securesoftwaredevelopment.domain.Technician;

import java.util.List;

public interface TechnicianDirectory {
    List<Technician> findAll();
}
