package com.ittools.platform.web;

import com.ittools.platform.domain.User;
import com.ittools.platform.repository.UserRepository;
import com.ittools.platform.security.CurrentUser;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** 当前用户自助修改密码（原 teacher/password.aspx、student/password.aspx）。 */
@RestController
public class AccountController {
    private final UserRepository users;
    private final PasswordEncoder encoder;

    public AccountController(UserRepository users, PasswordEncoder encoder) {
        this.users = users;
        this.encoder = encoder;
    }

    @PostMapping("/api/account/password")
    @Transactional
    public ApiResponse<Void> changePassword(@RequestBody Map<String, String> body) {
        String oldPw = body.get("oldPassword");
        String newPw = body.get("newPassword");
        if (newPw == null || newPw.length() < 4) {
            throw new IllegalArgumentException("新密码至少 4 位");
        }
        User u = users.findById(CurrentUser.id()).orElseThrow(() -> new IllegalArgumentException("用户不存在"));
        if (oldPw == null || !encoder.matches(oldPw, u.getPasswordHash())) {
            throw new IllegalArgumentException("原密码不正确");
        }
        u.setPasswordHash(encoder.encode(newPw));
        return ApiResponse.ok(null);
    }
}
