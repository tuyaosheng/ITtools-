package com.ittools.lab.repository;

import com.ittools.lab.domain.LabWorkstation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LabWorkstationRepository extends JpaRepository<LabWorkstation, Long> {
    List<LabWorkstation> findAllByOrderByPcNameAsc();
    Optional<LabWorkstation> findByPcName(String pcName);
    boolean existsByPcName(String pcName);
    boolean existsByIpAddress(String ipAddress);
}
