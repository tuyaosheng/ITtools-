package com.ittools.platform.service;

import com.ittools.platform.domain.SchoolYear;
import com.ittools.platform.repository.SchoolYearRepository;
import com.ittools.platform.service.dto.SchoolYearDtos.SchoolYearCommand;
import com.ittools.platform.service.dto.SchoolYearDtos.SchoolYearView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Java 8 note: the brief uses .toList() (Java 16+), which is unavailable
 * here - Collectors.toList() is used instead, matching LookupService
 * (Task 7).
 */
@Service
public class SchoolYearService {
    private final SchoolYearRepository repo;

    public SchoolYearService(SchoolYearRepository r) {
        this.repo = r;
    }

    public List<SchoolYearView> list() {
        return repo.findAll().stream()
                .map(y -> new SchoolYearView(y.getId(), y.getYearCode(), y.getLabel(), y.isActive()))
                .collect(Collectors.toList());
    }

    @Transactional
    public SchoolYearView create(SchoolYearCommand c) {
        SchoolYear y = repo.save(new SchoolYear(c.yearCode(), c.label(), c.active()));
        return new SchoolYearView(y.getId(), y.getYearCode(), y.getLabel(), y.isActive());
    }

    @Transactional
    public SchoolYearView update(Long id, SchoolYearCommand c) {
        SchoolYear y = repo.findById(id).orElseThrow(() -> new IllegalArgumentException("年级不存在"));
        y.setYearCode(c.yearCode());
        y.setLabel(c.label());
        y.setActive(c.active());
        return new SchoolYearView(y.getId(), y.getYearCode(), y.getLabel(), y.isActive());
    }

    @Transactional
    public void delete(Long id) {
        repo.deleteById(id);
    }
}
