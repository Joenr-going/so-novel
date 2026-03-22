package com.pcdd.sonovel.context;

import lombok.experimental.UtilityClass;

@UtilityClass
public class DownloadContext {

    private final InheritableThreadLocal<String> current = new InheritableThreadLocal<>();

    public void set(String downloadPath) {
        current.set(downloadPath);
    }

    public String get() {
        return current.get();
    }

    public String getOrDefault(String defaultValue) {
        String value = current.get();
        return value == null || value.isBlank() ? defaultValue : value;
    }

    public void clear() {
        current.remove();
    }
}
