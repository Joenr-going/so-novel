package com.pcdd.sonovel.core;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.IoUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.core.io.resource.ResourceUtil;
import cn.hutool.setting.Setting;
import cn.hutool.setting.dialect.Props;
import com.pcdd.sonovel.model.AppConfig;
import com.pcdd.sonovel.model.BookFormat;
import com.pcdd.sonovel.util.LangUtil;
import lombok.experimental.UtilityClass;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

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
        String configFilePath = System.getProperty("config.file");
        if (StrUtil.isNotBlank(configFilePath) && FileUtil.exist(configFilePath)) {
            return new Setting(new File(configFilePath).getAbsolutePath());
        }
        try (InputStream is = ResourceUtil.getStream("bundle/config.ini")) {
            if (is == null) {
                return new Setting();
            }
            File temp = File.createTempFile("so-novel-config-", ".ini");
            FileUtil.writeFromStream(is, temp);
            temp.deleteOnExit();
            return new Setting(temp.getAbsolutePath());
        } catch (Exception e) {
            return new Setting();
        }
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

        applyIniConfig(cfg);

        return cfg;
    }

    private void applyIniConfig(AppConfig cfg) {
        String configFilePath = System.getProperty("config.file");
        Map<String, Map<String, String>> ini;
        if (StrUtil.isNotBlank(configFilePath) && FileUtil.exist(configFilePath)) {
            ini = parseIni(IoUtil.readUtf8(FileUtil.getInputStream(configFilePath)));
        } else {
            try (InputStream is = ResourceUtil.getStream("bundle/config.ini")) {
                if (is == null) {
                    return;
                }
                ini = parseIni(IoUtil.readUtf8(is));
            } catch (Exception e) {
                return;
            }
        }

        Map<String, String> global = ini.getOrDefault("global", Map.of());
        cfg.setGhProxy(emptyToNull(global.get("gh-proxy")));
        cfg.setCfBypass(emptyToNull(global.get("cf-bypass")));

        Map<String, String> download = ini.getOrDefault("download", Map.of());
        String extName = download.get("extname");
        if (StrUtil.isNotBlank(extName)) {
            cfg.setExtName(BookFormat.fromString(extName));
        }
        cfg.setTxtEncoding(emptyToNull(download.get("txt-encoding")));
        cfg.setPreserveChapterCache(parseIntOrDefault(download.get("preserve-chapter-cache"), cfg.getPreserveChapterCache()));

        Map<String, String> source = ini.getOrDefault("source", Map.of());
        cfg.setLanguage(emptyToNull(source.get("language")));
        cfg.setActiveRules(emptyToNull(source.get("active-rules")));
        cfg.setSourceId(parseIntOrDefault(source.get("source-id"), cfg.getSourceId()));
        cfg.setSearchLimit(parseIntOrDefault(source.get("search-limit"), cfg.getSearchLimit()));
        cfg.setSearchFilter(parseIntOrDefault(source.get("search-filter"), cfg.getSearchFilter()));

        Map<String, String> crawl = ini.getOrDefault("crawl", Map.of());
        cfg.setConcurrency(parseIntOrDefault(crawl.get("concurrency"), cfg.getConcurrency()));
        cfg.setMinInterval(parseIntOrDefault(crawl.get("min-interval"), cfg.getMinInterval()));
        cfg.setMaxInterval(parseIntOrDefault(crawl.get("max-interval"), cfg.getMaxInterval()));
        cfg.setEnableRetry(parseIntOrDefault(crawl.get("enable-retry"), cfg.getEnableRetry()));
        cfg.setMaxRetries(parseIntOrDefault(crawl.get("max-retries"), cfg.getMaxRetries()));
        cfg.setRetryMinInterval(parseIntOrDefault(crawl.get("retry-min-interval"), cfg.getRetryMinInterval()));
        cfg.setRetryMaxInterval(parseIntOrDefault(crawl.get("retry-max-interval"), cfg.getRetryMaxInterval()));

        Map<String, String> cookie = ini.getOrDefault("cookie", Map.of());
        cfg.setQidianCookie(emptyToNull(cookie.get("qidian")));

        Map<String, String> proxy = ini.getOrDefault("proxy", Map.of());
        cfg.setProxyEnabled(parseIntOrDefault(proxy.get("enabled"), cfg.getProxyEnabled()));
        cfg.setProxyHost(emptyToNull(proxy.get("host")));
        cfg.setProxyPort(parseIntOrDefault(proxy.get("port"), cfg.getProxyPort()));
    }

    private Map<String, Map<String, String>> parseIni(String content) {
        Map<String, Map<String, String>> result = new HashMap<>();
        String section = "";
        for (String raw : content.split("\n")) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#") || line.startsWith(";")) {
                continue;
            }
            if (line.startsWith("[") && line.endsWith("]")) {
                section = line.substring(1, line.length() - 1).trim().toLowerCase();
                result.putIfAbsent(section, new HashMap<>());
                continue;
            }
            int idx = line.indexOf('=');
            if (idx <= 0) {
                continue;
            }
            String key = line.substring(0, idx).trim().toLowerCase();
            String val = line.substring(idx + 1).trim();
            result.computeIfAbsent(section, k -> new HashMap<>()).put(key, val);
        }
        return result;
    }

    private String emptyToNull(String v) {
        return StrUtil.isBlank(v) ? null : v;
    }

    private Integer parseIntOrDefault(String v, Integer defaultValue) {
        if (StrUtil.isBlank(v)) {
            return defaultValue;
        }
        String s = v.trim();
        if (!s.matches("^-?\\d+$")) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(s);
        } catch (Exception e) {
            return defaultValue;
        }
    }
}
