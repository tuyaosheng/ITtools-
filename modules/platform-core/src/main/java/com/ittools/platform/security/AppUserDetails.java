package com.ittools.platform.security;

import com.ittools.platform.domain.Role;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

public class AppUserDetails implements UserDetails, Serializable {
    private static final long serialVersionUID = 1L;

    private final Long userId;
    private final String username;
    private final String passwordHash;
    private final Role role;
    private final boolean enabled;
    /** 真实姓名（显示用），与登录标识 username 区分。 */
    private final String displayName;
    /** 学生所属班级 id；教师/管理员为 null。 */
    private final Long classId;
    /** 教师兼任管理员：额外授予 ROLE_ADMIN，主角色仍是 TEACHER。 */
    private final boolean adminAccess;

    public AppUserDetails(Long userId, String username, String passwordHash, Role role, boolean enabled) {
        this(userId, username, passwordHash, role, enabled, username, null);
    }

    public AppUserDetails(Long userId, String username, String passwordHash, Role role, boolean enabled,
                          String displayName, Long classId) {
        this(userId, username, passwordHash, role, enabled, displayName, classId, false);
    }

    public AppUserDetails(Long userId, String username, String passwordHash, Role role, boolean enabled,
                          String displayName, Long classId, boolean adminAccess) {
        this.adminAccess = role == Role.TEACHER && adminAccess;
        this.userId = userId;
        this.username = username;
        this.passwordHash = passwordHash;
        this.role = role;
        this.enabled = enabled;
        this.displayName = displayName;
        this.classId = classId;
    }

    public Long userId() { return userId; }
    public Role role() { return role; }
    public String displayName() { return displayName; }
    public Long classId() { return classId; }
    /** 能进管理后台：管理员本身，或兼任管理员的教师。 */
    public boolean canAdmin() { return role == Role.ADMIN || adminAccess; }
    public boolean adminAccess() { return adminAccess; }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        if (adminAccess) {
            return Arrays.asList(new SimpleGrantedAuthority("ROLE_" + role.name()), new SimpleGrantedAuthority("ROLE_ADMIN"));
        }
        return Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override public String getPassword() { return passwordHash; }
    @Override public String getUsername() { return username; }
    @Override public boolean isAccountNonExpired() { return true; }
    @Override public boolean isAccountNonLocked() { return true; }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled() { return enabled; }
}
