package com.ittools.platform;

import com.ittools.support.AbstractPostgresIT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;

import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

// See AuthEndpointIT / AdminSchoolYearIT for why @SpringBootTest is
// re-declared here with webEnvironment = RANDOM_PORT: AbstractPostgresIT's
// own @SpringBootTest has no webEnvironment (defaults to MOCK - no running
// server), but this test drives real HTTP + cookies via TestRestTemplate,
// which is only auto-configured for RANDOM_PORT/DEFINED_PORT.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AdminKlassIT extends AbstractPostgresIT {
    @Autowired
    TestRestTemplate rest;

    // Task 9: copied from AdminSchoolYearIT (Task 8) per the task brief's
    // instruction not to share test infra across tasks. Mirrors the
    // corrected cookie-jar pattern rather than the task brief's snippet,
    // which used login.getHeaders().getFirst(HttpHeaders.SET_COOKIE) - that
    // only grabs ONE of the two Set-Cookie headers (XSRF-TOKEN and
    // JSESSIONID) and silently drops the other, breaking either CSRF or
    // session auth. A real cookie jar sends every cookie back, so we collect
    // all Set-Cookie values, keep each "name=value" pair, and join them into
    // one Cookie header - plus lift the XSRF-TOKEN value into an
    // X-XSRF-TOKEN header, since /api/admin/** POST/PUT/DELETE are NOT
    // CSRF-exempt (only /api/auth/login is).
    private HttpHeaders adminSession() {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> login = rest.postForEntity("/api/auth/login",
                new HttpEntity<>("{\"loginType\":\"ADMIN\",\"loginName\":\"admin\",\"password\":\"admin123\"}", h),
                String.class);
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);

        List<String> setCookies = login.getHeaders().get(HttpHeaders.SET_COOKIE);
        assertThat(setCookies).isNotNull();

        HttpHeaders sess = new HttpHeaders();
        sess.setContentType(MediaType.APPLICATION_JSON);
        String cookie = setCookies.stream()
                .map(sc -> sc.split(";", 2)[0])
                .collect(Collectors.joining("; "));
        sess.add(HttpHeaders.COOKIE, cookie);
        for (String sc : setCookies) {
            String pair = sc.split(";", 2)[0].trim();
            if (pair.startsWith("XSRF-TOKEN=")) {
                sess.add("X-XSRF-TOKEN", pair.substring("XSRF-TOKEN=".length()));
            }
        }
        return sess;
    }

    @Test
    void createClassUnderYear() {
        HttpHeaders sess = adminSession();
        ResponseEntity<String> year = rest.exchange("/api/admin/school-years", HttpMethod.POST,
                new HttpEntity<>("{\"yearCode\":\"2060\",\"label\":\"2060级\",\"active\":true}", sess), String.class);
        assertThat(year.getStatusCode()).isEqualTo(HttpStatus.OK);
        // extract id from response json (naive)
        String body = year.getBody();
        long yearId = Long.parseLong(body.replaceAll(".*\"id\":(\\d+).*", "$1"));

        ResponseEntity<String> create = rest.exchange("/api/admin/classes", HttpMethod.POST,
                new HttpEntity<>("{\"schoolYearId\":" + yearId + ",\"name\":\"7班\",\"displayOrder\":1}", sess), String.class);
        assertThat(create.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(create.getBody()).contains("7班");

        ResponseEntity<String> list = rest.exchange("/api/admin/classes?yearId=" + yearId, HttpMethod.GET,
                new HttpEntity<>(sess), String.class);
        assertThat(list.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(list.getBody()).contains("7班");
    }

    @Test
    void anonymousForbidden() {
        ResponseEntity<String> r = rest.getForEntity("/api/admin/classes?yearId=1", String.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
