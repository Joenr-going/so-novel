package com.pcdd.sonovel.parse;

import com.pcdd.sonovel.context.HttpClientContext;
import com.pcdd.sonovel.model.AppConfig;
import com.pcdd.sonovel.model.Rule;
import okhttp3.OkHttpClient;

import java.lang.reflect.Method;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class TocParserRobustnessTest {
    public static void main(String[] args) throws Exception {
        HttpClientContext.set(new OkHttpClient.Builder().build());

        AppConfig cfg = new AppConfig();
        cfg.setSourceId(1);
        cfg.setMinInterval(0);
        cfg.setMaxInterval(0);
        cfg.setRetryMinInterval(0);
        cfg.setRetryMaxInterval(0);

        TocParser parser = new TocParser(cfg);

        Method parseToc = TocParser.class.getDeclaredMethod("parseToc", Set.class, int.class, int.class, Rule.Toc.class);
        parseToc.setAccessible(true);

        Rule.Toc r = new Rule.Toc();
        r.setTimeout(1);
        r.setBaseUri("http://example.com/");
        r.setItem("a");
        r.setPagination(false);

        Set<String> urls = new LinkedHashSet<>();
        urls.add("http://");
        urls.add("http://");

        Object out = parseToc.invoke(parser, urls, 1, Integer.MAX_VALUE, r);
        List<?> toc = (List<?>) out;
        if (toc == null) {
            throw new IllegalStateException("toc is null");
        }
        System.out.println("ok");
    }
}
