package com.ittools.homework.web;

import com.ittools.homework.service.MpAgentService;
import com.ittools.platform.security.CurrentUser;
import com.ittools.platform.web.ApiResponse;
import com.ittools.platform.web.ClientIp;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 掌控板助手（学生机本机编译、上传，和 Mind+ 上传模式一样）。
 * <ul>
 *   <li>/api/mpagent/agent/**：学生机上的助手调用，不登录，按来访 IP 认电脑；</li>
 *   <li>/api/mpagent/mindplus/{name}.js：编辑器取 Mind+ 的积木代码生成文件；</li>
 *   <li>/api/mpagent/client/**：编辑器（已登录的学生或老师）查助手状态、发编译上传任务、看进度。</li>
 * </ul>
 */
@RestController
public class MpAgentController {
    private final MpAgentService svc;

    public MpAgentController(MpAgentService svc) {
        this.svc = svc;
    }

    // ---------- 助手 ----------
    @GetMapping("/api/mpagent/agent/poll")
    public ApiResponse<Map<String, Object>> poll(@RequestParam(required = false) String version,
                                                 @RequestParam(defaultValue = "false") boolean mindplus,
                                                 @RequestParam(required = false) String port,
                                                 @RequestParam(required = false) String note,
                                                 @RequestParam(defaultValue = "20") int wait,
                                                 HttpServletRequest req) throws InterruptedException {
        return ApiResponse.ok(svc.poll(ClientIp.of(req), version, mindplus, port, note, wait));
    }

    @PostMapping("/api/mpagent/agent/jobs/{id}")
    public ApiResponse<Void> progress(@PathVariable String id, @RequestBody Map<String, Object> body, HttpServletRequest req) {
        svc.progress(ClientIp.of(req), id, str(body.get("status")), str(body.get("log")), str(body.get("message")));
        return ApiResponse.ok(null);
    }

    @PutMapping(value = "/api/mpagent/agent/mindplus/{name:.+}", consumes = MediaType.ALL_VALUE)
    public ApiResponse<Void> putFile(@PathVariable String name, @RequestBody byte[] data) {
        svc.putMindplusFile(name, data);
        return ApiResponse.ok(null);
    }

    // ---------- 编辑器 ----------
    @GetMapping("/api/mpagent/mindplus/{name:.+}.js")
    public ResponseEntity<byte[]> file(@PathVariable String name) {
        byte[] d = svc.getMindplusFile(name);
        if (d == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok().contentType(MediaType.valueOf("application/javascript;charset=UTF-8"))
                .cacheControl(CacheControl.maxAge(1, TimeUnit.DAYS)).body(d);
    }

    @GetMapping("/api/mpagent/client/status")
    public ApiResponse<Map<String, Object>> status(HttpServletRequest req) {
        return ApiResponse.ok(svc.status(ClientIp.of(req)));
    }

    @PostMapping("/api/mpagent/client/jobs")
    public ApiResponse<Map<String, Object>> submit(@RequestBody Map<String, Object> body, HttpServletRequest req) {
        return ApiResponse.ok(svc.submit(ClientIp.of(req), CurrentUser.id(), str(body.get("code"))));
    }

    @GetMapping("/api/mpagent/client/jobs/{id}")
    public ApiResponse<Map<String, Object>> job(@PathVariable String id, @RequestParam(defaultValue = "0") int logFrom, HttpServletRequest req) {
        return ApiResponse.ok(svc.job(ClientIp.of(req), CurrentUser.id(), id, logFrom));
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }
}
