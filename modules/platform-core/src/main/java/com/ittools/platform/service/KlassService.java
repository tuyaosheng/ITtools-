package com.ittools.platform.service;

import com.ittools.platform.domain.Klass;
import com.ittools.platform.domain.SchoolYear;
import com.ittools.platform.repository.KlassRepository;
import com.ittools.platform.repository.SchoolYearRepository;
import com.ittools.platform.service.dto.KlassDtos.KlassCommand;
import com.ittools.platform.service.dto.KlassDtos.KlassView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Java 8 note: the brief uses .stream()...toList() (Java 16+), which is
 * unavailable here - Collectors.toList() is used instead, matching
 * SchoolYearService (Task 8) / LookupService (Task 7).
 */
@Service
public class KlassService {
    private final KlassRepository classes;
    private final SchoolYearRepository years;

    public KlassService(KlassRepository c, SchoolYearRepository y) {
        this.classes = c;
        this.years = y;
    }

    /**
     * @Transactional(readOnly = true) is required here (the brief's snippet
     * omits it): Klass.schoolYear is @ManyToOne(fetch = LAZY), and
     * application.yml sets spring.jpa.open-in-view: false, so the Hibernate
     * session closes as soon as the (otherwise plain, non-transactional)
     * repository call returns. view() then dereferences
     * k.getSchoolYear().getLabel() outside any session, throwing
     * LazyInitializationException (surfaced as an unhandled 500, since
     * GlobalExceptionHandler has no generic Exception handler). Keeping the
     * session open across the map() call fixes it.
     */
    @Transactional(readOnly = true)
    public List<KlassView> listByYear(Long yearId) {
        return classes.findBySchoolYear_IdOrderByDisplayOrderAscNameAsc(yearId).stream()
                .map(this::view)
                .collect(Collectors.toList());
    }

    @Transactional
    public KlassView create(KlassCommand c) {
        SchoolYear y = years.findById(c.schoolYearId()).orElseThrow(() -> new IllegalArgumentException("年级不存在"));
        return view(classes.save(new Klass(y, c.name(), c.displayOrder())));
    }

    @Transactional
    public KlassView update(Long id, KlassCommand c) {
        Klass k = classes.findById(id).orElseThrow(() -> new IllegalArgumentException("班级不存在"));
        SchoolYear y = years.findById(c.schoolYearId()).orElseThrow(() -> new IllegalArgumentException("年级不存在"));
        k.setSchoolYear(y);
        k.setName(c.name());
        k.setDisplayOrder(c.displayOrder());
        return view(k);
    }

    @Transactional
    public void delete(Long id) {
        classes.deleteById(id);
    }

    private KlassView view(Klass k) {
        return new KlassView(k.getId(), k.getSchoolYear().getId(), k.getSchoolYear().getLabel(), k.getName(), k.getDisplayOrder());
    }
}
