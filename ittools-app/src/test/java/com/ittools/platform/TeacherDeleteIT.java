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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TeacherDeleteIT extends AbstractPostgresIT {
    @Autowired
    TestRestTemplate rest;

    // 项目约定：各测试类各自复制 cookie+CSRF 登录逻辑
    private HttpHeaders session(String body) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> login = rest.postForEntity("/api/auth/login", new HttpEntity<>(body, h), String.class);
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<String> setCookies = login.getHeaders().get(HttpHeaders.SET_COOKIE);
        assertThat(setCookies).isNotNull();
        HttpHeaders sess = new HttpHeaders();
        sess.add(HttpHeaders.COOKIE, setCookies.stream().map(sc -> sc.split(";", 2)[0]).collect(Collectors.joining("; ")));
        for (String sc : setCookies) {
            String pair = sc.split(";", 2)[0].trim();
            if (pair.startsWith("XSRF-TOKEN=")) sess.add("X-XSRF-TOKEN", pair.substring("XSRF-TOKEN=".length()));
        }
        return sess;
    }

    private HttpHeaders teacherSession() {
        HttpHeaders admin = session("{\"loginType\":\"ADMIN\",\"loginName\":\"admin\",\"password\":\"admin123\"}");
        admin.setContentType(MediaType.APPLICATION_JSON);
        rest.exchange("/api/admin/users", HttpMethod.POST, new HttpEntity<>(
                "{\"role\":\"TEACHER\",\"name\":\"删除测试\",\"loginName\":\"del_t\",\"password\":\"plan-pass-1\"}", admin), String.class);
        return session("{\"loginType\":\"TEACHER\",\"loginName\":\"del_t\",\"password\":\"plan-pass-1\"}");
    }

    @Test
    void deletingTeacherWithCoursesGivesFriendlyError() {
        HttpHeaders t = teacherSession();
        t.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> course = rest.exchange("/api/teacher/courses", HttpMethod.POST,
                new HttpEntity<>("{\"name\":\"有课的教师\"}", t), String.class);
        assertThat(course.getStatusCode()).isEqualTo(HttpStatus.OK);
        String me = rest.exchange("/api/auth/me", HttpMethod.GET, new HttpEntity<>(t), String.class).getBody();
        long teacherId = Long.parseLong(me.replaceAll("(?s).*\"(?:userId|id)\":(\\d+).*", "$1"));

        HttpHeaders admin = session("{\"loginType\":\"ADMIN\",\"loginName\":\"admin\",\"password\":\"admin123\"}");
        ResponseEntity<String> del = rest.exchange("/api/admin/users/" + teacherId, HttpMethod.DELETE, new HttpEntity<>(admin), String.class);
        assertThat(del.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(del.getBody()).contains("可改为停用账号");
    }
}
