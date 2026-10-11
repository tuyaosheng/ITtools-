package com.ittools.platform.security;

import com.ittools.platform.domain.Role;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.io.Serializable;
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

    public AppUserDetails(Long userId, String username, String passwordHash, Role role, boolean enabled) {
        this(userId, username, passwordHash, role, enabled, username, null);
    }

    public AppUserDetails(Long userId, String username, String passwordHash, Role role, boolean enabled,
                          String displayName, Long classId) {
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

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override public String getPassword() { return passwordHash; }
    @Override public String getUsername() { return username; }
    @Override public boolean isAccountNonExpired() { return true; }
    @Override public boolean isAccountNonLocked() { return true; }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled() { return enabled; }
}
