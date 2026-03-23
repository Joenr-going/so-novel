package com.pcdd.sonovel.util;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.IoUtil;
import cn.hutool.core.io.resource.ResourceUtil;
import cn.hutool.core.lang.Assert;
import cn.hutool.core.lang.ConsoleTable;
import cn.hutool.core.util.CharsetUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.Header;
import cn.hutool.json.JSONUtil;
import com.pcdd.sonovel.core.AppConfigLoader;
import com.pcdd.sonovel.core.OkHttpClientFactory;
import com.pcdd.sonovel.core.Source;
import com.pcdd.sonovel.model.AppConfig;
import com.pcdd.sonovel.model.Rule;
import com.pcdd.sonovel.model.SourceInfo;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;
import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.URL;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

/**
 * @author pcdd
 * Created at 2025/1/29
 */
@UtilityClass
public class SourceUtils {

    private final Logger log = LoggerFactory.getLogger(SourceUtils.class);
    public final String META_BOOK_NAME = "meta[property=\"og:novel:book_name\"]";
    public final String META_AUTHOR = "meta[property=\"og:novel:author\"]";
    public final String META_INTRO = "meta[name=\"description\"]";
    public final String META_CATEGORY = "meta[property=\"og:novel:category\"]";
    public final String META_COVER_URL = "meta[property=\"og:image\"]";
    public final String META_LATEST_CHAPTER = "meta[property=\"og:novel:latest_chapter_name\"]";
    public final String META_LAST_UPDATE_TIME = "meta[property=\"og:novel:update_time\"]";
    public final String META_STATUS = "meta[property=\"og:novel:status\"]";

    private final String RULES_DIR = "bundle/rules/";
    private final AppConfig APP_CONFIG = AppConfigLoader.APP_CONFIG;
    private List<Rule> cachedAllRules;
    private List<Rule> cachedActivatedRules;

    /**
     * 根据书籍详情页 url 从当前激活书源匹配规则
     */
    public Rule getRule(String bookUrl) {
        Rule rule = getActivatedRules().stream()
                .filter(r -> bookUrl.startsWith(r.getUrl()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(StrUtil.format("{} 找不到 bookUrl 为 {} 的规则！", APP_CONFIG.getActiveRules(), bookUrl)));
        return applyDefaultRule(rule);
    }

    /**
     * 根据 sourceId 从当前激活书源匹配规则
     */
    public Rule getRule(int sourceId) {
        Rule rule = getActivatedRules().stream()
                .filter(r -> r.getId() == sourceId)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(StrUtil.format("{} 找不到 ID 为 {} 的规则！", APP_CONFIG.getActiveRules(), sourceId)));
        return applyDefaultRule(rule);
    }

    private Rule applyDefaultRule(Rule rule) {
        Rule.Search ruleSearch = rule.getSearch();
        Rule.Book ruleBook = rule.getBook();
        Rule.Toc ruleToc = rule.getToc();
        Rule.Chapter ruleChapter = rule.getChapter();

        // language
        if (StrUtil.isEmpty(rule.getLanguage())) rule.setLanguage(LangUtil.getCurrentLang());

        // baseUri
        if (ruleSearch != null && StrUtil.isEmpty(ruleSearch.getBaseUri())) ruleSearch.setBaseUri(rule.getUrl());
        if (ruleBook != null && StrUtil.isEmpty(ruleBook.getBaseUri())) ruleBook.setBaseUri(rule.getUrl());
        if (ruleToc != null && StrUtil.isEmpty(ruleToc.getBaseUri())) ruleToc.setBaseUri(rule.getUrl());
        if (ruleChapter != null && StrUtil.isEmpty(ruleChapter.getBaseUri())) ruleChapter.setBaseUri(rule.getUrl());

        // timeout
        if (ruleSearch != null && ruleSearch.getTimeout() == null) ruleSearch.setTimeout(15);
        if (ruleBook != null && ruleBook.getTimeout() == null) ruleBook.setTimeout(15);
        if (ruleToc != null && ruleToc.getTimeout() == null) ruleToc.setTimeout(60);
        if (ruleChapter != null && ruleChapter.getTimeout() == null) ruleChapter.setTimeout(15);

        if (ruleBook != null) {
            // 先从规则获取详情，没有再从 meta 获取
            String nameQuery = StrUtil.emptyToDefault(ruleBook.getBookName(), META_BOOK_NAME);
            String authorQuery = StrUtil.emptyToDefault(ruleBook.getAuthor(), META_AUTHOR);
            String introQuery = StrUtil.emptyToDefault(ruleBook.getIntro(), META_INTRO);
            String coverUrlQuery = StrUtil.emptyToDefault(ruleBook.getCoverUrl(), META_COVER_URL);
            String categoryQuery = StrUtil.emptyToDefault(ruleBook.getCategory(), META_CATEGORY);
            String latestChapterQuery = StrUtil.emptyToDefault(ruleBook.getLatestChapter(), META_LATEST_CHAPTER);
            String lastUpdateTimeQuery = StrUtil.emptyToDefault(ruleBook.getLastUpdateTime(), META_LAST_UPDATE_TIME);
            String statusQuery = StrUtil.emptyToDefault(ruleBook.getStatus(), META_STATUS);

            ruleBook.setBookName(nameQuery);
            ruleBook.setAuthor(authorQuery);
            ruleBook.setIntro(introQuery);
            ruleBook.setCoverUrl(coverUrlQuery);
            ruleBook.setCategory(categoryQuery);
            ruleBook.setLatestChapter(latestChapterQuery);
            ruleBook.setLastUpdateTime(lastUpdateTimeQuery);
            ruleBook.setStatus(statusQuery);
        }

        return rule;
    }

    /**
     * 获取当前激活的规则（带缓存）
     */
    public List<Rule> getActivatedRules() {
        if (cachedActivatedRules != null) {
            return cachedActivatedRules;
        }
        cachedActivatedRules = loadRulesFromPath(getActiveRulesPath());
        return cachedActivatedRules;
    }

    /**
     * 获取 rules 目录下全部规则（带缓存）
     */
    public List<Rule> getAllRules() {
        if (cachedAllRules != null) {
            return cachedAllRules;
        }
        cachedAllRules = loadRulesFromPath(RULES_DIR);
        return cachedAllRules;
    }

    /**
     * 获取激活规则文件路径
     */
    private String getActiveRulesPath() {
        String active = APP_CONFIG.getActiveRules();
        if (StrUtil.isBlank(active)) {
            return RULES_DIR + "main.json";
        }
        File f = new File(active);
        if (f.isAbsolute() && f.exists()) {
            return f.getAbsolutePath();
        }
        if (active.startsWith(RULES_DIR)) {
            return active;
        }
        return RULES_DIR + active;
    }

    /**
     * @param pathname 规则目录路径 or 规则文件路径
     */
    private List<Rule> loadRulesFromPath(String pathname) {
        if (StrUtil.isBlank(pathname)) {
            return List.of();
        }

        List<Rule> rules;
        File file = new File(pathname);
        if (file.exists()) {
            if (file.isFile()) {
                rules = JSONUtil.readJSONArray(file, CharsetUtil.CHARSET_UTF_8)
                        .toList(Rule.class)
                        .stream()
                        .map(SourceUtils::applyDefaultRule)
                        .toList();
            } else {
                rules = FileUtil.loopFiles(file, f -> f.getName().endsWith(".json"))
                        .stream()
                        .flatMap(f -> JSONUtil.readJSONArray(f, CharsetUtil.CHARSET_UTF_8)
                                .toList(Rule.class)
                                .stream()
                                .map(SourceUtils::applyDefaultRule))
                        .toList();
            }
        } else if (pathname.endsWith(".json")) {
            try (InputStream is = ResourceUtil.getStream(pathname)) {
                Assert.notNull(is, "书源规则资源不存在: {}", pathname);
                String json = IoUtil.readUtf8(is);
                rules = JSONUtil.parseArray(json)
                        .toList(Rule.class)
                        .stream()
                        .map(SourceUtils::applyDefaultRule)
                        .toList();
            } catch (Exception e) {
                throw new IllegalStateException("读取书源规则资源失败: " + pathname, e);
            }
        } else {
            Set<String> resources = listResourcesInDir(pathname, ".json");
            Assert.isTrue(!resources.isEmpty(), "书源规则资源不存在: {}", pathname);
            rules = resources.stream()
                    .flatMap(name -> {
                        try (InputStream is = ResourceUtil.getStream(name)) {
                            if (is == null) {
                                return java.util.stream.Stream.empty();
                            }
                            String json = IoUtil.readUtf8(is);
                            return JSONUtil.parseArray(json).toList(Rule.class).stream();
                        } catch (Exception e) {
                            throw new IllegalStateException("读取书源规则资源失败: " + name, e);
                        }
                    })
                    .map(SourceUtils::applyDefaultRule)
                    .toList();
        }

        // 填充自增 ID
        IntStream.range(0, rules.size())
                .forEach(i -> rules.get(i).setId(i + 1));

        return rules;
    }

    private Set<String> listResourcesInDir(String dir, String suffix) {
        String normalized = dir.endsWith("/") ? dir : dir + "/";
        ClassLoader cl = SourceUtils.class.getClassLoader();
        Set<String> out = new LinkedHashSet<>();
        try {
            URL url = cl.getResource(normalized);
            if (url == null) {
                url = cl.getResource(dir);
            }
            if (url == null) {
                return out;
            }
            String protocol = url.getProtocol();
            if ("file".equals(protocol)) {
                File folder = new File(url.toURI());
                File[] files = folder.listFiles((d, name) -> name.endsWith(suffix));
                if (files != null) {
                    for (File f : files) {
                        out.add(normalized + f.getName());
                    }
                }
                return out;
            }
            if ("jar".equals(protocol)) {
                JarURLConnection conn = (JarURLConnection) url.openConnection();
                JarFile jar = conn.getJarFile();
                String prefix = conn.getEntryName();
                if (prefix == null) {
                    prefix = normalized;
                }
                if (!prefix.endsWith("/")) {
                    prefix = prefix + "/";
                }
                Enumeration<JarEntry> entries = jar.entries();
                while (entries.hasMoreElements()) {
                    JarEntry e = entries.nextElement();
                    String name = e.getName();
                    if (!e.isDirectory() && name.startsWith(prefix) && name.endsWith(suffix)) {
                        out.add(name);
                    }
                }
                return out;
            }
            return out;
        } catch (Exception e) {
            return out;
        }
    }

    /**
     * 获取可聚合搜索的书源列表。排除不支持搜索的、搜索有限流的、搜索意义不大的、暂时无法访问的书源
     */
    public List<Source> getSearchableSources() {
        return getActivatedRules().stream()
                .filter(r -> !r.isDisabled() && r.getSearch() != null && !r.getSearch().isDisabled())
                .map(r -> {
                    // 此处切勿改为 AppConfigLoader.APP_CONFIG
                    AppConfig cfg = AppConfigLoader.loadConfig();
                    cfg.setSourceId(r.getId());
                    return new Source(cfg);
                })
                .toList();
    }

    @SneakyThrows
    public List<SourceInfo> getActivatedSourcesWithAvailabilityCheck() {
        List<Rule> rules = SourceUtils.getActivatedRules();
        if (rules.isEmpty()) {
            return new ArrayList<>();
        }
        int poolSize = Math.min(rules.size(), Math.max(1, Runtime.getRuntime().availableProcessors()));
        ExecutorService executor = Executors.newFixedThreadPool(poolSize);
        CompletionService<SourceInfo> completionService = new ExecutorCompletionService<>(executor);
        OkHttpClient client = OkHttpClientFactory.create(APP_CONFIG);

        for (Rule r : rules) {
            completionService.submit(() -> {
                SourceInfo source = SourceInfo.builder()
                        .id(r.getId())
                        .name(r.getName())
                        .url(r.getUrl())
                        .build();
                try {
                    Call call = client.newCall(new Request.Builder()
                            .url(r.getUrl())
                            .header(Header.USER_AGENT.toString(), RandomUA.generate())
                            .head() // 只发 HEAD 请求，不获取 body，更快！
                            .build());
                    call.timeout().timeout(3, TimeUnit.SECONDS);

                    // 放这里才最准确
                    long startTime = System.currentTimeMillis();
                    try (Response resp = call.execute()) {
                        source.setDelay((int) (System.currentTimeMillis() - startTime));
                        source.setCode(resp.code());
                    }
                } catch (Exception e) {
                    source.setDelay(-1);
                    source.setCode(-1);
                    if (EnvUtils.isDev()) {
                        log.warn("书源 {} ({}) 测试连通性异常：{}", r.getId(), r.getName(), e.getMessage());
                    }
                }

                return source;
            });
        }
        List<SourceInfo> res = new ArrayList<>();
        for (int i = 0; i < rules.size(); i++) {
            // 获取最先完成的任务的结果
            res.add(completionService.take().get());
        }
        executor.shutdown();

        res.sort((o1, o2) -> {
            int delay1 = o1.getDelay() < 0 ? Integer.MAX_VALUE : o1.getDelay();
            int delay2 = o2.getDelay() < 0 ? Integer.MAX_VALUE : o2.getDelay();
            return Integer.compare(delay1, delay2);
        });

        return res;
    }

    public List<SourceInfo> getActivatedSources() {
        List<Rule> rules = SourceUtils.getActivatedRules();
        return BeanUtil.copyToList(rules, SourceInfo.class);
    }

    public void printActivatedSources() {
        ConsoleTable asciiTables = ConsoleTable.create()
                .setSBCMode(false)
                .addHeader("ID", "书源", "主页", "状态");
        getActivatedSources()
                .forEach(e -> asciiTables.addBody(
                        e.getId() + "",
                        e.getName(),
                        e.getUrl(),
                        e.isDisabled() ? "禁用" : "启用"
                ));
        log.info("\n{}", asciiTables);
    }

}
