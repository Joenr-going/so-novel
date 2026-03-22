package com.pcdd.sonovel.core;

import cn.hutool.setting.dialect.Props;
import com.pcdd.sonovel.model.AppConfig;
import com.pcdd.sonovel.util.LangUtil;
import lombok.experimental.UtilityClass;

import java.nio.charset.StandardCharsets;

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

        // [cookie]
        cfg.setQidianCookie("");

        // [proxy]
        cfg.setProxyEnabled(0);
        cfg.setProxyHost("127.0.0.1");
        cfg.setProxyPort(7890);

        return cfg;
    }

}
