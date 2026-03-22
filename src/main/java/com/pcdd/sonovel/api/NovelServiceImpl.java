package com.pcdd.sonovel.api;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import com.pcdd.sonovel.context.HttpClientContext;
import com.pcdd.sonovel.core.EpubFetcher;
import com.pcdd.sonovel.core.OkHttpClientFactory;
import com.pcdd.sonovel.core.Crawler;
import com.pcdd.sonovel.core.Source;
import com.pcdd.sonovel.handle.SearchResultsHandler;
import com.pcdd.sonovel.model.AppConfig;
import com.pcdd.sonovel.model.Chapter;
import com.pcdd.sonovel.model.Rule;
import com.pcdd.sonovel.model.SearchResult;
import com.pcdd.sonovel.parse.SearchParser;
import com.pcdd.sonovel.parse.SearchParserQuanben5;
import com.pcdd.sonovel.parse.TocParser;
import com.pcdd.sonovel.util.SourceUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.io.InputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class NovelServiceImpl implements NovelService {

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
                } finally {
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

        return config.getSearchFilter() == 1 ? SearchResultsHandler.filterSort(results, keyword) : results;
    }

    @Override
    public List<Chapter> fetchToc(String bookUrl) {
        AppConfig cfg = BeanUtil.copyProperties(config, AppConfig.class);
        cfg.setSourceId(SourceUtils.getRule(bookUrl).getId());
        HttpClientContext.set(OkHttpClientFactory.create(cfg));
        return new TocParser(cfg).parseAll(bookUrl);
    }

    @Override
    public InputStream fetch(String bookUrl, Chapter... chapters) {
        AppConfig cfg = BeanUtil.copyProperties(config, AppConfig.class);
        cfg.setSourceId(SourceUtils.getRule(bookUrl).getId());
        HttpClientContext.set(OkHttpClientFactory.create(cfg));
        if (chapters == null || chapters.length == 0) {
            return new EpubFetcher(cfg).fetch(bookUrl);
        }
        return new EpubFetcher(cfg).fetch(bookUrl, List.of(chapters));
    }

    @Override
    public double download(String bookUrl, Chapter... chapters) {
        if (chapters == null || chapters.length == 0) {
            return new Crawler(config).crawl(bookUrl);
        }
        return new Crawler(config).crawl(bookUrl, List.of(chapters));
    }
}
