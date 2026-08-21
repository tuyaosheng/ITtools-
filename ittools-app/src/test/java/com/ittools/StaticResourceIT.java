package com.ittools;

import com.ittools.support.AbstractPostgresIT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import static org.assertj.core.api.Assertions.assertThat;

// AbstractPostgresIT's own @SpringBootTest has no webEnvironment (defaults to
// MOCK, no running server), so re-declare with RANDOM_PORT here to get a
// TestRestTemplate, matching the pattern used by AuthEndpointIT.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class StaticResourceIT extends AbstractPostgresIT {
    @Autowired TestRestTemplate rest;

    @Test
    void rootServesSpaIndex() {
        ResponseEntity<String> r = rest.getForEntity("/", String.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(r.getBody()).contains("<div id=\"app\">");
    }

    @Test
    void deepLinkFallsBackToSpaIndex() {
        ResponseEntity<String> r = rest.getForEntity("/admin/users", String.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(r.getBody()).contains("<div id=\"app\">");
    }
}
