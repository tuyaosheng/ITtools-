package com.ittools.platform.repository;

import com.ittools.platform.domain.Klass;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface KlassRepository extends JpaRepository<Klass, Long> {
    List<Klass> findBySchoolYear_IdOrderByDisplayOrderAscNameAsc(Long schoolYearId);
}
