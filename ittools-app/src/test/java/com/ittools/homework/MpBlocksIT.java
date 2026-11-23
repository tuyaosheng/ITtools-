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

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 平台图形化编程（掌控板）：作品页打开图形化编程，学生读到课堂环境（MQTT 项目、组名、建议主题）、自动保存 / 读回积木程序，
 * 交 .mpcode 用 Mind+ 规则自动评分；“MQTT 设置正确”规则按学生的配对号判主题（{{MQTT项目}}/Bell{{配对号}}）。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MpBlocksIT extends AbstractPostgresIT {
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

    private static Map<String, Object> map(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
        return m;
    }

    /** 和编辑器存的一样的 project.json：主程序 → MQTT 初始化参数（主题 topic）→ 发起连接 → 发送消息至 Topic_0。 */
    private String project(String topic, String server) throws Exception {
        String settings = om.writeValueAsString(map("platform", "siot", "params", map("siotwww", server, "siotid", "siot", "siotpwd", "dfrobot", "topic", topic)));
        Map<String, Object> blocks = new LinkedHashMap<>();
        blocks.put("s", map("opcode", "dev-DFRobot-handpyEsp32@0.0.6_start", "next", "i", "parent", null, "inputs", map(), "fields", map(), "shadow", false, "topLevel", true, "x", 80, "y", 80));
        blocks.put("i", map("opcode", "ext-DFRobot-MQTT@0.0.4_mqtt_initial", "next", "c", "parent", "s", "inputs", map("SETTING", new Object[]{1, "set"}), "fields", map(), "shadow", false, "topLevel", false));
        blocks.put("set", map("opcode", "mqtt_settings", "next", null, "parent", "i", "inputs", map(), "fields", map("MQTT", new Object[]{settings, null}), "shadow", true, "topLevel", false));
        blocks.put("c", map("opcode", "ext-DFRobot-MQTT@0.0.4_mqtt_mqttConnect", "next", "p", "parent", "i", "inputs", map(), "fields", map(), "shadow", false, "topLevel", false));
        blocks.put("p", map("opcode", "ext-DFRobot-MQTT@0.0.4_mqtt_mqttSend", "next", null, "parent", "c", "inputs", map("MSG", new Object[]{1, new Object[]{10, "叮咚"}}),
                "fields", map("TOPIC", new Object[]{"Topic_0", null}), "shadow", false, "topLevel", false));
        return om.writeValueAsString(map("targets", new Object[]{map("isStage", true, "name", "Stage", "variables", map(), "lists", map(), "blocks", blocks)},
                "extensions", new Object[]{"dev-DFRobot-handpyEsp32@0.0.6", "ext-DFRobot-MQTT@0.0.4"}, "meta", map("semver", "3.0.0")));
    }

    private static byte[] mpcode(String projectJson) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipOutputStream z = new ZipOutputStream(bos, StandardCharsets.UTF_8)) {
            z.putNextEntry(new ZipEntry("project.json"));
            z.write(projectJson.getBytes(StandardCharsets.UTF_8));
            z.closeEntry();
        }
        return bos.toByteArray();
    }

    private JsonNode submit(HttpHeaders s, String url, byte[] data) throws Exception {
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("file", new ByteArrayResource(data) { @Override public String getFilename() { return "门铃程序.mpcode"; } });
        HttpHeaders mp = new HttpHeaders();
        mp.putAll(s);
        mp.setContentType(MediaType.MULTIPART_FORM_DATA);
        ResponseEntity<String> r = rest.exchange(url, HttpMethod.POST, new HttpEntity<>(form, mp), String.class);
        assertThat(r.getStatusCode()).as(r.getBody()).isEqualTo(HttpStatus.OK);
        return om.readTree(r.getBody()).get("data").get("submission");
    }

    @Test
    void blockEditorPageSaveSubmitAndMqttRule() throws Exception {
        HttpHeaders admin = session("{\"loginType\":\"ADMIN\",\"loginName\":\"admin\",\"password\":\"admin123\"}");
        long yearId = call(admin, HttpMethod.POST, "/api/admin/school-years", map("yearCode", "2092", "label", "2092级", "active", true)).get("id").asLong();
        long classId = call(admin, HttpMethod.POST, "/api/admin/classes", map("schoolYearId", yearId, "name", "八(9)班", "displayOrder", 1)).get("id").asLong();
        call(admin, HttpMethod.POST, "/api/admin/users", map("role", "TEACHER", "name", "积木老师", "loginName", "mb_t", "password", "mb-pass-1"));
        HttpHeaders[] st = new HttpHeaders[3];
        long[] sid = new long[3];
        for (int i = 0; i < 3; i++) {
            sid[i] = call(admin, HttpMethod.POST, "/api/admin/users", map("role", "STUDENT", "name", "积木" + i, "studentNo", "0" + (i + 1),
                    "xjh", "MB000" + i, "classId", classId, "enrollYearId", yearId, "password", "mb-pass-1")).get("id").asLong();
            st[i] = session("{\"loginType\":\"STUDENT_XJH\",\"xjh\":\"MB000" + i + "\",\"password\":\"mb-pass-1\"}");
        }
        HttpHeaders t = session("{\"loginType\":\"TEACHER\",\"loginName\":\"mb_t\",\"password\":\"mb-pass-1\"}");

        long course = call(t, HttpMethod.POST, "/api/teacher/courses", map("name", "第13课 门铃")).get("id").asLong();
        long hw = call(t, HttpMethod.POST, "/api/teacher/courses/" + course + "/pages", map("title", "门铃程序", "pageType", "HOMEWORK", "studentVisible", true)).get("id").asLong();
        long plain = call(t, HttpMethod.POST, "/api/teacher/courses/" + course + "/pages", map("title", "普通作品", "pageType", "HOMEWORK", "studentVisible", true)).get("id").asLong();
        // 打开图形化编程：允许的类型里自动加上 mpcode
        JsonNode cfg = call(t, HttpMethod.PUT, "/api/teacher/homework/pages/" + hw + "/config", map("blockEditor", true, "allowedExts", "mp", "autoGrade", true));
        assertThat(cfg.get("blockEditor").asBoolean()).isTrue();
        assertThat(cfg.get("allowedExts").asText()).isEqualTo("mp,mpcode");
        call(t, HttpMethod.PUT, "/api/teacher/grading/pages/" + hw + "/rules", map("rules", new Object[]{
                map("type", "MP_HAS_BLOCK", "param1", "mqtt_mqttSend", "score", 40),
                map("type", "MP_MQTT", "param1", "{{MQTT项目}}/Bell{{配对号}}", "param2", "10.174.233.61", "param3", "siot", "score", 60)}));

        long lesson = call(t, HttpMethod.POST, "/api/teacher/lessons", map("courseId", course, "classId", classId)).get("id").asLong();
        for (HttpHeaders s : st) call(s, HttpMethod.POST, "/api/student/lessons/" + lesson + "/join", map());
        String tg = "/api/teacher/groups/lessons/" + lesson;
        call(t, HttpMethod.POST, tg + "/by-number", map("size", 1, "mode", "CONSECUTIVE"));     // 3 组，各 1 人
        call(t, HttpMethod.PUT, tg + "/groups/1/role", map("role", "门铃", "pairNo", 1));
        call(t, HttpMethod.PUT, tg + "/groups/2/role", map("role", "门铃", "pairNo", 2));
        String proj = call(t, HttpMethod.GET, "/api/teacher/lessons/" + lesson + "/mqtt", null).get("project").asText();

        // ---- 编辑器读课堂环境 ----
        String mb = "/api/student/mpblocks/lessons/" + lesson + "/pages/" + hw;
        JsonNode load = call(st[0], HttpMethod.GET, mb, null);
        assertThat(load.get("project").isNull()).isTrue();
        assertThat(load.get("context").get("mqttProject").asText()).isEqualTo(proj);
        assertThat(load.get("context").get("groupLabel").asText()).isEqualTo("门铃1组");
        assertThat(load.get("context").get("topicSuggestions").toString()).contains(proj + "/G01").contains(proj + "/Bell1");
        // 没打开图形化编程的页面不行
        assertThat(raw(st[0], HttpMethod.GET, "/api/student/mpblocks/lessons/" + lesson + "/pages/" + plain, null).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        // ---- 自动保存、读回；老师能看 ----
        String good = project(proj + "/Bell1", "10.174.233.61");
        call(st[0], HttpMethod.PUT, mb, map("project", om.readTree(good), "code", "_mq.connect()"));
        assertThat(call(st[0], HttpMethod.GET, mb, null).get("project").get("targets").get(0).get("blocks").has("set")).isTrue();
        JsonNode tv = call(t, HttpMethod.GET, "/api/teacher/mpblocks/lessons/" + lesson + "/pages/" + hw + "/students/" + sid[0], null);
        assertThat(tv.get("project").get("targets").get(0).get("blocks").size()).isEqualTo(5);
        assertThat(tv.get("canSubmit").asBoolean()).isFalse();
        // 别的学生读不到这份
        assertThat(call(st[1], HttpMethod.GET, mb, null).get("project").isNull()).isTrue();

        // ---- 交 .mpcode：主题按各自的配对号判 ----
        String up = "/api/student/homework/lessons/" + lesson + "/pages/" + hw;
        JsonNode r0 = submit(st[0], up, mpcode(good));
        assertThat(r0.get("autoScore").asDouble()).isEqualTo(100.0);
        // 第 2 组（门铃2组）抄了第 1 组的主题：主题不对，只得用了积木的分
        JsonNode r1 = submit(st[1], up, mpcode(good));
        assertThat(r1.get("autoScore").asDouble()).isEqualTo(40.0);
        assertThat(r1.get("autoDetail").asText()).contains(proj + "/Bell2");
        // 服务器写错
        JsonNode r2 = submit(st[1], up, mpcode(project(proj + "/Bell2", "192.168.0.1")));
        assertThat(r2.get("autoScore").asDouble()).isEqualTo(40.0);
        assertThat(r2.get("autoDetail").asText()).contains("服务器地址");
        JsonNode r3 = submit(st[1], up, mpcode(project(proj + "/Bell2", "10.174.233.61")));
        assertThat(r3.get("autoScore").asDouble()).isEqualTo(100.0);
        // 改了规则后重新评分：仍按每个学生的配对号
        call(t, HttpMethod.POST, "/api/teacher/homework/lessons/" + lesson + "/pages/" + hw + "/regrade", null);
        JsonNode list = call(t, HttpMethod.GET, "/api/teacher/homework/lessons/" + lesson + "/pages/" + hw, null).get("students");
        assertThat(list.get(0).get("autoScore").asDouble()).isEqualTo(100.0);
        assertThat(list.get(1).get("autoScore").asDouble()).isEqualTo(100.0);
    }
}
