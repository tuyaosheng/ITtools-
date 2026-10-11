package com.ittools.resource;

import com.ittools.support.AbstractPostgresIT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LessonPlanCopyIT extends AbstractPostgresIT {
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
                "{\"role\":\"TEACHER\",\"name\":\"教案测试\",\"loginName\":\"plan_t\",\"password\":\"plan-pass-1\"}", admin), String.class);
        return session("{\"loginType\":\"TEACHER\",\"loginName\":\"plan_t\",\"password\":\"plan-pass-1\"}");
    }

    private static long id(String body) {
        return Long.parseLong(body.replaceAll("(?s).*?\"data\":\\{\"id\":(\\d+).*", "$1"));
    }

    @Test
    void deletingCopyKeepsOriginalAttachment() {
        HttpHeaders t = teacherSession();

        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("title", "顺序结构");
        form.add("content", "<p>教学目标</p>");
        form.add("doc", new ByteArrayResource("教案正文".getBytes(StandardCharsets.UTF_8)) {
            @Override public String getFilename() { return "plan.docx"; }
        });
        HttpHeaders mp = new HttpHeaders();
        mp.putAll(t);
        mp.setContentType(MediaType.MULTIPART_FORM_DATA);
        ResponseEntity<String> created = rest.exchange("/api/teacher/lesson-plans", HttpMethod.POST, new HttpEntity<>(form, mp), String.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.OK);
        long original = id(created.getBody());

        ResponseEntity<String> copied = rest.exchange("/api/teacher/lesson-plans/" + original + "/copy", HttpMethod.POST, new HttpEntity<>(t), String.class);
        assertThat(copied.getStatusCode()).isEqualTo(HttpStatus.OK);
        long copy = Long.parseLong(copied.getBody().replaceAll("(?s).*\"data\":(\\d+).*", "$1"));

        ResponseEntity<String> del = rest.exchange("/api/teacher/lesson-plans/" + copy, HttpMethod.DELETE, new HttpEntity<>(t), String.class);
        assertThat(del.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<byte[]> doc = rest.exchange("/api/teacher/lesson-plans/" + original + "/doc", HttpMethod.GET, new HttpEntity<>(t), byte[].class);
        assertThat(doc.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(new String(doc.getBody(), StandardCharsets.UTF_8)).isEqualTo("教案正文");
    }
}
