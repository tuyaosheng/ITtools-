package com.ittools.lab.web;

import com.ittools.lab.service.LabClassSeatService;
import com.ittools.lab.service.LabGroupService;
import com.ittools.lab.service.LabRoomService;
import com.ittools.lab.service.LabWorkstationService;
import com.ittools.platform.service.dto.ImportResult;
import com.ittools.platform.web.ApiResponse;
import com.ittools.platform.web.Body;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/** 管理员：机房模型/座位布局、分组模型、机位批量导入、班级固定机位。 */
@RestController
@RequestMapping("/api/admin/lab")
public class AdminLabController {
    private final com.ittools.lab.service.NetworkControlService network;
    private final LabRoomService rooms;
    private final LabGroupService groups;
    private final LabClassSeatService classSeats;
    private final LabWorkstationService workstations;

    public AdminLabController(com.ittools.lab.service.NetworkControlService network, LabRoomService rooms, LabGroupService groups, LabClassSeatService classSeats,
                              LabWorkstationService workstations) {
        this.network = network;
        this.rooms = rooms;
        this.groups = groups;
        this.classSeats = classSeats;
        this.workstations = workstations;
    }

    // ---- 机位批量导入 ----
    @PostMapping("/workstations/import")
    public ApiResponse<ImportResult> importWorkstations(@RequestParam("file") MultipartFile file,
                                                       @RequestParam(defaultValue = "false") boolean deleteExisting) throws IOException {
        return ApiResponse.ok(workstations.importFile(file.getInputStream(), file.getOriginalFilename(), deleteExisting));
    }

    @GetMapping("/workstations/template")
    public ResponseEntity<byte[]> template() {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("机位导入模板.xlsx", StandardCharsets.UTF_8).build().toString())
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(workstations.template());
    }

    // ---- 机房模型 ----
    @GetMapping("/rooms")
    public ApiResponse<List<Map<String, Object>>> rooms() {
        return ApiResponse.ok(rooms.list());
    }

    @PostMapping("/rooms")
    public ApiResponse<Map<String, Object>> createRoom(@RequestBody Map<String, Object> body) {
        Body b = Body.of(body);
        return ApiResponse.ok(rooms.create(b.reqStr("name", "名称"), b.intv("rows", 0), b.intv("cols", 0)));
    }

    @PutMapping("/rooms/{id}")
    public ApiResponse<Map<String, Object>> updateRoom(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Body b = Body.of(body);
        return ApiResponse.ok(rooms.update(id, b.reqStr("name", "名称"), b.intv("rows", 0), b.intv("cols", 0)));
    }

    @DeleteMapping("/rooms/{id}")
    public ApiResponse<Void> deleteRoom(@PathVariable Long id) {
        rooms.delete(id);
        return ApiResponse.ok(null);
    }

    @GetMapping("/rooms/{id}/layout")
    public ApiResponse<List<Map<String, Object>>> layout(@PathVariable Long id) {
        return ApiResponse.ok(rooms.layout(id));
    }

    @PutMapping("/rooms/{id}/layout")
    public ApiResponse<List<Map<String, Object>>> saveLayout(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(rooms.saveLayout(id, Body.of(body).maps("cells")));
    }

    @PostMapping("/rooms/{id}/auto-fill")
    public ApiResponse<List<Map<String, Object>>> autoFill(@PathVariable Long id) {
        return ApiResponse.ok(rooms.autoFill(id));
    }

    // ---- 分组模型 ----
    // ---- 机房方案（文本排版 + 规则生成机位） ----
    @GetMapping("/rooms/{id}/scheme")
    public ApiResponse<Map<String, Object>> scheme(@PathVariable Long id) {
        return ApiResponse.ok(rooms.scheme(id));
    }

    @PostMapping("/rooms/scheme")
    public ApiResponse<Map<String, Object>> createScheme(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(rooms.saveScheme(null, body));
    }

    @PutMapping("/rooms/{id}/scheme")
    public ApiResponse<Map<String, Object>> saveScheme(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(rooms.saveScheme(id, body));
    }

    @PostMapping("/workstations/swap-ip")
    public ApiResponse<Void> swapIp(@RequestBody Map<String, Object> body) {
        Object a = body.get("a"), b = body.get("b");
        rooms.swapIp(a instanceof Number ? ((Number) a).longValue() : null, b instanceof Number ? ((Number) b).longValue() : null);
        return ApiResponse.ok(null);
    }

    // ---- 网络控制器 CTR ----
    @GetMapping("/network/test")
    public ApiResponse<Map<String, Object>> networkTest() {
        return ApiResponse.ok(network.test());
    }

    @GetMapping("/group-models")
    public ApiResponse<List<Map<String, Object>>> groupModels() {
        return ApiResponse.ok(groups.list());
    }

    @PostMapping("/group-models")
    public ApiResponse<Map<String, Object>> createGroupModel(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(groups.create(Body.of(body).reqStr("name", "名称")));
    }

    @PutMapping("/group-models/{id}")
    public ApiResponse<Void> renameGroupModel(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        groups.rename(id, Body.of(body).reqStr("name", "名称"));
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/group-models/{id}")
    public ApiResponse<Void> deleteGroupModel(@PathVariable Long id) {
        groups.delete(id);
        return ApiResponse.ok(null);
    }

    @GetMapping("/group-models/{id}/members")
    public ApiResponse<List<Map<String, Object>>> members(@PathVariable Long id) {
        return ApiResponse.ok(groups.members(id));
    }

    @PutMapping("/group-models/{id}/members")
    public ApiResponse<List<Map<String, Object>>> saveMembers(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(groups.saveMembers(id, Body.of(body).maps("assignments")));
    }

    @PostMapping("/group-models/{id}/auto")
    public ApiResponse<List<Map<String, Object>>> autoGroup(@PathVariable Long id, @RequestParam int size) {
        return ApiResponse.ok(groups.autoGroup(id, size));
    }

    // ---- 班级机房 / 固定机位 ----
    @GetMapping("/class-configs")
    public ApiResponse<List<Map<String, Object>>> classConfigs() {
        return ApiResponse.ok(classSeats.classConfigs());
    }

    @PutMapping("/class-configs/{classId}")
    public ApiResponse<Void> saveClassConfig(@PathVariable Long classId, @RequestBody Map<String, Object> body) {
        Body b = Body.of(body);
        classSeats.saveConfig(classId, b.lng("roomId"), b.bool("fixedSeat", false));
        return ApiResponse.ok(null);
    }

    @GetMapping("/class-configs/{classId}/students")
    public ApiResponse<List<Map<String, Object>>> classStudents(@PathVariable Long classId) {
        return ApiResponse.ok(classSeats.students(classId));
    }

    @PutMapping("/class-configs/{classId}/students")
    public ApiResponse<List<Map<String, Object>>> saveBindings(@PathVariable Long classId, @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(classSeats.saveBindings(classId, Body.of(body).maps("bindings")));
    }

    @PostMapping("/class-configs/{classId}/auto-assign")
    public ApiResponse<List<Map<String, Object>>> autoAssign(@PathVariable Long classId) {
        return ApiResponse.ok(classSeats.autoAssign(classId));
    }
}
