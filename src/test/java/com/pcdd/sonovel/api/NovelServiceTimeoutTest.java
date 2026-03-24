package com.pcdd.sonovel.api;

import com.pcdd.sonovel.core.AppConfigLoader;
import com.pcdd.sonovel.model.AppConfig;
import com.pcdd.sonovel.model.Rule;
import com.pcdd.sonovel.util.SourceUtils;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;

public class NovelServiceTimeoutTest {

    private HttpServer server;
    private String baseUrl;
    private Object prevActivatedRules;

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        int port = server.getAddress().getPort();
        baseUrl = "http://127.0.0.1:" + port;

        server.createContext("/toc/1", exchange -> {
            byte[] body = """
                    <html><body>
                      <a href="/chapter/1">第一章</a>
                    </body></html>
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            } finally {
                exchange.close();
            }
        });

        server.createContext("/book/1", exchange -> {
            try {
                Thread.sleep(3_000);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            byte[] body = "<html></html>".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            } finally {
                exchange.close();
            }
        });

        server.start();

        Field f = SourceUtils.class.getDeclaredField("cachedActivatedRules");
        f.setAccessible(true);
        prevActivatedRules = f.get(null);

        Rule r = new Rule();
        r.setId(1);
        r.setUrl(baseUrl);
        r.setName("test");

        Rule.Book book = new Rule.Book();
        book.setBaseUri(baseUrl);
        book.setTimeout(1);
        book.setUrl("book/(\\d+)");
        r.setBook(book);

        Rule.Toc toc = new Rule.Toc();
        toc.setBaseUri(baseUrl);
        toc.setTimeout(1);
        toc.setUrl(baseUrl + "/toc/%s");
        toc.setItem("a");
        toc.setPagination(false);
        r.setToc(toc);

        f.set(null, List.of(r));
    }

    @AfterEach
    void tearDown() throws Exception {
        try {
            if (server != null) {
                server.stop(0);
            }
        } finally {
            Field f = SourceUtils.class.getDeclaredField("cachedActivatedRules");
            f.setAccessible(true);
            f.set(null, prevActivatedRules);
        }
    }

    @Test
    void downloadTimeoutShouldThrow() {
        AppConfig cfg = AppConfigLoader.loadConfig();
        cfg.setEnableRetry(0);
        cfg.setMaxRetries(1);
        cfg.setRetryMinInterval(0);
        cfg.setRetryMaxInterval(0);
        cfg.setMinInterval(0);
        cfg.setMaxInterval(0);

        NovelService service = new NovelServiceImpl(cfg);
        assertThrows(Throwable.class, () -> service.download(baseUrl + "/book/1"));
    }
}
