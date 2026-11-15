package com.ittools.platform.settings;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/** 系统设置保存后发布，监听者按改动的键决定要不要重新加载（如内置 MQTT 服务器换端口后重启）。 */
public class SettingsChangedEvent {
    private final Set<String> keys;

    public SettingsChangedEvent(Set<String> keys) {
        this.keys = Collections.unmodifiableSet(new HashSet<>(keys));
    }

    public Set<String> getKeys() {
        return keys;
    }
}
