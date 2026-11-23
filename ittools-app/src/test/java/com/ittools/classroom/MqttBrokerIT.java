package com.ittools.classroom;

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
import java.io.DataInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 内置 MQTT 服务器（代替 SIoT）：开课自动生成项目名、账号密码校验、“项目/设备”消息按课堂保存、订阅转发、
 * 页面占位符 {{MQTT项目}} / {{媒体:文件名}}、课程包 media/ 导入与下载。MQTT 客户端用手写的 3.1.1 报文。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MqttBrokerIT extends AbstractPostgresIT {
    @Autowired
    TestRestTemplate rest;
    final ObjectMapper om = new ObjectMapper();

    // ---------------- HTTP ----------------
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

    private JsonNode call(HttpHeaders s, HttpMethod m, String url, Object body) throws Exception {
        ResponseEntity<String> r = rest.exchange(url, m, new HttpEntity<>(body == null ? null : om.writeValueAsString(body), s), String.class);
        assertThat(r.getStatusCode()).as(url + " → " + r.getBody()).isEqualTo(HttpStatus.OK);
        return om.readTree(r.getBody()).get("data");
    }

    private static Map<String, Object> map(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
        return m;
    }

    // ---------------- 手写 MQTT 3.1.1 客户端 ----------------
    private static void str(ByteArrayOutputStream b, String s) {
        byte[] d = s.getBytes(StandardCharsets.UTF_8);
        b.write(d.length >> 8);
        b.write(d.length & 0xFF);
        b.write(d, 0, d.length);
    }

    private static byte[] packet(int header, ByteArrayOutputStream body) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(header);
        int len = body.size();
        do {
            int digit = len % 128;
            len /= 128;
            out.write(len > 0 ? digit | 0x80 : digit);
        } while (len > 0);
        byte[] b = body.toByteArray();
        out.write(b, 0, b.length);
        return out.toByteArray();
    }

    /** 读一个报文，返回 [类型字节, 内容...] */
    private static byte[] read(InputStream in) throws Exception {
        DataInputStream d = new DataInputStream(in);
        int h = d.readUnsignedByte();
        int len = 0, mul = 1, b;
        do {
            b = d.readUnsignedByte();
            len += (b & 0x7F) * mul;
            mul *= 128;
        } while ((b & 0x80) != 0);
        byte[] body = new byte[len + 1];
        body[0] = (byte) h;
        d.readFully(body, 1, len);
        return body;
    }

    /** 连接，返回 CONNACK 返回码（0 = 接受） */
    private static int connect(Socket s, String clientId, String user, String pass) throws Exception {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        str(b, "MQTT");
        b.write(4);            // 3.1.1
        b.write(0xC2);         // 用户名 + 密码 + clean session
        b.write(0);
        b.write(60);           // keepalive
        str(b, clientId);
        str(b, user);
        str(b, pass);
        s.getOutputStream().write(packet(0x10, b));
        byte[] ack = read(s.getInputStream());
        assertThat(ack[0] & 0xF0).isEqualTo(0x20);
        return ack[2];
    }

    private static void publish(Socket s, String topic, String payload) throws Exception {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        str(b, topic);
        byte[] p = payload.getBytes(StandardCharsets.UTF_8);
        b.write(p, 0, p.length);
        s.getOutputStream().write(packet(0x30, b));
        s.getOutputStream().flush();
    }

    private static void subscribe(Socket s, String filter) throws Exception {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        b.write(0);
        b.write(1);            // packet id
        str(b, filter);
        b.write(0);            // QoS 0
        s.getOutputStream().write(packet(0x82, b));
        byte[] ack = read(s.getInputStream());
        assertThat(ack[0] & 0xF0).isEqualTo(0x90);
    }

    private static int freePort() throws Exception {
        try (ServerSocket ss = new ServerSocket(0)) {
            return ss.getLocalPort();
        }
    }

    private JsonNode device(JsonNode view, String name) {
        for (JsonNode d : view.get("devices")) if (name.equals(d.get("device").asText())) return d;
        return null;
    }

    private byte[] zip(String json, String mediaName, byte[] media) throws Exception {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        try (ZipOutputStream z = new ZipOutputStream(bo, StandardCharsets.UTF_8)) {
            z.putNextEntry(new ZipEntry("course.json"));
            z.write(json.getBytes(StandardCharsets.UTF_8));
            z.closeEntry();
            z.putNextEntry(new ZipEntry("media/" + mediaName));
            z.write(media);
            z.closeEntry();
        }
        return bo.toByteArray();
    }

    @Test
    void builtInBrokerStoresMessagesPerLesson() throws Exception {
        HttpHeaders admin = session("{\"loginType\":\"ADMIN\",\"loginName\":\"admin\",\"password\":\"admin123\"}");
        int port = freePort();
        call(admin, HttpMethod.PUT, "/api/admin/settings", map("mqtt.enabled", "true", "mqtt.port", String.valueOf(port)));
        JsonNode st = call(admin, HttpMethod.GET, "/api/admin/mqtt/status", null);
        assertThat(st.get("running").asBoolean()).as(st.toString()).isTrue();
        assertThat(st.get("port").asInt()).isEqualTo(port);

        long yearId = call(admin, HttpMethod.POST, "/api/admin/school-years", map("yearCode", "2094", "label", "2094级", "active", true)).get("id").asLong();
        long classId = call(admin, HttpMethod.POST, "/api/admin/classes", map("schoolYearId", yearId, "name", "八(9)班", "displayOrder", 1)).get("id").asLong();
        call(admin, HttpMethod.POST, "/api/admin/users", map("role", "TEACHER", "name", "物联老师", "loginName", "mq_t", "password", "mq-pass-1"));
        call(admin, HttpMethod.POST, "/api/admin/users", map("role", "STUDENT", "name", "物联生", "studentNo", "01", "xjh", "MQ0001",
                "classId", classId, "enrollYearId", yearId, "password", "mq-pass-1"));
        HttpHeaders t = session("{\"loginType\":\"TEACHER\",\"loginName\":\"mq_t\",\"password\":\"mq-pass-1\"}");
        HttpHeaders s = session("{\"loginType\":\"STUDENT_XJH\",\"xjh\":\"MQ0001\",\"password\":\"mq-pass-1\"}");

        long course = call(t, HttpMethod.POST, "/api/teacher/courses", map("name", "物联课")).get("id").asLong();
        String pages = "/api/teacher/courses/" + course + "/pages";
        long text = call(t, HttpMethod.POST, pages, map("title", "任务单", "pageType", "TEXT", "studentVisible", true,
                "content", "<p>本课项目：{{MQTT项目}}，第 1 组主题 {{MQTT项目}}/G01，服务器 {{服务器IP}}</p>")).get("id").asLong();
        call(t, HttpMethod.POST, pages, map("title", "物联网消息", "pageType", "MQTT", "studentVisible", true));

        JsonNode lesson = call(t, HttpMethod.POST, "/api/teacher/lessons", map("courseId", course, "classId", classId));
        long lid = lesson.get("id").asLong();
        String project = lesson.get("mqttProject").asText();
        assertThat(project).matches("B89_\\d{4}_\\d{4}");
        call(t, HttpMethod.PUT, "/api/teacher/lessons/" + lid + "/pages/" + text, map("open", true));
        call(s, HttpMethod.POST, "/api/student/lessons/" + lid + "/join", map());
        String content = call(s, HttpMethod.GET, "/api/student/lessons/" + lid + "/pages/" + text, null).get("content").asText();
        assertThat(content).contains("本课项目：" + project).contains(project + "/G01").contains("服务器 ").doesNotContain("{{");
        assertThat(call(t, HttpMethod.GET, "/api/teacher/lessons/" + lid + "/pages/" + text, null).get("content").asText()).contains(project);
        // 课程编辑里保留原文
        assertThat(call(t, HttpMethod.GET, "/api/teacher/courses/" + course, null).get("pages").get(0).get("content").asText()).contains("{{MQTT项目}}");

        // 密码错：拒绝连接（返回码 4）
        try (Socket bad = new Socket("127.0.0.1", port)) {
            assertThat(connect(bad, "bad", "siot", "dfrobox")).isEqualTo(4);
        }
        try (Socket sub = new Socket("127.0.0.1", port); Socket pub = new Socket("127.0.0.1", port)) {
            sub.setSoTimeout(5000);
            assertThat(connect(sub, "display", "siot", "dfrobot")).isEqualTo(0);
            subscribe(sub, project + "/+");
            assertThat(connect(pub, "board", "siot", "dfrobot")).isEqualTo(0);
            publish(pub, project + "/G01", "第1组：你好，MQTT！");
            publish(pub, project + "/g01", "小写");
            publish(pub, "noslash", "不保存");
            publish(pub, "B00_0101_0000/G01", "上个班的板子");
            // 订阅者收到同项目的消息（第13课显示组）
            byte[] got = read(sub.getInputStream());
            assertThat(got[0] & 0xF0).isEqualTo(0x30);
            assertThat(new String(got, StandardCharsets.UTF_8)).contains(project + "/G01").contains("第1组：你好，MQTT！");
        }
        JsonNode view = null;
        for (int i = 0; i < 40; i++) {
            view = call(t, HttpMethod.GET, "/api/teacher/lessons/" + lid + "/mqtt", null);
            if (view.get("devices").size() >= 2) break;
            Thread.sleep(250);
        }
        assertThat(view.get("project").asText()).isEqualTo(project);
        assertThat(view.get("devices").size()).as(view.toString()).isEqualTo(2);
        assertThat(device(view, "G01").get("messages").get(0).get("payload").asText()).isEqualTo("第1组：你好，MQTT！");
        assertThat(device(view, "g01").get("count").asInt()).isEqualTo(1);
        JsonNode sv = call(s, HttpMethod.GET, "/api/student/lessons/" + lid + "/mqtt", null);
        assertThat(sv.get("devices").size()).isEqualTo(2);
        assertThat(sv.get("username").asText()).isEqualTo("siot");

        // 老师从网页发一条
        call(t, HttpMethod.POST, "/api/teacher/lessons/" + lid + "/mqtt/publish", map("device", "G02", "payload", "网页发的"));
        assertThat(device(call(t, HttpMethod.GET, "/api/teacher/lessons/" + lid + "/mqtt", null), "G02")).isNotNull();
        assertThat(call(t, HttpMethod.DELETE, "/api/teacher/lessons/" + lid + "/mqtt", null).asInt()).isEqualTo(3);
        assertThat(call(t, HttpMethod.GET, "/api/teacher/lessons/" + lid + "/mqtt", null).get("devices").size()).isEqualTo(0);
        call(t, HttpMethod.POST, "/api/teacher/lessons/" + lid + "/end", map());

        // 课程包：media/ 导入，正文 {{媒体:文件名}} 换成下载地址
        byte[] video = "FAKE-MP4-DATA".getBytes(StandardCharsets.UTF_8);
        String manifest = om.writeValueAsString(map("format", "ittools-course", "version", 1, "course", map("name", "带视频的课"),
                "pages", new Object[]{map("title", "任务单", "pageType", "TEXT", "content", "<video src=\"{{媒体:步骤1.mp4}}\" controls></video>")}));
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("file", new ByteArrayResource(zip(manifest, "步骤1.mp4", video)) { @Override public String getFilename() { return "v.ittools.zip"; } });
        HttpHeaders mp = new HttpHeaders();
        mp.putAll(t);
        mp.setContentType(MediaType.MULTIPART_FORM_DATA);
        ResponseEntity<String> imp = rest.exchange("/api/teacher/courses/import-pack", HttpMethod.POST, new HttpEntity<>(form, mp), String.class);
        assertThat(imp.getStatusCode()).as(imp.getBody()).isEqualTo(HttpStatus.OK);
        JsonNode r = om.readTree(imp.getBody()).get("data");
        assertThat(r.get("media").asInt()).isEqualTo(1);
        long c2 = r.get("course").get("id").asLong();
        long p2 = call(t, HttpMethod.GET, "/api/teacher/courses/" + c2, null).get("pages").get(0).get("id").asLong();
        long l2 = call(t, HttpMethod.POST, "/api/teacher/lessons", map("courseId", c2, "classId", classId)).get("id").asLong();
        call(t, HttpMethod.PUT, "/api/teacher/lessons/" + l2 + "/pages/" + p2, map("open", true));
        String c = call(s, HttpMethod.GET, "/api/student/lessons/" + l2 + "/pages/" + p2, null).get("content").asText();
        String url = "/api/classroom/courses/" + c2 + "/media/%E6%AD%A5%E9%AA%A41.mp4";
        assertThat(c).contains("src=\"" + url + "\"");
        ResponseEntity<byte[]> dl = rest.exchange(java.net.URI.create(rest.getRootUri() + url), HttpMethod.GET, new HttpEntity<>(s), byte[].class);
        assertThat(dl.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(dl.getBody()).isEqualTo(video);
        // 导出再带上 media/
        ResponseEntity<byte[]> exp = rest.exchange("/api/teacher/courses/" + c2 + "/pack", HttpMethod.GET, new HttpEntity<>(t), byte[].class);
        assertThat(exp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(new String(exp.getBody(), StandardCharsets.ISO_8859_1)).contains("media/");
        call(admin, HttpMethod.PUT, "/api/admin/settings", map("mqtt.port", "1883"));
    }
}
