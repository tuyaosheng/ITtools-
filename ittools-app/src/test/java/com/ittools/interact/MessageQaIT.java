package com.ittools.interact;

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

/** 系统消息与师生答疑：发送权限、已读、撤回、提问、答复通知、公开问答。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MessageQaIT extends AbstractPostgresIT {
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

    private void fails(HttpHeaders s, HttpMethod m, String url, Object body) throws Exception {
        assertThat(raw(s, m, url, body).getStatusCode()).as(url).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    private static Map<String, Object> map(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
        return m;
    }

    private long student(HttpHeaders admin, String name, String xjh, long classId, long yearId) throws Exception {
        return call(admin, HttpMethod.POST, "/api/admin/users", map("role", "STUDENT", "name", name, "studentNo", xjh.substring(xjh.length() - 2),
                "xjh", xjh, "classId", classId, "enrollYearId", yearId, "password", "msg-pass-1")).get("id").asLong();
    }

    @Test
    void messagesAndQuestions() throws Exception {
        HttpHeaders admin = session("{\"loginType\":\"ADMIN\",\"loginName\":\"admin\",\"password\":\"admin123\"}");
        long yearId = call(admin, HttpMethod.POST, "/api/admin/school-years", map("yearCode", "2033", "label", "2033级", "active", true)).get("id").asLong();
        long classA = call(admin, HttpMethod.POST, "/api/admin/classes", map("schoolYearId", yearId, "name", "甲班", "displayOrder", 1)).get("id").asLong();
        long classB = call(admin, HttpMethod.POST, "/api/admin/classes", map("schoolYearId", yearId, "name", "乙班", "displayOrder", 2)).get("id").asLong();
        long t1 = call(admin, HttpMethod.POST, "/api/admin/users", map("role", "TEACHER", "name", "甲老师", "loginName", "msg_t1", "password", "msg-pass-1")).get("id").asLong();
        long t2 = call(admin, HttpMethod.POST, "/api/admin/users", map("role", "TEACHER", "name", "乙老师", "loginName", "msg_t2", "password", "msg-pass-1")).get("id").asLong();
        call(admin, HttpMethod.PUT, "/api/admin/class-bindings/" + classB, map("teacherId", t2));
        student(admin, "甲一", "MSGA01", classA, yearId);
        long a2 = student(admin, "甲二", "MSGA02", classA, yearId);
        long b1 = student(admin, "乙一", "MSGB01", classB, yearId);

        HttpHeaders teacher1 = session("{\"loginType\":\"TEACHER\",\"loginName\":\"msg_t1\",\"password\":\"msg-pass-1\"}");
        HttpHeaders teacher2 = session("{\"loginType\":\"TEACHER\",\"loginName\":\"msg_t2\",\"password\":\"msg-pass-1\"}");
        HttpHeaders sa1 = session("{\"loginType\":\"STUDENT_XJH\",\"xjh\":\"MSGA01\",\"password\":\"msg-pass-1\"}");
        HttpHeaders sa2 = session("{\"loginType\":\"STUDENT_XJH\",\"xjh\":\"MSGA02\",\"password\":\"msg-pass-1\"}");
        HttpHeaders sb1 = session("{\"loginType\":\"STUDENT_XJH\",\"xjh\":\"MSGB01\",\"password\":\"msg-pass-1\"}");

        // ---- 系统消息 ----
        fails(teacher1, HttpMethod.POST, "/api/teacher/messages", map("title", "x", "classIds", new long[]{classB}));       // 乙班绑定给乙老师
        fails(teacher1, HttpMethod.POST, "/api/teacher/messages", map("title", "x", "studentIds", new long[]{b1}));
        fails(teacher1, HttpMethod.POST, "/api/teacher/messages", map("title", "x"));                                       // 无收件人
        JsonNode sent = call(teacher1, HttpMethod.POST, "/api/teacher/messages", map("title", "下周带U盘", "content", "上机课需要拷贝素材", "classIds", new long[]{classA}));
        assertThat(sent.get("recipientCount").asInt()).isEqualTo(2);
        assertThat(sent.get("targetDesc").asText()).contains("甲班（全班）");
        call(teacher1, HttpMethod.POST, "/api/teacher/messages", map("title", "单独提醒", "studentIds", new long[]{a2}));

        assertThat(call(sa1, HttpMethod.GET, "/api/notices/summary", null).get("unreadMessages").asInt()).isEqualTo(1);
        assertThat(call(sa2, HttpMethod.GET, "/api/notices/summary", null).get("unreadMessages").asInt()).isEqualTo(2);
        assertThat(call(sb1, HttpMethod.GET, "/api/messages/inbox", null).size()).isEqualTo(0);
        long msgId = sent.get("id").asLong();
        call(sa1, HttpMethod.POST, "/api/messages/inbox/" + msgId + "/read", null);
        assertThat(call(sa1, HttpMethod.GET, "/api/notices/summary", null).get("unreadMessages").asInt()).isEqualTo(0);
        JsonNode detail = call(teacher1, HttpMethod.GET, "/api/teacher/messages/" + msgId, null);
        assertThat(detail.get("readCount").asInt()).isEqualTo(1);
        fails(teacher2, HttpMethod.GET, "/api/teacher/messages/" + msgId, null);   // 只能看自己发的
        assertThat(call(sa2, HttpMethod.POST, "/api/messages/inbox/read-all", null).asInt()).isEqualTo(2);
        call(teacher1, HttpMethod.DELETE, "/api/teacher/messages/" + msgId, null);  // 撤回
        assertThat(call(sa1, HttpMethod.GET, "/api/messages/inbox", null).size()).isEqualTo(0);

        // ---- 师生答疑 ----
        JsonNode teachers = call(sa1, HttpMethod.GET, "/api/student/qa/teachers", null);
        List<String> teacherNames = new java.util.ArrayList<>();
        teachers.forEach(x -> teacherNames.add(x.get("name").asText()));
        assertThat(teacherNames).contains("甲老师", "乙老师");   // 测试库共享，其他用例的老师也会出现
        fails(sa1, HttpMethod.POST, "/api/student/qa", map("teacherId", t1, "title", "只有标题"));
        long q = call(sa1, HttpMethod.POST, "/api/student/qa", map("teacherId", t1, "title", "for 循环怎么倒序", "content", "range 能倒着数吗？")).get("id").asLong();
        assertThat(call(teacher1, HttpMethod.GET, "/api/notices/summary", null).get("pendingQuestions").asInt()).isEqualTo(1);
        assertThat(call(teacher2, HttpMethod.GET, "/api/teacher/qa", null).size()).isEqualTo(0);
        fails(teacher2, HttpMethod.PUT, "/api/teacher/qa/" + q + "/answer", map("answer", "越权答复"));
        call(teacher1, HttpMethod.PUT, "/api/teacher/qa/" + q + "/answer", map("answer", "可以：range(10, 0, -1)", "isPublic", true));
        assertThat(call(teacher1, HttpMethod.GET, "/api/notices/summary", null).get("pendingQuestions").asInt()).isEqualTo(0);
        JsonNode inbox = call(sa1, HttpMethod.GET, "/api/messages/inbox", null);
        assertThat(inbox.get(0).get("title").asText()).contains("for 循环怎么倒序");
        assertThat(inbox.get(0).get("senderName").asText()).isEqualTo("甲老师");
        assertThat(call(sa1, HttpMethod.GET, "/api/student/qa", null).get(0).get("answer").asText()).isEqualTo("可以：range(10, 0, -1)");
        JsonNode pub = call(sb1, HttpMethod.GET, "/api/qa/public?keyword=倒序", null);
        assertThat(pub.size()).isEqualTo(1);
        assertThat(pub.get(0).has("studentName")).as("公开问答不暴露提问者").isFalse();
        assertThat(pub.get(0).get("className").asText()).isEqualTo("2033级甲班");
        fails(sa2, HttpMethod.DELETE, "/api/student/qa/" + q, null);
        call(sa1, HttpMethod.DELETE, "/api/student/qa/" + q, null);
        assertThat(call(sb1, HttpMethod.GET, "/api/qa/public", null).size()).isEqualTo(0);
    }
}
