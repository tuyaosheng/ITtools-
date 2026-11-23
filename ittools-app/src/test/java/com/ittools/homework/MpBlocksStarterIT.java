package com.ittools.homework;

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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 平台图形化编程：作品页的起始程序（老师配，复制课程、课程包带上），
 * 学生第一次打开时可选起始程序或自己以前的程序（别的课的存档、随时练习），只能取自己的。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MpBlocksStarterIT extends AbstractPostgresIT {
    @Autowired
    TestRestTemplate rest;
    final ObjectMapper om = new ObjectMapper();

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

    private ResponseEntity<String> upload(HttpHeaders s, String url, byte[] data, String filename) {
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("file", new ByteArrayResource(data) { @Override public String getFilename() { return filename; } });
        HttpHeaders mp = new HttpHeaders();
        mp.putAll(s);
        mp.setContentType(MediaType.MULTIPART_FORM_DATA);
        return rest.exchange(url, HttpMethod.POST, new HttpEntity<>(form, mp), String.class);
    }

    private static Map<String, Object> map(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
        return m;
    }

    private static Map<String, Object> prog(String tag) {
        Map<String, Object> blocks = map(
                "a", map("opcode", "dev-DFRobot-handpyEsp32@0.0.6_start", "topLevel", true),
                "b", map("opcode", "control_forever", "parent", "a"),
                "c", map("opcode", "control_wait", "parent", "b"));
        return map("targets", new Object[]{map("isStage", false, "blocks", blocks)}, "meta", map("semver", "3.0.0", "tag", tag));
    }

    /** 只有“ESP32 主程序 + 循环执行”的空程序 */
    private static Map<String, Object> blank() {
        Map<String, Object> blocks = map(
                "a", map("opcode", "dev-DFRobot-handpyEsp32@0.0.6_start", "topLevel", true),
                "b", map("opcode", "control_forever", "parent", "a"));
        return map("targets", new Object[]{map("isStage", false, "blocks", blocks)}, "meta", map("semver", "3.0.0"));
    }

    private long pageId(HttpHeaders t, long course, String title) throws Exception {
        for (JsonNode p : call(t, HttpMethod.GET, "/api/teacher/courses/" + course, null).get("pages")) {
            if (title.equals(p.get("title").asText())) return p.get("id").asLong();
        }
        throw new AssertionError("no page " + title);
    }

    @Test
    void starterProgramAndEarlierPrograms() throws Exception {
        HttpHeaders admin = session("{\"loginType\":\"ADMIN\",\"loginName\":\"admin\",\"password\":\"admin123\"}");
        long yearId = call(admin, HttpMethod.POST, "/api/admin/school-years", map("yearCode", "2059", "label", "2059级", "active", true)).get("id").asLong();
        long classId = call(admin, HttpMethod.POST, "/api/admin/classes", map("schoolYearId", yearId, "name", "八(7)班", "displayOrder", 1)).get("id").asLong();
        call(admin, HttpMethod.POST, "/api/admin/users", map("role", "TEACHER", "name", "起始老师", "loginName", "st_t", "password", "st-pass-1"));
        call(admin, HttpMethod.POST, "/api/admin/users", map("role", "TEACHER", "name", "别的老师", "loginName", "st_t2", "password", "st-pass-1"));
        call(admin, HttpMethod.POST, "/api/admin/users", map("role", "STUDENT", "name", "起始甲", "studentNo", "01", "xjh", "ST0001", "classId", classId, "enrollYearId", yearId, "password", "st-pass-1"));
        call(admin, HttpMethod.POST, "/api/admin/users", map("role", "STUDENT", "name", "起始乙", "studentNo", "02", "xjh", "ST0002", "classId", classId, "enrollYearId", yearId, "password", "st-pass-1"));
        HttpHeaders t = session("{\"loginType\":\"TEACHER\",\"loginName\":\"st_t\",\"password\":\"st-pass-1\"}");
        HttpHeaders t2 = session("{\"loginType\":\"TEACHER\",\"loginName\":\"st_t2\",\"password\":\"st-pass-1\"}");
        HttpHeaders s1 = session("{\"loginType\":\"STUDENT_XJH\",\"xjh\":\"ST0001\",\"password\":\"st-pass-1\"}");
        HttpHeaders s2 = session("{\"loginType\":\"STUDENT_XJH\",\"xjh\":\"ST0002\",\"password\":\"st-pass-1\"}");

        // ---- 上节课：一个作品页，学生甲做了程序 ----
        long c1 = call(t, HttpMethod.POST, "/api/teacher/courses", map("name", "第12课")).get("id").asLong();
        long hw1 = call(t, HttpMethod.POST, "/api/teacher/courses/" + c1 + "/pages", map("title", "门锁程序", "pageType", "HOMEWORK", "studentVisible", true)).get("id").asLong();
        call(t, HttpMethod.PUT, "/api/teacher/homework/pages/" + hw1 + "/config", map("blockEditor", true));
        long l1 = call(t, HttpMethod.POST, "/api/teacher/lessons", map("courseId", c1, "classId", classId)).get("id").asLong();
        call(s1, HttpMethod.POST, "/api/student/lessons/" + l1 + "/join", map());
        call(t, HttpMethod.PUT, "/api/teacher/lessons/" + l1 + "/pages/" + hw1, map("open", true));
        String w1 = "/api/student/mpblocks/lessons/" + l1 + "/pages/" + hw1;
        assertThat(call(s1, HttpMethod.GET, w1, null).get("fresh").asBoolean()).isTrue();
        call(s1, HttpMethod.PUT, w1, map("project", prog("lesson12"), "code", "x"));
        assertThat(call(s1, HttpMethod.GET, w1, null).has("fresh")).isFalse();
        call(s1, HttpMethod.PUT, "/api/student/mpblocks/free", map("project", prog("free"), "code", "y"));
        call(t, HttpMethod.POST, "/api/teacher/lessons/" + l1 + "/end", map());

        // ---- 这节课：作品页配起始程序 ----
        long c2 = call(t, HttpMethod.POST, "/api/teacher/courses", map("name", "第13课")).get("id").asLong();
        long hw2 = call(t, HttpMethod.POST, "/api/teacher/courses/" + c2 + "/pages", map("title", "门铃程序", "pageType", "HOMEWORK", "studentVisible", true)).get("id").asLong();
        JsonNode cfg = call(t, HttpMethod.PUT, "/api/teacher/homework/pages/" + hw2 + "/config", map("blockEditor", true));
        assertThat(cfg.get("allowHistory").asBoolean()).isTrue();
        assertThat(cfg.get("hasStarter").asBoolean()).isFalse();
        String st = "/api/teacher/mpblocks/pages/" + hw2 + "/starter";
        assertThat(call(t, HttpMethod.GET, st, null).get("project").isNull()).isTrue();
        call(t, HttpMethod.PUT, st, map("project", prog("starter13")));
        assertThat(call(t, HttpMethod.GET, st, null).get("project").get("meta").get("tag").asText()).isEqualTo("starter13");
        assertThat(call(t, HttpMethod.GET, "/api/teacher/homework/pages/" + hw2 + "/config", null).get("hasStarter").asBoolean()).isTrue();
        // 别的老师改不了
        assertThat(raw(t2, HttpMethod.PUT, st, map("project", prog("x"))).getStatusCode()).isNotEqualTo(HttpStatus.OK);
        // 学生不能用老师接口
        assertThat(raw(s1, HttpMethod.GET, st, null).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        long l2 = call(t, HttpMethod.POST, "/api/teacher/lessons", map("courseId", c2, "classId", classId)).get("id").asLong();
        call(s1, HttpMethod.POST, "/api/student/lessons/" + l2 + "/join", map());
        call(s2, HttpMethod.POST, "/api/student/lessons/" + l2 + "/join", map());
        call(t, HttpMethod.PUT, "/api/teacher/lessons/" + l2 + "/pages/" + hw2, map("open", true));
        String w2 = "/api/student/mpblocks/lessons/" + l2 + "/pages/" + hw2;
        JsonNode first = call(s1, HttpMethod.GET, w2, null);
        assertThat(first.get("fresh").asBoolean()).isTrue();
        assertThat(first.get("project").isNull()).isTrue();          // 不再自动套用别的课堂的存档
        assertThat(first.get("starter").get("meta").get("tag").asText()).isEqualTo("starter13");
        assertThat(first.get("context").get("allowHistory").asBoolean()).isTrue();

        // 我以前的程序：上节课作品页 + 随时练习；当前页不列
        JsonNode hist = call(s1, HttpMethod.GET, "/api/student/mpblocks/history?lessonId=" + l2 + "&pageId=" + hw2, null);
        assertThat(hist.size()).isEqualTo(2);
        JsonNode work = null;
        for (JsonNode h : hist) if ("work".equals(h.get("kind").asText())) work = h;
        assertThat(work).isNotNull();
        assertThat(work.get("courseName").asText()).isEqualTo("第12课");
        assertThat(work.get("pageTitle").asText()).isEqualTo("门锁程序");
        String hw = "/api/student/mpblocks/history/work/" + work.get("lessonId").asLong() + "/" + work.get("pageId").asLong();
        assertThat(call(s1, HttpMethod.GET, hw, null).get("project").get("meta").get("tag").asText()).isEqualTo("lesson12");
        // 乙取不到甲的
        assertThat(raw(s2, HttpMethod.GET, hw, null).getStatusCode()).isNotEqualTo(HttpStatus.OK);
        assertThat(call(s2, HttpMethod.GET, "/api/student/mpblocks/history", null).size()).isEqualTo(0);

        // 选好后存进本页，之后就不再是 fresh；历史列表里也不列本页
        call(s1, HttpMethod.PUT, w2, map("project", prog("lesson12"), "code", "x"));
        JsonNode again = call(s1, HttpMethod.GET, w2, null);
        assertThat(again.has("fresh")).isFalse();
        // 存过以后“打开”菜单里还能回到起始程序
        assertThat(again.get("starter").get("meta").get("tag").asText()).isEqualTo("starter13");
        assertThat(call(s1, HttpMethod.GET, "/api/student/mpblocks/history?lessonId=" + l2 + "&pageId=" + hw2, null).size()).isEqualTo(2);
        // 只有“ESP32 主程序 + 循环执行”的空程序不列进“我以前的程序”
        call(s2, HttpMethod.PUT, w2, map("project", blank(), "code", ""));
        call(s2, HttpMethod.PUT, "/api/student/mpblocks/free", map("project", blank(), "code", ""));
        assertThat(call(s2, HttpMethod.GET, "/api/student/mpblocks/history", null).size()).isEqualTo(0);

        // 关掉“允许选用以前的程序”
        call(t, HttpMethod.PUT, "/api/teacher/homework/pages/" + hw2 + "/config", map("blockEditor", true, "allowHistory", false));
        assertThat(call(s2, HttpMethod.GET, w2, null).get("context").get("allowHistory").asBoolean()).isFalse();

        // ---- 复制课程、课程包都带上起始程序和开关 ----
        long cp = call(t, HttpMethod.POST, "/api/teacher/courses/" + c2 + "/copy", null).get("id").asLong();
        long cpHw = pageId(t, cp, "门铃程序");
        assertThat(call(t, HttpMethod.GET, "/api/teacher/mpblocks/pages/" + cpHw + "/starter", null).get("project").get("meta").get("tag").asText()).isEqualTo("starter13");
        assertThat(call(t, HttpMethod.GET, "/api/teacher/homework/pages/" + cpHw + "/config", null).get("allowHistory").asBoolean()).isFalse();

        ResponseEntity<byte[]> ex = rest.exchange("/api/teacher/courses/" + c2 + "/pack", HttpMethod.GET, new HttpEntity<>(t), byte[].class);
        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.OK);
        ResponseEntity<String> imp = upload(t, "/api/teacher/courses/import-pack", ex.getBody(), "第13课.ittools.zip");
        assertThat(imp.getStatusCode()).as(imp.getBody()).isEqualTo(HttpStatus.OK);
        long nc = om.readTree(imp.getBody()).get("data").get("course").get("id").asLong();
        long nHw = pageId(t, nc, "门铃程序");
        assertThat(call(t, HttpMethod.GET, "/api/teacher/mpblocks/pages/" + nHw + "/starter", null).get("project").get("meta").get("tag").asText()).isEqualTo("starter13");
        assertThat(call(t, HttpMethod.GET, "/api/teacher/homework/pages/" + nHw + "/config", null).get("allowHistory").asBoolean()).isFalse();

        // 清除起始程序
        call(t, HttpMethod.DELETE, st, null);
        assertThat(call(t, HttpMethod.GET, "/api/teacher/homework/pages/" + hw2 + "/config", null).get("hasStarter").asBoolean()).isFalse();
    }
}
