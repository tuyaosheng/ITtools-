package com.ittools.lab.web;

import com.ittools.lab.service.LabQueryService;
import com.ittools.platform.web.ApiResponse;
import com.ittools.platform.web.ClientIp;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;

@RestController
public class LabInfoController {
    private final LabQueryService query;

    public LabInfoController(LabQueryService query) {
        this.query = query;
    }

    /** 当前请求来自哪台机位（任意已登录用户可调用；学生顶栏显示"机器名"）。 */
    @GetMapping("/api/lab/whoami")
    public ApiResponse<Map<String, Object>> whoami(HttpServletRequest req) {
        return ApiResponse.ok(query.workstationByIp(ClientIp.of(req)));
    }

    /** 教师开课时选择分组模型。 */
    @GetMapping("/api/teacher/lab/group-models")
    public ApiResponse<List<Map<String, Object>>> groupModels() {
        return ApiResponse.ok(query.groupModels());
    }
}
