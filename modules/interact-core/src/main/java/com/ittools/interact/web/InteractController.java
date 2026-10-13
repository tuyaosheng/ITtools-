package com.ittools.interact.web;

import com.ittools.classroom.service.LessonService;
import com.ittools.interact.service.ForumService;
import com.ittools.interact.service.InteractService;
import com.ittools.platform.security.AppUserDetails;
import com.ittools.platform.security.CurrentUser;
import com.ittools.platform.web.ApiResponse;
import com.ittools.platform.web.Body;
import com.ittools.platform.web.ClientIp;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
public class InteractController {
    private final InteractService svc;
    private final ForumService forum;
    private final LessonService lessons;

    public InteractController(InteractService svc, ForumService forum, LessonService lessons) {
        this.svc = svc;
        this.forum = forum;
        this.lessons = lessons;
    }

    // ================= 教师 =================
    @GetMapping("/api/teacher/interact/lessons/{id}")
    public ApiResponse<Map<String, Object>> teacherOverview(@PathVariable Long id) {
        lessons.requireTeacher(CurrentUser.id(), id);
        Map<String, Object> m = new HashMap<>();
        m.put("helps", svc.helps(id));
        m.put("helpers", svc.helpers(id));
        m.put("broadcasts", svc.broadcasts(id));
        m.put("monitor", svc.monitor(id));
        m.put("activity", svc.activity(id));
        return ApiResponse.ok(m);
    }

    @GetMapping("/api/teacher/interact/lessons/{id}/dwell")
    public ApiResponse<Map<String, Object>> dwell(@PathVariable Long id) {
        lessons.requireTeacher(CurrentUser.id(), id);
        return ApiResponse.ok(svc.dwell(id));
    }

    @PostMapping("/api/teacher/interact/helps/{helpId}/resolve")
    public ApiResponse<Void> resolve(@PathVariable Long helpId) {
        svc.resolveHelp(CurrentUser.id(), helpId);
        return ApiResponse.ok(null);
    }

    @PostMapping("/api/teacher/interact/lessons/{id}/broadcast")
    public ApiResponse<Map<String, Object>> broadcast(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(svc.broadcast(CurrentUser.id(), id, Body.of(body).str("content")));
    }

    @GetMapping("/api/teacher/interact/lessons/{id}/discuss/{pageId}")
    public ApiResponse<Map<String, Object>> teacherDiscuss(@PathVariable Long id, @PathVariable Long pageId,
                                                           @RequestParam(required = false) Integer groupNo) {
        lessons.requireTeacher(CurrentUser.id(), id);
        Map<String, Object> m = new HashMap<>();
        m.put("posts", svc.discussPosts(id, pageId, groupNo));
        m.put("conclusions", svc.conclusions(id, pageId));
        return ApiResponse.ok(m);
    }

    @PostMapping("/api/teacher/interact/lessons/{id}/discuss/{pageId}")
    public ApiResponse<Map<String, Object>> teacherPost(@PathVariable Long id, @PathVariable Long pageId, @RequestBody Map<String, Object> body) {
        Body b = Body.of(body);
        return ApiResponse.ok(svc.discussPost(CurrentUser.id(), "TEACHER", id, pageId, b.str("content"), false, b.intv("groupNo")));
    }

    @PostMapping("/api/teacher/interact/lessons/{id}/answer-first")
    public ApiResponse<Map<String, Object>> startRound(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Body b = Body.of(body);
        return ApiResponse.ok(svc.startAnswerFirst(CurrentUser.id(), id, b.lng("pageId"), b.str("question")));
    }

    @GetMapping("/api/teacher/interact/lessons/{id}/answer-first/{pageId}")
    public ApiResponse<List<Map<String, Object>>> rounds(@PathVariable Long id, @PathVariable Long pageId) {
        lessons.requireTeacher(CurrentUser.id(), id);
        return ApiResponse.ok(svc.rounds(id, pageId));
    }

    @PostMapping("/api/teacher/interact/answer-first/{roundId}/close")
    public ApiResponse<Map<String, Object>> closeRound(@PathVariable Long roundId) {
        return ApiResponse.ok(svc.closeAnswerFirst(CurrentUser.id(), roundId));
    }

    @PutMapping("/api/teacher/interact/answer-first/{roundId}/entries/{studentId}")
    public ApiResponse<Map<String, Object>> judge(@PathVariable Long roundId, @PathVariable Long studentId, @RequestBody Map<String, Object> body) {
        Body b = Body.of(body);
        return ApiResponse.ok(svc.judgeAnswerFirst(CurrentUser.id(), roundId, studentId, b.has("correct") ? b.bool("correct", false) : null, b.intv("score")));
    }

    // ================= 学生 =================
    @GetMapping("/api/student/interact/lessons/{id}")
    public ApiResponse<Map<String, Object>> studentOverview(@PathVariable Long id) {
        Long me = CurrentUser.id();
        lessons.requireStudent(me, id);
        Map<String, Object> m = new HashMap<>();
        m.put("myHelp", svc.myHelp(me, id));
        m.put("classmates", svc.classmatesInLesson(me, id));
        m.put("broadcasts", svc.broadcasts(id));
        m.put("group", svc.myGroup(me, id));
        m.put("answerFirst", svc.latestRound(id, null));
        return ApiResponse.ok(m);
    }

    @PostMapping("/api/student/interact/lessons/{id}/help/thanks")
    public ApiResponse<Void> thanks(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        svc.thankHelper(CurrentUser.id(), id, Body.of(body).lng("helperId"));
        return ApiResponse.ok(null);
    }

    @PostMapping("/api/student/interact/lessons/{id}/help")
    public ApiResponse<Map<String, Object>> raiseHand(@PathVariable Long id, @RequestBody Map<String, Object> body, HttpServletRequest req) {
        return ApiResponse.ok(svc.raiseHand(CurrentUser.id(), id, Body.of(body).str("message"), ClientIp.of(req)));
    }

    @DeleteMapping("/api/student/interact/lessons/{id}/help")
    public ApiResponse<Void> cancelHand(@PathVariable Long id) {
        svc.cancelHand(CurrentUser.id(), id);
        return ApiResponse.ok(null);
    }

    @PostMapping("/api/student/interact/lessons/{id}/heartbeat")
    public ApiResponse<Void> heartbeat(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Body b = Body.of(body);
        svc.heartbeat(CurrentUser.id(), id, b.lng("pageId"), b.str("pageTitle"), !Boolean.FALSE.equals(body.get("visible")));
        return ApiResponse.ok(null);
    }

    @GetMapping("/api/student/interact/lessons/{id}/discuss/{pageId}")
    public ApiResponse<Map<String, Object>> studentDiscuss(@PathVariable Long id, @PathVariable Long pageId) {
        Long me = CurrentUser.id();
        lessons.studentPage(me, id, pageId);
        int g = lessons.groupNoOf(id, me);
        Map<String, Object> m = new HashMap<>();
        m.put("groupNo", g);
        m.put("posts", svc.discussPosts(id, pageId, g));
        m.put("conclusions", svc.conclusions(id, pageId));
        return ApiResponse.ok(m);
    }

    @PostMapping("/api/student/interact/lessons/{id}/discuss/{pageId}")
    public ApiResponse<Map<String, Object>> studentPost(@PathVariable Long id, @PathVariable Long pageId, @RequestBody Map<String, Object> body) {
        lessons.studentPage(CurrentUser.id(), id, pageId);
        Body b = Body.of(body);
        return ApiResponse.ok(svc.discussPost(CurrentUser.id(), "STUDENT", id, pageId, b.str("content"), b.bool("conclusion", false), null));
    }

    @GetMapping("/api/student/interact/lessons/{id}/answer-first/{pageId}")
    public ApiResponse<Map<String, Object>> studentRound(@PathVariable Long id, @PathVariable Long pageId) {
        lessons.requireStudent(CurrentUser.id(), id);
        return ApiResponse.ok(svc.latestRound(id, pageId));
    }

    @PostMapping("/api/student/interact/answer-first/{roundId}")
    public ApiResponse<Map<String, Object>> answer(@PathVariable Long roundId, @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(svc.answerFirst(CurrentUser.id(), roundId, Body.of(body).str("answer")));
    }

    // ================= 论坛（师生共用） =================
    @GetMapping("/api/forum/courses/{courseId}/topics")
    public ApiResponse<List<Map<String, Object>>> topics(@PathVariable Long courseId) {
        AppUserDetails u = CurrentUser.get();
        forum.requireAccess(u.userId(), u.role().name(), courseId);
        return ApiResponse.ok(forum.topics(courseId));
    }

    @PostMapping("/api/forum/courses/{courseId}/topics")
    public ApiResponse<Long> createTopic(@PathVariable Long courseId, @RequestBody Map<String, Object> body) {
        AppUserDetails u = CurrentUser.get();
        forum.requireAccess(u.userId(), u.role().name(), courseId);
        Body b = Body.of(body);
        return ApiResponse.ok(forum.create(u.userId(), courseId, b.str("title"), b.str("content")));
    }

    @GetMapping("/api/forum/topics/{topicId}")
    public ApiResponse<Map<String, Object>> topic(@PathVariable Long topicId) {
        AppUserDetails u = CurrentUser.get();
        forum.requireAccess(u.userId(), u.role().name(), forum.courseOfTopic(topicId));
        return ApiResponse.ok(forum.topic(topicId));
    }

    @PostMapping("/api/forum/topics/{topicId}/replies")
    public ApiResponse<Void> reply(@PathVariable Long topicId, @RequestBody Map<String, Object> body) {
        AppUserDetails u = CurrentUser.get();
        forum.requireAccess(u.userId(), u.role().name(), forum.courseOfTopic(topicId));
        forum.reply(u.userId(), topicId, Body.of(body).str("content"));
        return ApiResponse.ok(null);
    }

    @PutMapping("/api/forum/topics/{topicId}/flags")
    public ApiResponse<Void> flags(@PathVariable Long topicId, @RequestBody Map<String, Object> body) {
        if (!forum.isCourseTeacher(CurrentUser.id(), forum.courseOfTopic(topicId))) throw new IllegalArgumentException("仅课程教师可操作");
        Body b = Body.of(body);
        forum.flags(topicId, b.has("pinned") ? b.bool("pinned", false) : null, b.has("recommended") ? b.bool("recommended", false) : null);
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/api/forum/topics/{topicId}")
    public ApiResponse<Void> deleteTopic(@PathVariable Long topicId) {
        AppUserDetails u = CurrentUser.get();
        forum.delete(u.userId(), u.role().name(), topicId);
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/api/forum/replies/{replyId}")
    public ApiResponse<Void> deleteReply(@PathVariable Long replyId) {
        AppUserDetails u = CurrentUser.get();
        forum.deleteReply(u.userId(), u.role().name(), replyId);
        return ApiResponse.ok(null);
    }
}
