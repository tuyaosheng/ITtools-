package com.ittools.platform;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ittools.support.AbstractPostgresIT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/** 教师兼任管理员：勾选后教师账号可用管理后台接口，教学功能不变；改动后旧登录下线；不能改自己、不能停用最后一个管理员。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TeacherAdminAccessIT extends AbstractPostgresIT {
    @Autowired
    TestRestTemplate rest;
    final ObjectMapper om = new ObjectMapper();

    // 项目约定：各测试类各自复制 cookie+CSRF 登录逻辑
    private HttpHeaders session(String body) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> login = rest.postForEntity("/api/auth/login", new HttpEntity<>(body, h), String.class);
        assertThat(login.getStatusCode()).as(body).isEqualTo(HttpStatus.OK);
        List<String> setCookies = login.getHeaders().get(HttpHeaders.SET_COOKIE);
        HttpHeaders sess = new HttpHeaders();
        sess.setContentType(MediaType.APPLICATION_JSON);
        sess.add(HttpHeaders.COOKIE, setCookies.stream().map(sc -> sc.split(";", 2)[0]).collect(Collectors.joining("; ")));
        for (String sc : setCookies) {
            String pair = sc.split(";", 2)[0].trim();
            if (pair.startsWith("XSRF-TOKEN=")) sess.add("X-XSRF-TOKEN", pair.substring("XSRF-TOKEN=".length()));
        }
        return sess;
    }

    private ResponseEntity<String> raw(HttpHeaders s, HttpMethod m, String url, Object body) throws Exception {
        return rest.exchange(url, m, new HttpEntity<>(body == null ? null : om.writeValueAsString(body), s), String.class);
    }

    private JsonNode call(HttpHeaders s, HttpMethod m, String url, Object body) throws Exception {
        ResponseEntity<String> r = raw(s, m, url, body);
        assertThat(r.getStatusCode()).as(url + " → " + r.getBody()).isEqualTo(HttpStatus.OK);
        return om.readTree(r.getBody()).get("data");
    }

    private String fails(HttpHeaders s, HttpMethod m, String url) throws Exception {
        ResponseEntity<String> r = raw(s, m, url, null);
        assertThat(r.getStatusCode()).as(url).isEqualTo(HttpStatus.BAD_REQUEST);
        return r.getBody();
    }

    private static Map<String, Object> map(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
        return m;
    }

    @Test
    void teacherWithAdminAccessCanUseAdminPages() throws Exception {
        HttpHeaders admin = session("{\"loginType\":\"ADMIN\",\"loginName\":\"admin\",\"password\":\"admin123\"}");
        long adminId = call(admin, HttpMethod.GET, "/api/auth/me", null).get("userId").asLong();
        long tid = call(admin, HttpMethod.POST, "/api/admin/users",
                map("role", "TEACHER", "name", "兼管老师", "loginName", "adm_t", "password", "adm-pass-1")).get("id").asLong();
        String teacherLogin = "{\"loginType\":\"TEACHER\",\"loginName\":\"adm_t\",\"password\":\"adm-pass-1\"}";

        // 普通教师：进不了管理接口
        HttpHeaders t = session(teacherLogin);
        assertThat(call(t, HttpMethod.GET, "/api/auth/me", null).get("canAdmin").asBoolean()).isFalse();
        assertThat(raw(t, HttpMethod.GET, "/api/admin/settings", null).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        // 勾选兼任管理员：旧登录下线，重新登录后可用管理接口，教学接口照常
        JsonNode v = call(admin, HttpMethod.POST, "/api/admin/users/" + tid + "/admin-access?value=true", null);
        assertThat(v.get("adminAccess").asBoolean()).isTrue();
        ResponseEntity<String> old = raw(t, HttpMethod.GET, "/api/teacher/classes", null);
        assertThat(old.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(old.getBody()).contains("管理员权限已变更");
        t = session(teacherLogin);
        JsonNode me = call(t, HttpMethod.GET, "/api/auth/me", null);
        assertThat(me.get("role").asText()).isEqualTo("TEACHER");
        assertThat(me.get("canAdmin").asBoolean()).isTrue();
        call(t, HttpMethod.GET, "/api/admin/settings", null);
        assertThat(call(t, HttpMethod.GET, "/api/admin/users/page?keyword=adm_t", null).get("items").get(0).get("adminAccess").asBoolean()).isTrue();
        call(t, HttpMethod.GET, "/api/teacher/classes", null);

        // 保护：不能改自己的权限、不能停用 / 删除自己、不能停用最后一个管理员
        assertThat(fails(t, HttpMethod.POST, "/api/admin/users/" + tid + "/admin-access?value=false")).contains("自己");
        assertThat(fails(t, HttpMethod.POST, "/api/admin/users/" + tid + "/enabled?value=false")).contains("自己");
        assertThat(fails(t, HttpMethod.DELETE, "/api/admin/users/" + tid)).contains("自己");
        assertThat(fails(t, HttpMethod.POST, "/api/admin/users/" + adminId + "/enabled?value=false")).contains("最后一个");
        assertThat(fails(t, HttpMethod.DELETE, "/api/admin/users/" + adminId)).contains("最后一个");
        // 只有教师能兼任
        long sid = call(admin, HttpMethod.POST, "/api/admin/users", map("role", "STUDENT", "name", "兼管生", "studentNo", "01",
                "xjh", "ADMACC01", "password", "stu-pass-1")).get("id").asLong();
        assertThat(fails(admin, HttpMethod.POST, "/api/admin/users/" + sid + "/admin-access?value=true")).contains("只有教师");

        // 取消：下线，重新登录后又进不了
        call(admin, HttpMethod.POST, "/api/admin/users/" + tid + "/admin-access?value=false", null);
        assertThat(raw(t, HttpMethod.GET, "/api/teacher/classes", null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        t = session(teacherLogin);
        assertThat(raw(t, HttpMethod.GET, "/api/admin/settings", null).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
