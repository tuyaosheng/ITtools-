package com.ittools.platform;

import com.ittools.platform.domain.*;
import com.ittools.platform.repository.*;
import com.ittools.support.AbstractPostgresIT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import static org.assertj.core.api.Assertions.assertThat;

// Same RANDOM_PORT re-declaration pattern as AuthEndpointIT: AbstractPostgresIT's
// own @SpringBootTest has no webEnvironment (defaults to MOCK, no running
// server), but this test drives real HTTP via TestRestTemplate, which is only
// auto-configured for RANDOM_PORT/DEFINED_PORT.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PublicLookupIT extends AbstractPostgresIT {
    @Autowired TestRestTemplate rest;
    @Autowired SchoolYearRepository years;
    @Autowired KlassRepository classes;
    @Autowired UserRepository users;

    @Test
    void studentsListedForClassWithoutLogin() {
        SchoolYear y = years.save(new SchoolYear("2040","2040级",true));
        Klass k = classes.save(new Klass(y,"5班",1));
        User s = new User(); s.setRole(Role.STUDENT); s.setName("王五"); s.setKlass(k); s.setPasswordHash("h");
        users.save(s);

        ResponseEntity<String> r = rest.getForEntity("/api/public/students?classId=" + k.getId(), String.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(r.getBody()).contains("王五");
    }
}
