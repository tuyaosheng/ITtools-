package com.ittools.lab;

import com.ittools.support.AbstractPostgresIT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;

import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AdminLabWorkstationIT extends AbstractPostgresIT {
    @Autowired
    TestRestTemplate rest;

    // 与 AdminKlassIT/AdminSchoolYearIT 相同的 cookie+CSRF 处理方式，各测试类各自复制一份
    // （项目约定：不共享测试基础设施）。
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
    void createListUpdateDeleteWorkstation() {
        HttpHeaders sess = adminSession();

        ResponseEntity<String> create = rest.exchange("/api/admin/lab-workstations", HttpMethod.POST,
                new HttpEntity<>("{\"pcName\":\"PC-01\",\"ipAddress\":\"192.168.1.101\"}", sess), String.class);
        assertThat(create.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(create.getBody()).contains("PC-01").contains("192.168.1.101");
        long id = Long.parseLong(create.getBody().replaceAll(".*\"id\":(\\d+).*", "$1"));

        ResponseEntity<String> list = rest.exchange("/api/admin/lab-workstations", HttpMethod.GET,
                new HttpEntity<>(sess), String.class);
        assertThat(list.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(list.getBody()).contains("PC-01");

        ResponseEntity<String> update = rest.exchange("/api/admin/lab-workstations/" + id, HttpMethod.PUT,
                new HttpEntity<>("{\"pcName\":\"PC-01\",\"ipAddress\":\"192.168.1.102\"}", sess), String.class);
        assertThat(update.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(update.getBody()).contains("192.168.1.102");

        ResponseEntity<String> delete = rest.exchange("/api/admin/lab-workstations/" + id, HttpMethod.DELETE,
                new HttpEntity<>(sess), String.class);
        assertThat(delete.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void duplicatePcNameRejected() {
        HttpHeaders sess = adminSession();
        rest.exchange("/api/admin/lab-workstations", HttpMethod.POST,
                new HttpEntity<>("{\"pcName\":\"PC-DUP\",\"ipAddress\":\"192.168.1.201\"}", sess), String.class);

        ResponseEntity<String> dup = rest.exchange("/api/admin/lab-workstations", HttpMethod.POST,
                new HttpEntity<>("{\"pcName\":\"PC-DUP\",\"ipAddress\":\"192.168.1.202\"}", sess), String.class);
        assertThat(dup.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(dup.getBody()).contains("已存在");
    }

    @Test
    void anonymousForbidden() {
        ResponseEntity<String> r = rest.getForEntity("/api/admin/lab-workstations", String.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
