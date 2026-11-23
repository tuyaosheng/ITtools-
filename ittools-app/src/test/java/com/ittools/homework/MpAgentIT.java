package com.ittools.homework;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ittools.homework.service.MpAgentService;
import com.ittools.support.AbstractPostgresIT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 掌控板助手（Mind+ 方式上传）：学生机上的助手按 IP 报到、长轮询领任务、回报进度；
 * 浏览器只能看到、只能发给本机的助手；Mind+ 的积木代码生成文件只收登记过的版本。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MpAgentIT extends AbstractPostgresIT {
    @Autowired
    TestRestTemplate rest;
    final ObjectMapper om = new ObjectMapper();

    private HttpHeaders session(String body, String ip) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        h.add("X-Forwarded-For", ip);
        ResponseEntity<String> login = rest.postForEntity("/api/auth/login", new HttpEntity<>(body, h), String.class);
        assertThat(login.getStatusCode()).as(body).isEqualTo(HttpStatus.OK);
        List<String> setCookies = login.getHeaders().get(HttpHeaders.SET_COOKIE);
        HttpHeaders sess = new HttpHeaders();
        sess.setContentType(MediaType.APPLICATION_JSON);
        sess.add("X-Forwarded-For", ip);
        sess.add(HttpHeaders.COOKIE, setCookies.stream().map(sc -> sc.split(";", 2)[0]).collect(Collectors.joining("; ")));
        for (String sc : setCookies) {
            String pair = sc.split(";", 2)[0].trim();
            if (pair.startsWith("XSRF-TOKEN=")) sess.add("X-XSRF-TOKEN", pair.substring("XSRF-TOKEN=".length()));
        }
        return sess;
    }

    private static HttpHeaders agent(String ip) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        h.add("X-Forwarded-For", ip);
        return h;
    }

    private ResponseEntity<String> raw(HttpHeaders s, HttpMethod m, String url, Object body) throws Exception {
        Object b = body == null ? null : body instanceof byte[] ? body : om.writeValueAsString(body);
        return rest.exchange(url, m, new HttpEntity<>(b, s), String.class);
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

    @Test
    void agentCompilesJobsForItsOwnComputerOnly() throws Exception {
        HttpHeaders admin = session("{\"loginType\":\"ADMIN\",\"loginName\":\"admin\",\"password\":\"admin123\"}", "10.9.0.1");
        long yearId = call(admin, HttpMethod.POST, "/api/admin/school-years", map("yearCode", "2057", "label", "2057级", "active", true)).get("id").asLong();
        long classId = call(admin, HttpMethod.POST, "/api/admin/classes", map("schoolYearId", yearId, "name", "八(7)班", "displayOrder", 1)).get("id").asLong();
        call(admin, HttpMethod.POST, "/api/admin/users", map("role", "STUDENT", "name", "助手甲", "studentNo", "01", "xjh", "AG0001", "classId", classId, "enrollYearId", yearId, "password", "ag-pass-1"));
        call(admin, HttpMethod.POST, "/api/admin/users", map("role", "STUDENT", "name", "助手乙", "studentNo", "02", "xjh", "AG0002", "classId", classId, "enrollYearId", yearId, "password", "ag-pass-1"));
        HttpHeaders s1 = session("{\"loginType\":\"STUDENT_XJH\",\"xjh\":\"AG0001\",\"password\":\"ag-pass-1\"}", "10.9.0.21");
        HttpHeaders s2 = session("{\"loginType\":\"STUDENT_XJH\",\"xjh\":\"AG0002\",\"password\":\"ag-pass-1\"}", "10.9.0.22");

        // 助手没开：状态离线，不能发任务
        assertThat(call(s1, HttpMethod.GET, "/api/mpagent/client/status", null).get("online").asBoolean()).isFalse();
        assertThat(raw(s1, HttpMethod.POST, "/api/mpagent/client/jobs", map("code", "void setup(){}")).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        // 助手报到（不登录）
        JsonNode hello = call(agent("10.9.0.21"), HttpMethod.GET, "/api/mpagent/agent/poll?wait=0&mindplus=true&port=COM5&version=1.0.0", null);
        assertThat(hello.get("job")).isNull();
        JsonNode st = call(s1, HttpMethod.GET, "/api/mpagent/client/status", null);
        assertThat(st.get("online").asBoolean()).isTrue();
        assertThat(st.get("mindplus").asBoolean()).isTrue();
        assertThat(st.get("port").asText()).isEqualTo("COM5");
        // 别的电脑看不到这个助手
        assertThat(call(s2, HttpMethod.GET, "/api/mpagent/client/status", null).get("online").asBoolean()).isFalse();

        // 学生发任务，助手长轮询领到
        CompletableFuture<JsonNode> polled = CompletableFuture.supplyAsync(() -> {
            try {
                return call(agent("10.9.0.21"), HttpMethod.GET, "/api/mpagent/agent/poll?wait=10&mindplus=true&port=COM5", null);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        Thread.sleep(500);
        JsonNode job = call(s1, HttpMethod.POST, "/api/mpagent/client/jobs", map("code", "#include <MPython.h>\nvoid setup(){}\nvoid loop(){}\n"));
        String id = job.get("id").asText();
        assertThat(job.get("status").asText()).isEqualTo("QUEUED");
        JsonNode got = polled.get(15, TimeUnit.SECONDS).get("job");
        assertThat(got.get("id").asText()).isEqualTo(id);
        assertThat(got.get("code").asText()).contains("MPython.h");
        assertThat(call(s1, HttpMethod.GET, "/api/mpagent/client/jobs/" + id, null).get("status").asText()).isEqualTo("TAKEN");

        // 别的电脑的助手不能回报这个任务；别的学生看不到
        assertThat(raw(agent("10.9.0.22"), HttpMethod.POST, "/api/mpagent/agent/jobs/" + id, map("status", "DONE")).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(raw(s2, HttpMethod.GET, "/api/mpagent/client/jobs/" + id, null).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        // 回报进度，日志可以分段取
        call(agent("10.9.0.21"), HttpMethod.POST, "/api/mpagent/agent/jobs/" + id, map("status", "COMPILING", "log", "第一行\n", "message", "正在编译"));
        JsonNode v1 = call(s1, HttpMethod.GET, "/api/mpagent/client/jobs/" + id, null);
        assertThat(v1.get("status").asText()).isEqualTo("COMPILING");
        assertThat(v1.get("log").asText()).isEqualTo("第一行\n");
        int end = v1.get("logEnd").asInt();
        call(agent("10.9.0.21"), HttpMethod.POST, "/api/mpagent/agent/jobs/" + id, map("status", "DONE", "log", "第二行\n", "message", "上传完成"));
        JsonNode v2 = call(s1, HttpMethod.GET, "/api/mpagent/client/jobs/" + id + "?logFrom=" + end, null);
        assertThat(v2.get("status").asText()).isEqualTo("DONE");
        assertThat(v2.get("log").asText()).isEqualTo("第二行\n");
        assertThat(v2.get("message").asText()).isEqualTo("上传完成");

        // 连点两次上传：前一个还在排队的作废，助手只领到最新的
        String a = call(s1, HttpMethod.POST, "/api/mpagent/client/jobs", map("code", "// A")).get("id").asText();
        String b = call(s1, HttpMethod.POST, "/api/mpagent/client/jobs", map("code", "// B")).get("id").asText();
        assertThat(call(s1, HttpMethod.GET, "/api/mpagent/client/jobs/" + a, null).get("status").asText()).isEqualTo("FAILED");
        JsonNode got2 = call(agent("10.9.0.21"), HttpMethod.GET, "/api/mpagent/agent/poll?wait=2&mindplus=true", null).get("job");
        assertThat(got2.get("id").asText()).isEqualTo(b);
        assertThat(got2.get("code").asText()).isEqualTo("// B");

        // 助手没找到 Mind+：不能发任务
        call(agent("10.9.0.21"), HttpMethod.GET, "/api/mpagent/agent/poll?wait=0&mindplus=false", null);
        assertThat(raw(s1, HttpMethod.POST, "/api/mpagent/client/jobs", map("code", "// C")).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void mindplusFilesOnlyAcceptRegisteredVersions() throws Exception {
        HttpHeaders octet = agent("10.9.0.30");
        octet.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        // 不认识的名字、内容不对：都不收
        assertThat(raw(octet, HttpMethod.PUT, "/api/mpagent/agent/mindplus/evil", "module.exports=1".getBytes(StandardCharsets.UTF_8)).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(raw(octet, HttpMethod.PUT, "/api/mpagent/agent/mindplus/dev-DFRobot-handpyEsp32@0.0.6", "alert(1)".getBytes(StandardCharsets.UTF_8)).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(rest.getForEntity("/api/mpagent/mindplus/evil.js", String.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        // 平台登记的三个文件
        assertThat(MpAgentService.MINDPLUS_FILES).containsOnlyKeys("dev-DFRobot-handpyEsp32@0.0.6", "ext-DFRobot-MQTT@0.0.4", "ext-DFRobot-WIFI@0.0.5");
        // 报到时告诉助手缺哪些（装了 Mind+ 的教师机上可能一个都不缺）
        JsonNode need = call(agent("10.9.0.30"), HttpMethod.GET, "/api/mpagent/agent/poll?wait=0", null).get("need");
        assertThat(need.isArray()).isTrue();
    }
}
