package com.pcdd.sonovel.api;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import com.pcdd.sonovel.context.HttpClientContext;
import com.pcdd.sonovel.core.Crawler;
import com.pcdd.sonovel.core.Defaults;
import com.pcdd.sonovel.core.EpubFetcher;
import com.pcdd.sonovel.core.FormatFetcher;
import com.pcdd.sonovel.core.OkHttpClientFactory;
import com.pcdd.sonovel.core.Source;
import com.pcdd.sonovel.handle.SearchResultsHandler;
import com.pcdd.sonovel.model.AppConfig;
import com.pcdd.sonovel.model.BookFormat;
import com.pcdd.sonovel.model.Chapter;
import com.pcdd.sonovel.model.Rule;
import com.pcdd.sonovel.model.SearchResult;
import com.pcdd.sonovel.parse.SearchParser;
import com.pcdd.sonovel.parse.SearchParserQuanben5;
import com.pcdd.sonovel.parse.TocParser;
import com.pcdd.sonovel.util.SourceUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.io.InputStream;
import java.io.IOException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

public class NovelServiceImpl implements NovelService {

    private static final Logger log = LoggerFactory.getLogger(NovelServiceImpl.class);
    private final AppConfig config;

    public NovelServiceImpl(AppConfig config) {
        this.config = config;
    }

    @Override
    public List<SearchResult> search(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return Collections.emptyList();
        }

        List<Rule> rules = SourceUtils.getActivatedRules();
        if (rules.isEmpty()) {
            return Collections.emptyList();
        }

        ConcurrentLinkedQueue<Throwable> errors = new ConcurrentLinkedQueue<>();
        List<SearchResult> results = Collections.synchronizedList(new ArrayList<>());
        int poolSize = Math.min(rules.size(), Math.max(1, Runtime.getRuntime().availableProcessors()));
        ExecutorService executor = Executors.newFixedThreadPool(poolSize);
        CountDownLatch latch = new CountDownLatch(rules.size());

        for (Rule rule : rules) {
            executor.execute(() -> {
                try {
                    if (rule.isDisabled() || rule.getSearch() == null || rule.getSearch().isDisabled()) {
                        return;
                    }
                    AppConfig cfg = BeanUtil.copyProperties(config, AppConfig.class);
                    cfg.setSourceId(rule.getId());
                    HttpClientContext.set(OkHttpClientFactory.create(cfg));
                    Source source = new Source(rule, cfg);
                    List<SearchResult> res = "proxy-required.json".equals(source.config.getActiveRules()) && source.config.getSourceId() == 2
                            ? new SearchParserQuanben5(source.config).parse(keyword)
                            : new SearchParser(source.config).parse(keyword);
                    if (CollUtil.isNotEmpty(res)) {
                        results.addAll(res);
                    }
                } catch (Throwable t) {
                    errors.add(t);
                } finally {
                    HttpClientContext.clear();
                    latch.countDown();
                }
            });
        }

        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            executor.shutdown();
        }

        if (!errors.isEmpty()) {
            Throwable first = errors.poll();
            IllegalStateException ex = new IllegalStateException("search failed", first);
            for (Throwable t : errors) {
                ex.addSuppressed(t);
            }
            throw ex;
        }

        return config.getSearchFilter() == 1 ? SearchResultsHandler.filterSort(results, keyword) : results;
    }

    @Override
    public void searchAsync(String keyword, SearchListener listener) {
        if (keyword == null || keyword.isBlank()) {
            if (listener != null) listener.onComplete();
            return;
        }

        List<Rule> rules = SourceUtils.getActivatedRules();
        if (rules.isEmpty()) {
            if (listener != null) listener.onComplete();
            return;
        }

        int poolSize = Math.min(rules.size(), Math.max(1, Runtime.getRuntime().availableProcessors()));
        ExecutorService executor = Executors.newFixedThreadPool(poolSize);
        java.util.concurrent.atomic.AtomicInteger remaining = new java.util.concurrent.atomic.AtomicInteger(rules.size());

        for (Rule rule : rules) {
            executor.execute(() -> {
                try {
                    if (rule.isDisabled() || rule.getSearch() == null || rule.getSearch().isDisabled()) {
                        return;
                    }
                    AppConfig cfg = BeanUtil.copyProperties(config, AppConfig.class);
                    cfg.setSourceId(rule.getId());
                    HttpClientContext.set(OkHttpClientFactory.create(cfg));
                    Source source = new Source(rule, cfg);
                    List<SearchResult> res = "proxy-required.json".equals(source.config.getActiveRules()) && source.config.getSourceId() == 2
                            ? new SearchParserQuanben5(source.config).parse(keyword)
                            : new SearchParser(source.config).parse(keyword);
                    if (CollUtil.isNotEmpty(res) && listener != null) {
                        listener.onResult(config.getSearchFilter() == 1 ? SearchResultsHandler.filterSort(res, keyword) : res);
                    }
                } catch (Throwable t) {
                    if (listener != null) listener.onError(t);
                } finally {
                    HttpClientContext.clear();
                    if (remaining.decrementAndGet() == 0) {
                        if (listener != null) listener.onComplete();
                        executor.shutdown();
                    }
                }
            });
        }
    }

    @Override
    public List<Chapter> fetchToc(String bookUrl) {
        AppConfig cfg = BeanUtil.copyProperties(config, AppConfig.class);
        try {
            cfg.setSourceId(SourceUtils.getRule(bookUrl).getId());
            HttpClientContext.set(OkHttpClientFactory.create(cfg));
            return retry(() -> new TocParser(cfg).parseAll(bookUrl), "fetchToc", cfg);
        } finally {
            HttpClientContext.clear();
        }
    }

    @Override
    public InputStream fetch(String bookUrl, Chapter... chapters) {
        return fetch(bookUrl, config.getExtName(), chapters);
    }

    @Override
    public double download(String bookUrl, Chapter... chapters) {
        return download(bookUrl, config.getExtName(), chapters);
    }

    @Override
    public InputStream fetch(String bookUrl, BookFormat format, Chapter... chapters) {
        AppConfig cfg = prepareConfig(bookUrl, format);
        try {
            HttpClientContext.set(OkHttpClientFactory.create(cfg));
            if (BookFormat.EPUB.equals(cfg.getExtName())) {
                if (chapters == null || chapters.length == 0) {
                    return retry(() -> new EpubFetcher(cfg).fetch(bookUrl), "fetchEpub", cfg);
                }
                return retry(() -> new EpubFetcher(cfg).fetch(bookUrl, List.of(chapters)), "fetchEpub", cfg);
            }
            if (chapters == null || chapters.length == 0) {
                return retry(() -> new FormatFetcher(cfg).fetch(bookUrl), "fetch", cfg);
            }
            return retry(() -> new FormatFetcher(cfg).fetch(bookUrl, List.of(chapters)), "fetch", cfg);
        } finally {
            HttpClientContext.clear();
        }
    }

    @Override
    public double download(String bookUrl, BookFormat format, Chapter... chapters) {
        AppConfig cfg = prepareConfig(bookUrl, format);
        try {
            HttpClientContext.set(OkHttpClientFactory.create(cfg));
            if (chapters == null || chapters.length == 0) {
                return retry(() -> new Crawler(cfg).crawl(bookUrl), "download", cfg);
            }
            return retry(() -> new Crawler(cfg).crawl(bookUrl, List.of(chapters)), "download", cfg);
        } finally {
            HttpClientContext.clear();
        }
    }

    private AppConfig prepareConfig(String bookUrl, BookFormat format) {
        AppConfig cfg = BeanUtil.copyProperties(config, AppConfig.class);
        cfg.setSourceId(SourceUtils.getRule(bookUrl).getId());
        cfg.setExtName(format != null ? format : (cfg.getExtName() != null ? cfg.getExtName() : Defaults.EXT_NAME));
        return cfg;
    }

    private <T> T retry(Supplier<T> supplier, String action, AppConfig cfg) {
        int enabled = cfg.getEnableRetry() == null ? 0 : cfg.getEnableRetry();
        int maxRetries = cfg.getMaxRetries() == null ? 0 : cfg.getMaxRetries();
        if (enabled != 1 || maxRetries <= 0) {
            return supplier.get();
        }
        int attempts = Math.max(1, maxRetries);
        for (int i = 1; i <= attempts; i++) {
            try {
                return supplier.get();
            } catch (Exception e) {
                if (i >= attempts || !isRetryable(e)) {
                    throw e;
                }
                sleepRetryInterval(cfg);
                log.debug("{} 重试 {}/{}: {}", action, i, attempts, e.toString());
            }
        }
        return supplier.get();
    }

    private boolean isRetryable(Throwable t) {
        Throwable cur = t;
        while (cur != null) {
            if (cur instanceof IOException) {
                return true;
            }
            cur = cur.getCause();
        }
        return false;
    }

    private void sleepRetryInterval(AppConfig cfg) {
        int min = cfg.getRetryMinInterval() == null ? 0 : cfg.getRetryMinInterval();
        int max = cfg.getRetryMaxInterval() == null ? min : cfg.getRetryMaxInterval();
        if (max < min) {
            int tmp = max;
            max = min;
            min = tmp;
        }
        long sleepMs;
        if (max <= 0) {
            sleepMs = 0;
        } else if (min <= 0) {
            sleepMs = ThreadLocalRandom.current().nextLong(0, max + 1L);
        } else if (min == max) {
            sleepMs = min;
        } else {
            sleepMs = ThreadLocalRandom.current().nextLong(min, (long) max + 1L);
        }
        if (sleepMs <= 0) {
            return;
        }
        try {
            Thread.sleep(sleepMs);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }
}
