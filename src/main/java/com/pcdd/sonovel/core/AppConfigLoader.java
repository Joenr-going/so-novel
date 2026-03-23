package com.pcdd.sonovel.core;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.setting.Setting;
import cn.hutool.setting.dialect.Props;
import com.pcdd.sonovel.model.AppConfig;
import com.pcdd.sonovel.model.BookFormat;
import com.pcdd.sonovel.util.EnvUtils;
import com.pcdd.sonovel.util.FileUtils;
import com.pcdd.sonovel.util.LangUtil;
import lombok.experimental.UtilityClass;

import java.nio.charset.StandardCharsets;
import java.io.File;

/**
 * @author pcdd
 * Created at 2024/3/23
 */
@UtilityClass
public class AppConfigLoader {

    private final String SELECTION_GLOBAL = "global";
    private final String SELECTION_SOURCE = "source";
    private final String SELECTION_CRAWL = "crawl";
    private final String SELECTION_COOKIE = "cookie";
    private final String SELECTION_PROXY = "proxy";
    public final AppConfig APP_CONFIG = loadConfig();

    /**
     * 加载应用属性
     */
    public Props sys() {
        return Props.getProp("application.properties", StandardCharsets.UTF_8);
    }

    /**
     * 加载用户属性
     */
    public Setting usr() {
        // 从虚拟机选项 -Dconfig.file 获取用户配置文件路径
        String configFilePath = System.getProperty("config.file");

        // 若未指定或指定路径不存在，则从默认位置获取
        if (!FileUtil.exist(configFilePath)) {
            // 用户配置文件默认路径
            String defaultPath = resolveConfigFileName();
            // 若默认路径也不存在，则抛出 FileNotFoundException
            return new Setting(defaultPath);
        }

        return new Setting(new File(configFilePath).getAbsolutePath());
    }

    public AppConfig loadConfig() {
        AppConfig cfg = new AppConfig();
        cfg.setVersion(sys().getStr("version"));

        cfg.setGhProxy("");
        cfg.setCfBypass(null);

        // [source]
        cfg.setLanguage(LangUtil.getCurrentLang());
        cfg.setActiveRules("main.json");
        cfg.setSourceId(-1);
        cfg.setSearchLimit(-1);
        cfg.setSearchFilter(1);

        // [crawl]
        cfg.setConcurrency(-1);
        cfg.setMinInterval(200);
        cfg.setMaxInterval(400);
        cfg.setEnableRetry(1);
        cfg.setMaxRetries(5);
        cfg.setRetryMinInterval(2000);
        cfg.setRetryMaxInterval(4000);
        cfg.setExtName(Defaults.EXT_NAME);
        cfg.setTxtEncoding(Defaults.TXT_ENCODING);
        cfg.setPreserveChapterCache(0);

        // [cookie]
        cfg.setQidianCookie("");

        // [proxy]
        cfg.setProxyEnabled(0);
        cfg.setProxyHost("127.0.0.1");
        cfg.setProxyPort(7890);

        return cfg;
    }

    // 修复 hutool 空串不能触发默认值的 bug
    private String getStrOrDefault(Setting setting, String key, String group, String defaultValue) {
        String value = setting.getByGroup(key, group);
        return StrUtil.isEmpty(value) ? defaultValue : value;
    }

    private String resolveConfigFileName() {
        return FileUtils.toAbsolutePath(EnvUtils.isDev() ? "config-dev.ini" : "config.ini");
    }
}
