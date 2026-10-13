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

/** 第4批课堂环节：课堂调查、随机抽题、随机点名与请假原因。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ClassActivityIT extends AbstractPostgresIT {
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
    void surveyTakeQuestionRollCall() throws Exception {
        HttpHeaders admin = session("{\"loginType\":\"ADMIN\",\"loginName\":\"admin\",\"password\":\"admin123\"}");
        long yearId = call(admin, HttpMethod.POST, "/api/admin/school-years", map("yearCode", "2043", "label", "2043级", "active", true)).get("id").asLong();
        long classA = call(admin, HttpMethod.POST, "/api/admin/classes", map("schoolYearId", yearId, "name", "活动1班", "displayOrder", 1)).get("id").asLong();
        long classB = call(admin, HttpMethod.POST, "/api/admin/classes", map("schoolYearId", yearId, "name", "活动2班", "displayOrder", 2)).get("id").asLong();
        call(admin, HttpMethod.POST, "/api/admin/users", map("role", "TEACHER", "name", "活动老师", "loginName", "act_t", "password", "act-pass-1"));
        long s1 = student(admin, "活动生甲", "ACT001", classA, yearId);
        long s2 = student(admin, "活动生乙", "ACT002", classA, yearId);
        long s3 = student(admin, "活动生丙", "ACT003", classA, yearId);
        student(admin, "二班生", "ACT104", classB, yearId);
        HttpHeaders t = session("{\"loginType\":\"TEACHER\",\"loginName\":\"act_t\",\"password\":\"act-pass-1\"}");
        HttpHeaders a = session("{\"loginType\":\"STUDENT_XJH\",\"xjh\":\"ACT001\",\"password\":\"msg-pass-1\"}");
        HttpHeaders b = session("{\"loginType\":\"STUDENT_XJH\",\"xjh\":\"ACT002\",\"password\":\"msg-pass-1\"}");
        HttpHeaders c2 = session("{\"loginType\":\"STUDENT_XJH\",\"xjh\":\"ACT104\",\"password\":\"msg-pass-1\"}");

        long course = call(t, HttpMethod.POST, "/api/teacher/courses", map("name", "活动课")).get("id").asLong();
        long survey = call(t, HttpMethod.POST, "/api/teacher/courses/" + course + "/pages", map("title", "学习调查", "pageType", "SURVEY", "studentVisible", true)).get("id").asLong();
        long take = call(t, HttpMethod.POST, "/api/teacher/courses/" + course + "/pages", map("title", "抽题", "pageType", "TAKE_QUESTION", "studentVisible", true)).get("id").asLong();

        // ---- 调查题目配置 ----
        String cfgUrl = "/api/teacher/activity/pages/" + survey + "/survey";
        fails(t, HttpMethod.PUT, cfgUrl, map("items", new Object[]{map("qtype", "SINGLE", "stem", "只有一个选项", "options", new String[]{"A"})}));
        JsonNode cfg = call(t, HttpMethod.PUT, cfgUrl, map("nameMode", "TEACHER", "items", new Object[]{
                map("qtype", "JUDGE", "stem", "本课内容你听懂了吗"),
                map("qtype", "SINGLE", "stem", "难度如何", "options", new String[]{"简单", "适中", "较难"}),
                map("qtype", "MULTI", "stem", "喜欢哪些环节", "options", new String[]{"讲解", "练习", "讨论"}),
                map("qtype", "FILL", "stem", "给老师的建议")}));
        JsonNode items = cfg.get("items");
        String jId = items.get(0).get("id").asText(), sId = items.get(1).get("id").asText(), mId = items.get(2).get("id").asText(), fId = items.get(3).get("id").asText();

        long lessonA = call(t, HttpMethod.POST, "/api/teacher/lessons", map("courseId", course, "classId", classA)).get("id").asLong();
        long lessonB = call(t, HttpMethod.POST, "/api/teacher/lessons", map("courseId", course, "classId", classB)).get("id").asLong();
        call(a, HttpMethod.POST, "/api/student/lessons/" + lessonA + "/join", map());
        call(b, HttpMethod.POST, "/api/student/lessons/" + lessonA + "/join", map());
        call(c2, HttpMethod.POST, "/api/student/lessons/" + lessonB + "/join", map());

        String su = "/api/student/activity/lessons/" + lessonA + "/pages/" + survey + "/survey";
        JsonNode view = call(a, HttpMethod.GET, su, null);
        assertThat(view.get("items").size()).isEqualTo(4);
        assertThat(view.get("results").isNull()).as("未提交不显示结果").isTrue();
        fails(a, HttpMethod.PUT, su, map("answers", map(jId, "T")));                     // 未答完
        fails(a, HttpMethod.PUT, su, map("answers", map(jId, "T", sId, "D", mId, new String[]{"A"}, fId, "好")));   // 单选越界
        view = call(a, HttpMethod.PUT, su, map("answers", map(jId, "T", sId, "B", mId, new String[]{"C", "A", "A"}, fId, "多练习")));
        assertThat(view.get("submitted").asBoolean()).isTrue();
        assertThat(view.get("answers").get(mId).toString()).isEqualTo("[\"A\",\"C\"]");
        JsonNode res = view.get("results");
        assertThat(res.get("respondents").asInt()).isEqualTo(1);
        assertThat(res.get("items").get(3).get("texts").get(0).has("name")).as("nameMode=TEACHER 学生端不显示名字").isFalse();
        call(b, HttpMethod.PUT, su, map("answers", map(jId, "F", sId, "B", mId, new String[]{"A"}, fId, "挺好")));
        call(c2, HttpMethod.PUT, "/api/student/activity/lessons/" + lessonB + "/pages/" + survey + "/survey",
                map("answers", map(jId, "T", sId, "C", mId, new String[]{"B"}, fId, "二班建议")));

        JsonNode tv = call(t, HttpMethod.GET, "/api/teacher/activity/lessons/" + lessonA + "/pages/" + survey + "/survey", null).get("results");
        assertThat(tv.get("respondents").asInt()).isEqualTo(2);
        assertThat(tv.get("items").get(0).get("counts").toString()).isEqualTo("{\"T\":1,\"F\":1}");
        assertThat(tv.get("items").get(1).get("counts").get("B").asInt()).isEqualTo(2);
        assertThat(tv.get("items").get(2).get("counts").get("A").asInt()).isEqualTo(2);
        assertThat(tv.get("items").get(3).get("texts").get(0).get("name").asText()).as("教师端显示名字").isEqualTo("活动生甲");
        JsonNode grade = call(t, HttpMethod.GET, "/api/teacher/activity/lessons/" + lessonA + "/pages/" + survey + "/survey?scope=GRADE", null);
        assertThat(grade.get("results").get("respondents").asInt()).as("年级段结果含二班").isEqualTo(3);
        assertThat(grade.get("lessons").asInt()).isEqualTo(2);

        // 已有作答：不能删题、不能改题型；可改题目文字
        fails(t, HttpMethod.PUT, cfgUrl, map("items", new Object[]{map("id", jId, "qtype", "JUDGE", "stem", "听懂了吗")}));
        fails(t, HttpMethod.PUT, cfgUrl, map("items", new Object[]{map("id", jId, "qtype", "FILL", "stem", "x"), map("id", sId, "qtype", "SINGLE", "stem", "难度", "options", new String[]{"1", "2", "3"}),
                map("id", mId, "qtype", "MULTI", "stem", "环节", "options", new String[]{"1", "2", "3"}), map("id", fId, "qtype", "FILL", "stem", "建议")}));
        call(t, HttpMethod.PUT, cfgUrl, map("nameMode", "ALL", "items", new Object[]{map("id", jId, "qtype", "JUDGE", "stem", "听懂了吗？"), map("id", sId, "qtype", "SINGLE", "stem", "难度", "options", new String[]{"1", "2", "3"}),
                map("id", mId, "qtype", "MULTI", "stem", "环节", "options", new String[]{"1", "2", "3"}), map("id", fId, "qtype", "FILL", "stem", "建议"),
                map("qtype", "FILL", "stem", "新增题")}));
        assertThat(call(a, HttpMethod.GET, su, null).get("results").get("items").get(3).get("texts").get(0).get("name").asText()).as("nameMode=ALL").isEqualTo("活动生甲");

        // ---- 随机抽题：题号 = 学号 mod 题数 ----
        call(t, HttpMethod.PUT, "/api/teacher/activity/pages/" + take + "/questions", map("questions", new String[]{"说说什么是算法", "举例说明循环结构"}));
        String tu = "/api/student/activity/lessons/" + lessonA + "/pages/" + take + "/take";
        assertThat(call(a, HttpMethod.GET, tu, null).has("question")).isFalse();
        assertThat(call(a, HttpMethod.POST, tu, null).get("no").asInt()).isEqualTo(1);    // 01 % 2 = 1
        JsonNode bd = call(b, HttpMethod.POST, tu, null);                                    // 02 % 2 = 0 → 第 2 题
        assertThat(bd.get("no").asInt()).isEqualTo(2);
        assertThat(bd.get("question").asText()).isEqualTo("举例说明循环结构");
        JsonNode tt = call(t, HttpMethod.GET, "/api/teacher/activity/lessons/" + lessonA + "/pages/" + take + "/take", null);
        assertThat(tt.get("students").size()).isEqualTo(3);
        assertThat(tt.get("students").get(2).get("questionNo").asInt()).as("03 % 2 = 1").isEqualTo(1);
        assertThat(tt.get("students").get(2).get("drawnNo").isNull()).isTrue();

        // ---- 随机点名：只点出席的；一轮内不重复 ----
        String rc = "/api/teacher/activity/lessons/" + lessonA + "/roll-call";
        long first = call(t, HttpMethod.POST, rc, null).get("studentId").asLong();
        long second = call(t, HttpMethod.POST, rc, null).get("studentId").asLong();
        assertThat(first).isNotEqualTo(second);
        assertThat(java.util.Arrays.asList(s1, s2)).contains(first, second);
        assertThat(call(t, HttpMethod.GET, rc, null).get("called").size()).isEqualTo(2);
        call(t, HttpMethod.POST, rc, null);   // 全部点完后重新一轮
        assertThat(call(t, HttpMethod.GET, rc, null).get("called").size()).isEqualTo(1);

        // 缺席学生登记请假原因
        call(t, HttpMethod.PUT, "/api/teacher/lessons/" + lessonA + "/attendance/" + s3, map("status", "LEAVE", "leaveReason", "参加竞赛"));
        JsonNode stu = call(t, HttpMethod.GET, rc, null).get("students");
        assertThat(stu.get(2).get("status").asText() + stu.get(2).get("leaveReason").asText()).isEqualTo("LEAVE参加竞赛");
        // 座位图关注标记
        call(t, HttpMethod.PUT, "/api/teacher/lessons/" + lessonA + "/attendance/" + s1, map("attention", true));
        assertThat(call(t, HttpMethod.GET, "/api/teacher/lessons/" + lessonA + "/attendance", null).get(0).get("attention").asBoolean()).isTrue();
    }
}
