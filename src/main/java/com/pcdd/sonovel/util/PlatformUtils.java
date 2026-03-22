package com.pcdd.sonovel.util;

import cn.hutool.system.SystemUtil;
import lombok.experimental.UtilityClass;

@UtilityClass
public class PlatformUtils {

    public String osName() {
        return SystemUtil.getOsInfo().getName().toLowerCase();
    }

    public boolean isAndroid() {
        String runtime = System.getProperty("java.runtime.name", "").toLowerCase();
        String vm = System.getProperty("java.vm.name", "").toLowerCase();
        String os = osName();
        return os.contains("android") || runtime.contains("android") || vm.contains("dalvik");
    }

    public boolean isWindows() {
        return osName().contains("windows");
    }

    public boolean isMac() {
        return osName().contains("mac");
    }

    public boolean isLinux() {
        return osName().contains("linux");
    }
}
