package com.ittools.platform;

import com.ittools.platform.repository.UserRepository;
import com.ittools.support.AbstractPostgresIT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

// See AuthEndpointIT / AdminSchoolYearIT / AdminKlassIT for why @SpringBootTest
// is re-declared here with webEnvironment = RANDOM_PORT: AbstractPostgresIT's
// own @SpringBootTest has no webEnvironment (defaults to MOCK - no running
// server), but this test drives real HTTP + cookies via TestRestTemplate,
// which is only auto-configured for RANDOM_PORT/DEFINED_PORT.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AdminUserIT extends AbstractPostgresIT {
    @Autowired
    TestRestTemplate rest;
    @Autowired
    UserRepository users;
    @Autowired
    PasswordEncoder encoder;

    // Task 10: copied per-task (not shared) per the established Task 8/9
    // pattern. Mirrors the CORRECTED cookie-jar pattern rather than the task
    // brief's snippet, which used
    // login.getHeaders().getFirst(HttpHeaders.SET_COOKIE) - that only grabs
    // ONE of the two Set-Cookie headers (XSRF-TOKEN and JSESSIONID) and
    // silently drops the other, breaking either CSRF or session auth. A real
    // cookie jar sends every cookie back, so we collect all Set-Cookie
    // values, keep each "name=value" pair, and join them into one Cookie
    // header - plus lift the XSRF-TOKEN value into an X-XSRF-TOKEN header,
    // since /api/admin/** POST/PUT/DELETE are NOT CSRF-exempt (only
    // /api/auth/login is).
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
    void createTeacherStoresBcryptHash() {
        HttpHeaders sess = adminSession();
        ResponseEntity<String> create = rest.exchange("/api/admin/users", HttpMethod.POST,
                new HttpEntity<>("{\"role\":\"TEACHER\",\"name\":\"陈老师\",\"loginName\":\"chen\",\"password\":\"pw123\"}", sess),
                String.class);
        assertThat(create.getStatusCode()).isEqualTo(HttpStatus.OK);

        com.ittools.platform.domain.User u = users.findByLoginName("chen").orElseThrow(() -> new AssertionError("not found"));
        assertThat(u.getPasswordHash()).isNotEqualTo("pw123");
        assertThat(encoder.matches("pw123", u.getPasswordHash())).isTrue();
    }

    @Test
    void createDefaultsPasswordWhenOmitted() {
        HttpHeaders sess = adminSession();
        ResponseEntity<String> create = rest.exchange("/api/admin/users", HttpMethod.POST,
                new HttpEntity<>("{\"role\":\"TEACHER\",\"name\":\"李老师\",\"loginName\":\"li\"}", sess),
                String.class);
        assertThat(create.getStatusCode()).isEqualTo(HttpStatus.OK);

        com.ittools.platform.domain.User u = users.findByLoginName("li").orElseThrow(() -> new AssertionError("not found"));
        assertThat(encoder.matches("123456", u.getPasswordHash())).isTrue();
    }

    @Test
    void listUpdateResetGraduateEnableAndDeleteFlow() {
        HttpHeaders sess = adminSession();
        ResponseEntity<String> create = rest.exchange("/api/admin/users", HttpMethod.POST,
                new HttpEntity<>("{\"role\":\"TEACHER\",\"name\":\"王老师\",\"loginName\":\"wang\",\"password\":\"pw123\"}", sess),
                String.class);
        assertThat(create.getStatusCode()).isEqualTo(HttpStatus.OK);
        long id = users.findByLoginName("wang").orElseThrow(() -> new AssertionError("not found")).getId();

        ResponseEntity<String> list = rest.exchange("/api/admin/users?role=TEACHER", HttpMethod.GET,
                new HttpEntity<>(sess), String.class);
        assertThat(list.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(list.getBody()).contains("wang");

        ResponseEntity<String> update = rest.exchange("/api/admin/users/" + id, HttpMethod.PUT,
                new HttpEntity<>("{\"name\":\"王老师2\",\"loginName\":\"wang\"}", sess), String.class);
        assertThat(update.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(users.findById(id).orElseThrow(() -> new AssertionError("not found")).getName()).isEqualTo("王老师2");

        ResponseEntity<String> reset = rest.exchange("/api/admin/users/" + id + "/reset-password", HttpMethod.POST,
                new HttpEntity<>("{\"password\":\"newpw\"}", sess), String.class);
        assertThat(reset.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(encoder.matches("newpw", users.findById(id).orElseThrow(() -> new AssertionError("not found")).getPasswordHash())).isTrue();

        ResponseEntity<String> graduate = rest.exchange("/api/admin/users/" + id + "/graduate?value=true", HttpMethod.POST,
                new HttpEntity<>(sess), String.class);
        assertThat(graduate.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(users.findById(id).orElseThrow(() -> new AssertionError("not found")).isGraduated()).isTrue();

        ResponseEntity<String> disable = rest.exchange("/api/admin/users/" + id + "/enabled?value=false", HttpMethod.POST,
                new HttpEntity<>(sess), String.class);
        assertThat(disable.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(users.findById(id).orElseThrow(() -> new AssertionError("not found")).isEnabled()).isFalse();

        ResponseEntity<String> delete = rest.exchange("/api/admin/users/" + id, HttpMethod.DELETE,
                new HttpEntity<>(sess), String.class);
        assertThat(delete.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(users.findById(id).isPresent()).isFalse();
    }

    @Test
    void anonymousForbidden() {
        ResponseEntity<String> r = rest.getForEntity("/api/admin/users", String.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
