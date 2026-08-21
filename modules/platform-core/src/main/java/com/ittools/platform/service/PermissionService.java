package com.ittools.platform.service;

import com.ittools.platform.domain.ModulePermission;
import com.ittools.platform.repository.ModulePermissionRepository;
import com.ittools.platform.repository.PermissionRepository;
import com.ittools.platform.service.dto.PermissionDtos.ModulePermissionCommand;
import com.ittools.platform.service.dto.PermissionDtos.PermissionView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class PermissionService {
    private final PermissionRepository perms;
    private final ModulePermissionRepository modulePerms;

    public PermissionService(PermissionRepository p, ModulePermissionRepository m) {
        this.perms = p;
        this.modulePerms = m;
    }

    public List<PermissionView> listPermissions() {
        return perms.findAll().stream()
                .map(p -> new PermissionView(p.getId(), p.getCode(), p.getName(), p.getModule()))
                .collect(Collectors.toList());
    }

    @Transactional
    public void upsertModulePermission(ModulePermissionCommand c) {
        ModulePermission mp = modulePerms
                .findByScopeAndScopeRefIdAndPermissionId(c.scope(), c.scopeRefId(), c.permissionId())
                .orElseGet(ModulePermission::new);
        mp.setScope(c.scope());
        mp.setScopeRefId(c.scopeRefId());
        mp.setPermissionId(c.permissionId());
        mp.setEnabled(c.enabled());
        modulePerms.save(mp);
    }
}
