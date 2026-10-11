package com.ittools.lab.service;

import com.ittools.platform.domain.Role;
import com.ittools.platform.security.AppUserDetails;
import com.ittools.platform.security.LoginPolicy;
import com.ittools.platform.web.PolicyViolationException;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 固定机位登录（原工作模式"班级+姓名+固定机位"）：
 * 学生所在班级启用固定机位且已绑定座位时，只能从绑定机位的 IP 登录。
 */
@Component
public class FixedSeatLoginPolicy implements LoginPolicy {
    private final LabClassSeatService seats;

    public FixedSeatLoginPolicy(LabClassSeatService seats) {
        this.seats = seats;
    }

    @Override
    public void check(AppUserDetails user, String clientIp) {
        if (user.role() != Role.STUDENT) return;
        Map<String, Object> info = seats.fixedSeatInfo(user.userId());
        if (!Boolean.TRUE.equals(info.get("fixed"))) return;
        Object ip = info.get("ip");
        if (ip == null) return; // 尚未分配座位的学生不受限制
        if (!ip.equals(clientIp)) {
            throw new PolicyViolationException("本班已启用固定机位，请在你的机位 " + info.get("pc") + " 上登录");
        }
    }
}
