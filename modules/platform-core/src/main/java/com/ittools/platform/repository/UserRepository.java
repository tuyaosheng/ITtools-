package com.ittools.platform.repository;

import com.ittools.platform.domain.Role;
import com.ittools.platform.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByLoginName(String loginName);
    Optional<User> findByXjh(String xjh);
    Optional<User> findByKlass_IdAndNameAndRole(Long classId, String name, Role role);
    List<User> findByKlass_Id(Long classId);
}
