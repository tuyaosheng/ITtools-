package com.ittools.lab.service;

import com.ittools.lab.domain.LabWorkstation;
import com.ittools.lab.repository.LabWorkstationRepository;
import com.ittools.lab.service.dto.LabWorkstationDtos.LabWorkstationCommand;
import com.ittools.lab.service.dto.LabWorkstationDtos.LabWorkstationView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class LabWorkstationService {
    private final LabWorkstationRepository workstations;

    public LabWorkstationService(LabWorkstationRepository w) {
        this.workstations = w;
    }

    @Transactional(readOnly = true)
    public List<LabWorkstationView> list() {
        return workstations.findAllByOrderByPcNameAsc().stream()
                .map(this::view)
                .collect(Collectors.toList());
    }

    @Transactional
    public LabWorkstationView create(LabWorkstationCommand c) {
        validate(c);
        if (workstations.existsByPcName(c.pcName())) {
            throw new IllegalArgumentException("计算机名已存在");
        }
        if (workstations.existsByIpAddress(c.ipAddress())) {
            throw new IllegalArgumentException("IP地址已存在");
        }
        return view(workstations.save(new LabWorkstation(c.pcName(), c.ipAddress())));
    }

    @Transactional
    public LabWorkstationView update(Long id, LabWorkstationCommand c) {
        validate(c);
        LabWorkstation w = workstations.findById(id).orElseThrow(() -> new IllegalArgumentException("机位不存在"));
        workstations.findByPcName(c.pcName())
                .filter(other -> !other.getId().equals(id))
                .ifPresent(other -> { throw new IllegalArgumentException("计算机名已存在"); });
        if (!c.ipAddress().equals(w.getIpAddress()) && workstations.existsByIpAddress(c.ipAddress())) {
            throw new IllegalArgumentException("IP地址已存在");
        }
        w.setPcName(c.pcName());
        w.setIpAddress(c.ipAddress());
        return view(w);
    }

    @Transactional
    public void delete(Long id) {
        workstations.deleteById(id);
    }

    /**
     * 批量导入（原 iplist.aspx "批量导入"）：表头需包含"计算机名""IP地址"；
     * deleteExisting=true 时先清空机位表（原"删除已有IP"选项）。
     */
    @Transactional
    public com.ittools.platform.service.dto.ImportResult importFile(java.io.InputStream in, String filename, boolean deleteExisting) {
        java.util.List<java.util.List<String>> rows;
        try {
            rows = new com.ittools.platform.service.SpreadsheetReader().read(in, filename);
        } catch (Exception e) {
            throw new IllegalArgumentException("无法读取文件：" + e.getMessage());
        }
        if (rows.isEmpty()) throw new IllegalArgumentException("文件为空");
        java.util.List<String> header = rows.get(0);
        int nameCol = header.indexOf("计算机名");
        int ipCol = header.indexOf("IP地址");
        if (nameCol < 0 || ipCol < 0) throw new IllegalArgumentException("表头需包含：计算机名、IP地址");
        if (deleteExisting) {
            workstations.deleteAllInBatch();
            workstations.flush();
        }
        com.ittools.platform.service.dto.ImportResult result = new com.ittools.platform.service.dto.ImportResult();
        java.util.Set<String> seenName = new java.util.HashSet<>();
        java.util.Set<String> seenIp = new java.util.HashSet<>();
        for (int r = 1; r < rows.size(); r++) {
            java.util.List<String> cells = rows.get(r);
            String name = nameCol < cells.size() ? cells.get(nameCol) : "";
            String ip = ipCol < cells.size() ? cells.get(ipCol) : "";
            if (name.isEmpty() && ip.isEmpty()) continue;
            result.incrementTotal();
            if (name.isEmpty() || ip.isEmpty()) { result.addError(r, "计算机名和IP地址都不能为空"); continue; }
            if (!IPV4.matcher(ip).matches()) { result.addError(r, "IP地址格式不正确：" + ip); continue; }
            if (!seenName.add(name) || workstations.existsByPcName(name)) { result.addError(r, "计算机名重复：" + name); continue; }
            if (!seenIp.add(ip) || workstations.existsByIpAddress(ip)) { result.addError(r, "IP地址重复：" + ip); continue; }
            workstations.save(new LabWorkstation(name, ip));
            result.incrementImported();
        }
        return result;
    }

    /** 导入模板（.xlsx）。 */
    public byte[] template() {
        return com.ittools.platform.service.ImportTemplates.build("机位",
                new String[]{"计算机名", "IP地址"},
                new String[][]{{"PC-01", "192.168.1.101"}, {"PC-02", "192.168.1.102"}},
                new int[]{16, 18},
                "计算机名和 IP 地址都必填，且各自不能重复（与已有机位重复的行会被跳过并提示）。",
                "如果机房座位号与 IP 有规律（如 1 号座位 .101、往后递增），更省事的做法是在“机房模型 → 方案设置”里按规则一次生成全部机位。");
    }

    private static final java.util.regex.Pattern IPV4 = java.util.regex.Pattern.compile(
            "^((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)\\.){3}(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)$");

    private void validate(LabWorkstationCommand c) {
        if (c.pcName() == null || c.pcName().trim().isEmpty()) {
            throw new IllegalArgumentException("计算机名不能为空");
        }
        if (c.ipAddress() == null || c.ipAddress().trim().isEmpty()) {
            throw new IllegalArgumentException("IP地址不能为空");
        }
        if (!IPV4.matcher(c.ipAddress().trim()).matches()) {
            throw new IllegalArgumentException("IP地址格式不正确");
        }
    }

    private LabWorkstationView view(LabWorkstation w) {
        return new LabWorkstationView(w.getId(), w.getPcName(), w.getIpAddress());
    }
}
