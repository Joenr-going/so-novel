package com.pcdd.sonovel;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.date.StopWatch;
import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.NumberUtil;
import cn.hutool.core.util.RuntimeUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import cn.hutool.log.dialect.console.ConsoleLog;
import cn.hutool.log.level.Level;
import com.pcdd.sonovel.context.BookContext;
import com.pcdd.sonovel.context.HttpClientContext;
import com.pcdd.sonovel.core.AppConfigLoader;
import com.pcdd.sonovel.core.ChapterRenderer;
import com.pcdd.sonovel.core.OkHttpClientFactory;
import com.pcdd.sonovel.core.Source;
import com.pcdd.sonovel.model.AppConfig;
import com.pcdd.sonovel.model.Chapter;
import com.pcdd.sonovel.model.Rule;
import com.pcdd.sonovel.model.Rule.Book;
import com.pcdd.sonovel.model.SearchResult;
import com.pcdd.sonovel.parse.*;
import com.pcdd.sonovel.util.ChineseConverter;
import com.pcdd.sonovel.util.LangType;
import com.pcdd.sonovel.util.SourceUtils;
import lombok.SneakyThrows;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @author pcdd
 * Created at 2024/12/28
 */
// 保证测试类实例在多个测试方法间共享
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BookSourceTest {

    private static final Logger log = LoggerFactory.getLogger(BookSourceTest.class);
    static final AppConfig APP_CONFIG = AppConfigLoader.APP_CONFIG;
    static final String DIVIDER = "=".repeat(50);
    String firstChapterUrl;
    String firstChapterTitle;
    List<Chapter> chapters;

    static {
        HttpClientContext.set(OkHttpClientFactory.create(APP_CONFIG));
        ConsoleLog.setLevel(Level.OFF);
        // 覆盖 config-dev.ini 配置
        // 在此修改要测试的书源
        APP_CONFIG.setActiveRules("main.json");
        // 正文语言
        APP_CONFIG.setLanguage(LangType.ZH_CN);
    }

    @DisplayName("main.json")
    @ParameterizedTest
    @CsvSource({
            "http://www.xbiqugu.la/130/130509/",
            "https://www.shuhaige.net/70475/",
            "http://www.mcxs.info/145_145199/",
            "http://www.99xs.info/tag/129_129843/",
            "https://www.22biqu.com/biqu79148/",
            "http://www.xbiquzw.net/10_10233/",
            "http://www.shu009.com/book/160879/",
            "http://www.ujxsw.org/book/107612/",
            "http://www.yeudusk.com/book/1322535/",
            "https://www.wxsy.net/novel/1803/",
            "https://www.biquge365.net/book/95601/",
            "https://www.ranwen8.cc/book/8217/"
    })
    void test01(String bookUrl) {
        Rule rule = SourceUtils.getRule(bookUrl);
        APP_CONFIG.setSourceId(rule.getId());

        searchParse("斗罗");
        bookParse(bookUrl);
        tocParse(bookUrl);
        chapterParse();
        // chapterBatchParse(0, 100);
    }

    @DisplayName("non-searchable.json")
    @ParameterizedTest
    @CsvSource({
            "https://www.tianxibook.com/book/66120/",
            "https://www.0xs.net/txt/68398.html",
            "https://www.laoyaoxs.org/info/281469.html"
    })
    void test02(String bookUrl) {
        Rule rule = SourceUtils.getRule(bookUrl);
        APP_CONFIG.setSourceId(rule.getId());

        bookParse(bookUrl);
        tocParse(bookUrl);
        chapterParse();
        chapterBatchParse(0, 100);
    }

    @DisplayName("proxy-required.json")
    @ParameterizedTest
    @CsvSource({
            "https://www.69shuba.com/book/48273.htm",
            "https://quanben5.com/n/henchunhenaimei/",
            "https://www.dxmwx.org/book/56441.html"
    })
    void test03(String bookUrl) {
        Rule rule = SourceUtils.getRule(bookUrl);
        APP_CONFIG.setSourceId(rule.getId());

        searchParse("斗罗大陆");
        bookParse(bookUrl);
        tocParse(bookUrl);
        chapterParse();
    }

    @DisplayName("cloudflare.json")
    @ParameterizedTest
    @CsvSource({
            "http://www.xhytd.com/170/170581/",
            "https://www.96dushu.com/book/448209/",
            "http://www.dongtanxs.com/dong49371/"
    })
    void test04(String bookUrl) {
        Rule rule = SourceUtils.getRule(bookUrl);
        APP_CONFIG.setSourceId(rule.getId());

        searchParse("斗罗大陆");
        bookParse(bookUrl);
        tocParse(bookUrl);
        chapterParse();
    }

    public void searchParse(String keyword) {
        log.info("\n{} START searchParse {}", DIVIDER, DIVIDER);
        List<SearchResult> list = "proxy-required.json".equals(APP_CONFIG.getActiveRules()) && APP_CONFIG.getSourceId() == 2
                ? new SearchParserQuanben5(APP_CONFIG).parse(keyword)
                : new SearchParser(APP_CONFIG).parse(keyword);
        if (CollUtil.isEmpty(list)) {
            log.info("\"{}\"搜索结果为空", keyword);
        }
        if (!list.isEmpty()) {
            log.info("点击此 URL 检查首条搜索结果有效性: {}", CollUtil.getFirst(list).getUrl());
        }
        new SearchParser(APP_CONFIG).printSearchResult(list);
        log.info("{} END searchParse {}\n", DIVIDER, DIVIDER);
    }

    @org.junit.jupiter.api.Test
    @DisplayName("Test search async")
    void testSearchAsync() throws InterruptedException {

        log.info("\n{} START testSearchAsync {}", DIVIDER, DIVIDER);
        com.pcdd.sonovel.api.NovelService novelService = new com.pcdd.sonovel.api.NovelServiceImpl(APP_CONFIG);
        CountDownLatch latch = new CountDownLatch(1);
        
        novelService.searchAsync("斗罗", new com.pcdd.sonovel.api.SearchListener() {
            @Override
            public void onResult(List<SearchResult> results) {
                log.info("Received async batch of {} results", results.size());
                if (!results.isEmpty()) {
                    log.info("First item in async batch: {}", results.get(0).getBookName());
                }
            }

            @Override
            public void onError(Throwable e) {
                log.error("Async search error: {}", e.getMessage());
            }

            @Override
            public void onComplete() {
                log.info("Async search completed");
                latch.countDown();
            }
        });
        
        latch.await(30, java.util.concurrent.TimeUnit.SECONDS);
        log.info("{} END testSearchAsync {}\n", DIVIDER, DIVIDER);
    }

    public void bookParse(String bookUrl) {
        log.info("\n{} START bookParse {}", DIVIDER, DIVIDER);
        Book book = new BookParser(APP_CONFIG).parse(bookUrl);
        BookContext.set(book);
        log.info(JSONUtil.toJsonPrettyStr(book));
        log.info("{} END bookParse {}\n", DIVIDER, DIVIDER);
    }

    public void tocParse(String bookUrl) {
        log.info("\n{} START tocParse {}", DIVIDER, DIVIDER);
        TocParser tocParser = new TocParser(APP_CONFIG);
        List<Chapter> toc = tocParser.parseAll(bookUrl);
        chapters = toc;
        toc.forEach(chapter -> log.info("{}", chapter));
        if (CollUtil.isEmpty(toc)) {
            log.info("目录为空");
            return;
        }
        // 被 chapterParse() 调用
        firstChapterUrl = toc.get(0).getUrl();
        firstChapterTitle = toc.get(0).getTitle();
        log.info("{} END tocParse {}\n", DIVIDER, DIVIDER);
    }

    /**
     * 必须在 tocParse 之后执行，因为需要 firstChapterUrl，测试对象是 @CsvSource 里的链接
     */
    public void chapterParse() {
        log.info("\n{} START chapterParse {}", DIVIDER, DIVIDER);
        if (StrUtil.hasEmpty(firstChapterUrl, firstChapterTitle)) {
            log.info("首章链接或章节名为空，不执行 chapterParse");
            return;
        }
        log.info("firstChapterUrl: {}\nfirstChapterTitle: {}", firstChapterUrl, firstChapterTitle);

        Chapter build = Chapter.builder()
                .title(firstChapterTitle)
                .url(firstChapterUrl)
                .build();
        Chapter chapter = new ChapterParser(APP_CONFIG).parse(build);
        Assert.notNull(chapter, "下载失败，详见 logs");

        log.info(chapter.getContent());
        log.info("{} END chapterParse {}\n", DIVIDER, DIVIDER);
    }

    /**
     * 测试章节是否限流
     */
    @SneakyThrows
    public void chapterBatchParse(int start, int end) {
        log.info("\n{} START chapterBatchParse {}", DIVIDER, DIVIDER);

        StopWatch stopWatch = new StopWatch();
        stopWatch.start();

        ExecutorService threadPool = Executors.newFixedThreadPool(RuntimeUtil.getProcessorCount() * 5);
        CountDownLatch latch = new CountDownLatch(end - start);
        Source source = new Source(APP_CONFIG.getSourceId());

        for (Chapter chapter : CollUtil.sub(chapters, start, end)) {
            threadPool.execute(() -> {
                Chapter o = Chapter.builder().url(chapter.getUrl()).build();
                Chapter beforeFiltration = new ChapterParser(APP_CONFIG).parse(o);
                Chapter afterFiltration = new ChapterRenderer(APP_CONFIG).process(beforeFiltration);
                Chapter res = ChineseConverter.convert(afterFiltration, source.rule.getLanguage(), APP_CONFIG.getLanguage());
                if (StrUtil.isAllNotEmpty(res.getTitle(), res.getContent())) {
                    log.info("✅ {}", res.getTitle());
                } else {
                    StringBuilder errMsg = new StringBuilder("❌ %s %s ".formatted(res.getTitle(), res.getUrl()));
                    if (StrUtil.isEmpty(res.getTitle())) {
                        errMsg.append("章节标题为空 ");
                    }
                    if (StrUtil.isEmpty(res.getContent())) {
                        errMsg.append("章节正文为空");
                    }
                    log.info(errMsg.toString());
                }

                latch.countDown();
            });
        }

        latch.await();
        threadPool.shutdown();
        stopWatch.stop();

        log.info("总耗时 {} s", NumberUtil.round(stopWatch.getTotalTimeSeconds(), 2));
        log.info("{} END chapterBatchParse {}\n", DIVIDER, DIVIDER);
    }

}
