package com.pcdd.sonovel.util;

import cn.hutool.setting.dialect.Props;
import lombok.experimental.UtilityClass;

import java.io.File;

@UtilityClass
public class EnvUtils {

    private Props env;

    static {
        try {
            env = new Props(new File(".env"));
        } catch (Exception e) {
            env = new Props();
        }
    }

    /**
     * 根据键获取 .env 文件中的字符串值。
     */
    public String get(String key) {
        return env.getStr(key);
    }

    /**
     * 根据键获取 .env 文件中的字符串值，如果键不存在则返回默认值。
     */
    public String get(String key, String defaultValue) {
        return env.getStr(key, defaultValue);
    }

    /**
     * 获取当前环境名称 (例如 "dev", "prod", "test")。
     */
    public String getCurrentEnv() {
        return env.getStr("ENV", "prod").toLowerCase();
    }

    /**
     * 判断当前环境是否为开发环境。
     */
    public boolean isDev() {    // 目前先写死 dev ,不然 lib 集成模式下，读取 File(".env") 会有问题
        return true
    }

    /**
     * 判断当前环境是否为生产环境。
     */
    public boolean isProd() {
        return "prod".equals(getCurrentEnv());
    }

    /**
     * 判断当前环境是否为测试环境。
     */
    public boolean isTest() {
        return "test".equals(getCurrentEnv());
    }

}