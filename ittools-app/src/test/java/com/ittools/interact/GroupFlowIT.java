package com.ittools.interact;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/** 分组与小组活动全流程：自由分组、预分组、组长推选、小组合作、成果互评、加载历史分组、随机分组。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GroupFlowIT extends AbstractPostgresIT {
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

    private static JsonNode group(JsonNode overview, int no) {
        return overview.get("groups").get(no - 1);
    }

    private static List<String> names(JsonNode group) {
        List<String> out = new java.util.ArrayList<>();
        group.get("members").forEach(m -> out.add(m.get("name").asText()));
        return out;
    }

    @Test
    void groupingAndGroupWork() throws Exception {
        HttpHeaders admin = session("{\"loginType\":\"ADMIN\",\"loginName\":\"admin\",\"password\":\"admin123\"}");
        long yearId = call(admin, HttpMethod.POST, "/api/admin/school-years", map("yearCode", "2032", "label", "2032级", "active", true)).get("id").asLong();
        long classId = call(admin, HttpMethod.POST, "/api/admin/classes", map("schoolYearId", yearId, "name", "组班", "displayOrder", 1)).get("id").asLong();
        call(admin, HttpMethod.POST, "/api/admin/users", map("role", "TEACHER", "name", "组老师", "loginName", "grp_t", "password", "grp-pass-1"));
        long[] sid = new long[5];
        HttpHeaders[] s = new HttpHeaders[5];
        for (int i = 1; i <= 4; i++) {
            sid[i] = call(admin, HttpMethod.POST, "/api/admin/users", map("role", "STUDENT", "name", "组员" + i, "studentNo", "0" + i,
                    "xjh", "GRP000" + i, "classId", classId, "enrollYearId", yearId, "password", "grp-pass-1")).get("id").asLong();
            s[i] = session("{\"loginType\":\"STUDENT_XJH\",\"xjh\":\"GRP000" + i + "\",\"password\":\"grp-pass-1\"}");
        }
        HttpHeaders t = session("{\"loginType\":\"TEACHER\",\"loginName\":\"grp_t\",\"password\":\"grp-pass-1\"}");
        long courseId = call(t, HttpMethod.POST, "/api/teacher/courses", map("name", "小组合作课")).get("id").asLong();
        long lesson = call(t, HttpMethod.POST, "/api/teacher/lessons", map("courseId", courseId, "classId", classId)).get("id").asLong();
        String tg = "/api/teacher/groups/lessons/" + lesson, sg = "/api/student/groups/lessons/" + lesson;
        for (int i = 1; i <= 3; i++) call(s[i], HttpMethod.POST, "/api/student/lessons/" + lesson + "/join", map());

        // ---- 自由分组 ----
        fails(s[1], HttpMethod.POST, sg + "/join", map("groupNo", 1));   // 未开放
        call(t, HttpMethod.PUT, tg + "/settings", map("groupCount", 2, "joinOpen", true));
        call(s[1], HttpMethod.POST, sg + "/join", map("groupNo", 1));
        call(s[2], HttpMethod.POST, sg + "/join", map("groupNo", 1));
        call(s[3], HttpMethod.POST, sg + "/join", map("groupNo", 2));
        fails(s[3], HttpMethod.POST, sg + "/join", map("groupNo", 3));   // 超出组数
        // 未进课的学生由教师预分组，进课时生效
        call(t, HttpMethod.PUT, tg + "/members/" + sid[4], map("groupNo", 2));
        assertThat(names(group(call(s[1], HttpMethod.GET, sg, null), 2))).as("学生只看到已进课的组员").containsExactly("组员3");
        call(s[4], HttpMethod.POST, "/api/student/lessons/" + lesson + "/join", map());
        JsonNode ov = call(t, HttpMethod.GET, tg, null);
        assertThat(names(group(ov, 1))).containsExactly("组员1", "组员2");
        assertThat(names(group(ov, 2))).containsExactly("组员3", "组员4");
        call(t, HttpMethod.PUT, tg + "/settings", map("joinOpen", false));
        fails(s[1], HttpMethod.POST, sg + "/join", map("groupNo", 2));

        // ---- 组长推选 ----
        fails(s[2], HttpMethod.POST, sg + "/vote", map("candidateId", sid[1]));  // 未开放
        call(t, HttpMethod.PUT, tg + "/settings", map("voteOpen", true));
        fails(s[2], HttpMethod.POST, sg + "/vote", map("candidateId", sid[3]));  // 只能投本组
        fails(s[2], HttpMethod.POST, sg + "/vote", map("candidateId", sid[2]));  // 不能投自己
        call(s[2], HttpMethod.POST, sg + "/vote", map("candidateId", sid[1]));
        call(s[3], HttpMethod.POST, sg + "/vote", map("candidateId", sid[4]));
        call(s[4], HttpMethod.POST, sg + "/vote", map("candidateId", sid[3]));   // 第 2 组平票 → 学号靠前者
        ov = call(t, HttpMethod.POST, tg + "/auto-leaders", null);
        assertThat(group(ov, 1).get("leaderName").asText()).isEqualTo("组员1");
        assertThat(group(ov, 2).get("leaderName").asText()).isEqualTo("组员3");
        assertThat(call(s[1], HttpMethod.GET, sg, null).get("me").get("isLeader").asBoolean()).isTrue();

        // ---- 小组合作 ----
        fails(s[2], HttpMethod.PUT, sg + "/work/topic", map("topic", "组员改主题"));  // 非组长
        call(s[1], HttpMethod.PUT, sg + "/work/topic", map("topic", "校园垃圾分类调查"));
        call(s[1], HttpMethod.PUT, sg + "/work/tasks", map("tasks", new Object[]{map("studentId", sid[2], "task", "做问卷"), map("studentId", sid[3], "task", "越权")}));
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("file", new ByteArrayResource("调查报告".getBytes(StandardCharsets.UTF_8)) { @Override public String getFilename() { return "report.txt"; } });
        form.add("title", "调查报告");
        HttpHeaders mp = new HttpHeaders();
        mp.putAll(s[2]);
        mp.setContentType(MediaType.MULTIPART_FORM_DATA);
        JsonNode work = om.readTree(rest.exchange(sg + "/work/files", HttpMethod.POST, new HttpEntity<>(form, mp), String.class).getBody()).get("data");
        long fileId = work.get("files").get(0).get("id").asLong();
        form.set("title", "草稿");
        long draftId = om.readTree(rest.exchange(sg + "/work/files", HttpMethod.POST, new HttpEntity<>(form, mp), String.class).getBody())
                .get("data").get("files").get(0).get("id").asLong();
        work = call(s[2], HttpMethod.GET, sg + "/work", null);
        assertThat(work.get("group").get("topic").asText()).isEqualTo("校园垃圾分类调查");
        assertThat(work.get("group").get("members").get(1).get("task").asText()).isEqualTo("做问卷");
        assertThat(work.get("files").size()).isEqualTo(2);
        fails(s[2], HttpMethod.PUT, sg + "/work/result", map("resultText", "x", "resultFileId", fileId));  // 非组长
        call(s[1], HttpMethod.PUT, sg + "/work/result", map("resultText", "共回收问卷 120 份", "resultFileId", fileId));
        // 外组同学：成果文件可下载，组内其他资源不可下载
        assertThat(rest.exchange("/api/groups/files/" + fileId, HttpMethod.GET, new HttpEntity<>(s[3]), byte[].class).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(rest.exchange("/api/groups/files/" + draftId, HttpMethod.GET, new HttpEntity<>(s[3]), String.class).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        // 组员互评
        fails(s[2], HttpMethod.PUT, sg + "/work/peers/" + sid[3], map("score", 5));   // 外组
        call(s[2], HttpMethod.PUT, sg + "/work/peers/" + sid[1], map("score", 5, "comment", "组织得好"));

        // ---- 成果展示 / 组间互评 ----
        fails(s[1], HttpMethod.PUT, sg + "/show/1", map("score", 100));   // 不能评本组
        JsonNode show = call(s[3], HttpMethod.PUT, sg + "/show/1", map("score", 90, "comment", "数据详实"));
        assertThat(show.get("groups").get(0).get("myScore").asInt()).isEqualTo(90);
        assertThat(show.get("groups").get(0).get("resultFileName").asText()).isEqualTo("report.txt");
        JsonNode tw = call(t, HttpMethod.GET, tg + "/work", null);
        assertThat(tw.get("groups").get(0).get("reviewAvg").asDouble()).isEqualTo(90.0);
        assertThat(tw.get("groups").get(0).get("members").get(0).get("peerAvg").asDouble()).isEqualTo(5.0);

        // ---- 下一节课：加载历史分组 ----
        call(t, HttpMethod.POST, "/api/teacher/lessons/" + lesson + "/end", map());
        long lesson2 = call(t, HttpMethod.POST, "/api/teacher/lessons", map("courseId", courseId, "classId", classId)).get("id").asLong();
        String tg2 = "/api/teacher/groups/lessons/" + lesson2;
        call(s[2], HttpMethod.POST, "/api/student/lessons/" + lesson2 + "/join", map());
        JsonNode hist = call(t, HttpMethod.GET, tg2 + "/history", null);
        assertThat(hist.get(0).get("id").asLong()).isEqualTo(lesson);
        ov = call(t, HttpMethod.POST, tg2 + "/load", map("fromLessonId", lesson));
        assertThat(names(group(ov, 1))).containsExactly("组员1", "组员2");
        assertThat(group(ov, 1).get("leaderName").asText()).isEqualTo("组员1");
        call(s[1], HttpMethod.POST, "/api/student/lessons/" + lesson2 + "/join", map());
        assertThat(call(s[1], HttpMethod.GET, "/api/student/groups/lessons/" + lesson2, null).get("me").get("groupNo").asInt())
                .as("预分组在进课时生效").isEqualTo(1);

        // ---- 随机分组 ----
        call(s[3], HttpMethod.POST, "/api/student/lessons/" + lesson2 + "/join", map());
        call(s[4], HttpMethod.POST, "/api/student/lessons/" + lesson2 + "/join", map());
        ov = call(t, HttpMethod.POST, tg2 + "/random", map("groupCount", 2));
        assertThat(group(ov, 1).get("members").size()).isEqualTo(2);
        assertThat(group(ov, 2).get("members").size()).isEqualTo(2);
        assertThat(group(ov, 1).get("leaderId").isNull()).as("重新分组后组长清空").isTrue();
    }
}
