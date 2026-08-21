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

// AbstractPostgresIT's own @SpringBootTest has no webEnvironment (defaults to
// MOCK - no running server), but this test drives real HTTP + cookies via
// TestRestTemplate, which is only auto-configured for RANDOM_PORT/DEFINED_PORT.
// Re-declaring @SpringBootTest here (same pattern ContextSmokeIT already uses
// to pin its own class-level config) replaces the inherited annotation with a
// RANDOM_PORT one; the parent's @DynamicPropertySource method is a plain
// method and stays inherited regardless.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuthEndpointIT extends AbstractPostgresIT {
    @Autowired
    TestRestTemplate rest;

    @Test
    void meIsUnauthorizedWithoutLogin() {
        ResponseEntity<String> r = rest.getForEntity("/api/auth/me", String.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void adminCanLoginAndFetchMe() {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        String body = "{\"loginType\":\"ADMIN\",\"loginName\":\"admin\",\"password\":\"admin123\"}";
        ResponseEntity<String> login = rest.postForEntity("/api/auth/login", new HttpEntity<>(body, h), String.class);
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(login.getBody()).contains("\"role\":\"ADMIN\"");

        // The login response carries two Set-Cookie headers - XSRF-TOKEN
        // (from CookieCsrfTokenRepository, written on every request) and
        // JSESSIONID (created when AuthController#login persists the
        // SecurityContext into the HttpSession). HttpHeaders#getFirst would
        // silently grab only whichever happens to be written first (in
        // practice XSRF-TOKEN, since the servlet container appends the
        // session cookie at response commit) and drop the session cookie
        // that authentication actually depends on. A real browser/cookie-jar
        // sends every cookie back, so replicate that here instead.
        List<String> setCookies = login.getHeaders().get(HttpHeaders.SET_COOKIE);
        assertThat(setCookies).isNotNull();
        String cookie = setCookies.stream()
                .map(sc -> sc.split(";", 2)[0])
                .collect(Collectors.joining("; "));
        HttpHeaders h2 = new HttpHeaders();
        h2.add(HttpHeaders.COOKIE, cookie);
        ResponseEntity<String> me = rest.exchange("/api/auth/me", HttpMethod.GET, new HttpEntity<>(h2), String.class);
        assertThat(me.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
