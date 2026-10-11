package com.ittools.interact.web;

import com.ittools.interact.service.MessageService;
import com.ittools.interact.service.QaService;
import com.ittools.platform.security.AppUserDetails;
import com.ittools.platform.security.CurrentUser;
import com.ittools.platform.web.ApiResponse;
import com.ittools.platform.web.Body;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 系统消息 + 师生答疑。 */
@RestController
public class MessageController {
    private final MessageService messages;
    private final QaService qa;

    public MessageController(MessageService messages, QaService qa) {
        this.messages = messages;
        this.qa = qa;
    }

    /** 菜单角标：未读消息数；教师另含待答复问题数。 */
    @GetMapping("/api/notices/summary")
    public ApiResponse<Map<String, Object>> summary() {
        AppUserDetails u = CurrentUser.get();
        Map<String, Object> m = new HashMap<>();
        m.put("unreadMessages", messages.unreadCount(u.userId()));
        m.put("pendingQuestions", "TEACHER".equals(u.role().name()) ? qa.pendingCount(u.userId()) : 0);
        return ApiResponse.ok(m);
    }

    // ================= 收件箱（所有角色） =================
    @GetMapping("/api/messages/inbox")
    public ApiResponse<List<Map<String, Object>>> inbox() {
        return ApiResponse.ok(messages.inbox(CurrentUser.id()));
    }

    @PostMapping("/api/messages/inbox/{id}/read")
    public ApiResponse<Void> read(@PathVariable Long id) {
        messages.markRead(CurrentUser.id(), id);
        return ApiResponse.ok(null);
    }

    @PostMapping("/api/messages/inbox/read-all")
    public ApiResponse<Integer> readAll() {
        return ApiResponse.ok(messages.markAllRead(CurrentUser.id()));
    }

    @DeleteMapping("/api/messages/inbox/{id}")
    public ApiResponse<Void> remove(@PathVariable Long id) {
        messages.removeFromInbox(CurrentUser.id(), id);
        return ApiResponse.ok(null);
    }

    // ================= 教师发送 =================
    @PostMapping("/api/teacher/messages")
    public ApiResponse<Map<String, Object>> send(@RequestBody Map<String, Object> body) {
        Body b = Body.of(body);
        return ApiResponse.ok(messages.send(CurrentUser.id(), b.str("title"), b.str("content"), b.longs("classIds"), b.longs("studentIds")));
    }

    @GetMapping("/api/teacher/messages")
    public ApiResponse<List<Map<String, Object>>> sent() {
        return ApiResponse.ok(messages.sent(CurrentUser.id()));
    }

    @GetMapping("/api/teacher/messages/{id}")
    public ApiResponse<Map<String, Object>> sentDetail(@PathVariable Long id) {
        return ApiResponse.ok(messages.sentDetail(CurrentUser.id(), id));
    }

    @DeleteMapping("/api/teacher/messages/{id}")
    public ApiResponse<Void> retract(@PathVariable Long id) {
        messages.retract(CurrentUser.id(), id);
        return ApiResponse.ok(null);
    }

    // ================= 师生答疑：学生 =================
    @GetMapping("/api/student/qa/teachers")
    public ApiResponse<List<Map<String, Object>>> teachers() {
        return ApiResponse.ok(qa.teachersFor(CurrentUser.id()));
    }

    @GetMapping("/api/student/qa")
    public ApiResponse<List<Map<String, Object>>> mine() {
        return ApiResponse.ok(qa.mine(CurrentUser.id()));
    }

    @PostMapping("/api/student/qa")
    public ApiResponse<Map<String, Object>> ask(@RequestBody Map<String, Object> body) {
        Body b = Body.of(body);
        return ApiResponse.ok(qa.ask(CurrentUser.id(), b.reqLng("teacherId", "老师"), b.str("title"), b.str("content")));
    }

    @DeleteMapping("/api/student/qa/{id}")
    public ApiResponse<Void> deleteMine(@PathVariable Long id) {
        qa.deleteMine(CurrentUser.id(), id);
        return ApiResponse.ok(null);
    }

    @GetMapping("/api/qa/public")
    public ApiResponse<List<Map<String, Object>>> publicList(@RequestParam(required = false) String keyword) {
        return ApiResponse.ok(qa.publicList(keyword));
    }

    // ================= 师生答疑：教师 =================
    @GetMapping("/api/teacher/qa")
    public ApiResponse<List<Map<String, Object>>> teacherList(@RequestParam(defaultValue = "false") boolean pending) {
        return ApiResponse.ok(qa.teacherList(CurrentUser.id(), pending));
    }

    @PutMapping("/api/teacher/qa/{id}/answer")
    public ApiResponse<Void> answer(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Body b = Body.of(body);
        qa.answer(CurrentUser.id(), id, b.str("answer"), b.bool("isPublic", false));
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/api/teacher/qa/{id}")
    public ApiResponse<Void> teacherDelete(@PathVariable Long id) {
        qa.teacherDelete(CurrentUser.id(), id);
        return ApiResponse.ok(null);
    }
}
