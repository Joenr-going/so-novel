package com.pcdd.sonovel.handle;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.pcdd.sonovel.context.DownloadContext;
import com.pcdd.sonovel.core.Defaults;
import com.pcdd.sonovel.model.Rule.Book;
import com.pcdd.sonovel.util.EnvUtils;
import com.pcdd.sonovel.util.FileUtils;
import com.pcdd.sonovel.util.PlatformUtils;
import lombok.SneakyThrows;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.util.List;

/**
 * @author pcdd
 * Created at 2025/4/17
 */
public class PdfMergeHandler implements PostProcessingHandler {

    private static File getFontFile() {
        String basePath = EnvUtils.isDev() ? "bundle/fonts/" : "fonts/";
        File fontFile = new File(basePath + "SoNovel.ttf");
        if (fontFile.exists()) {
            return fontFile;
        }

        List<String> candidates;

        if (PlatformUtils.isAndroid()) {
            candidates = List.of(
                    "/system/fonts/NotoSansCJK-Regular.ttc",
                    "/system/fonts/NotoSansCJK-Regular.otf",
                    "/system/fonts/DroidSansFallback.ttf"
            );
        } else if (PlatformUtils.isWindows()) {
            candidates = List.of(
                    "C:/Windows/Fonts/msyh.ttc",
                    "C:/Windows/Fonts/simsun.ttc",
                    "C:/Windows/Fonts/simhei.ttf"
            );
        } else if (PlatformUtils.isMac()) {
            candidates = List.of(
                    "/System/Library/Fonts/Supplemental/PingFang.ttc",
                    "/System/Library/Fonts/Supplemental/STHeiti Medium.ttc",
                    "/System/Library/Fonts/Supplemental/STSong.ttc"
            );
        } else if (PlatformUtils.isLinux()) {
            candidates = List.of(
                    "/usr/share/fonts/truetype/wqy/wqy-zenhei.ttc",
                    "/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc",
                    "/usr/share/fonts/truetype/arphic/ukai.ttc"
            );
        } else {
            throw new UnsupportedOperationException("无法获取默认字体文件，请自行准备 TTF 格式的中文字体，重命名为 SoNovel.ttf 并放在 fonts 目录下");
        }

        return getFirstAvailableFont(candidates);
    }

    /**
     * 读取指定目录下所有 HTML 文件内容
     */
    private static String getHtmlContentFromDirectory(File directory) {
        if (!directory.isDirectory()) {
            throw new IllegalArgumentException("Invalid directory path");
        }

        StringBuilder mergedHtml = new StringBuilder();
        FileUtils.sortFilesByName(directory).stream()
                .filter(f -> f.getName().endsWith(".html"))
                .forEach(f -> {
                    String chapterHtml = FileUtil.readUtf8String(f);
                    mergedHtml
                            .append("<div class=\"chapter\">")
                            .append(chapterHtml)
                            .append("</div>");
                });

        return mergedHtml.toString();
    }

    public static File getFirstAvailableFont(List<String> paths) {
        for (String path : paths) {
            if (FileUtil.exist(path)) {
                return FileUtil.file(path);
            }
        }
        throw new UnsupportedOperationException("无法获取默认字体文件，请自行准备 TTF 格式的中文字体，重命名为 SoNovel.ttf 并放在 fonts 目录下");

    }

    @SneakyThrows
    @Override
    public void handle(Book book, File saveDir) {
        // 获取 chapter_html 目录下所有 HTML 文件并合并内容
        String htmlContent = getHtmlContentFromDirectory(saveDir);
        String basePath = DownloadContext.getOrDefault(Defaults.DOWNLOAD_PATH);
        String outputPath = StrUtil.format("{}{}({}).pdf",
                basePath + File.separator,
                book.getBookName(),
                book.getAuthor());
        OutputStream out = new FileOutputStream(outputPath);

        try {
            Object builder = createPdfBuilder();
            invoke(builder, "useFastMode");
            invoke(builder, "useFont", new Class[]{File.class, String.class}, new Object[]{getFontFile(), "SoNovel"});
            Object inches = enumValue("com.openhtmltopdf.pdfboxout.PdfRendererBuilder$PageSizeUnits", "INCHES");
            invoke(builder, "useDefaultPageSize", new Class[]{float.class, float.class, inches.getClass()}, new Object[]{7.36f, 9.76f, inches});
            invoke(builder, "withHtmlContent", new Class[]{String.class, String.class}, new Object[]{"""
                    <html>
                    <head>
                      <style>
                        body {
                          font-family: 'SoNovel', sans-serif;
                        }
                        p {
                           font-size: 18px;
                           text-indent: 2em;
                           line-height: 1.6;
                         }
                        .chapter {
                          page-break-before: always;
                          break-before: page;
                        }
                        .chapter:first-child {
                          page-break-before: avoid;
                          break-before: auto;
                        }
                      </style>
                    </head>
                    <body>
                    %s
                    </body>
                    </html>
                    """.formatted(htmlContent), null});
            invoke(builder, "toStream", new Class[]{OutputStream.class}, new Object[]{out});
            invoke(builder, "run");
        } finally {
            out.close();
        }
    }

    private Object createPdfBuilder() {
        if (PlatformUtils.isAndroid()) {
            throw new UnsupportedOperationException("Android 环境不支持 PDF 生成");
        }
        try {
            Class<?> cls = Class.forName("com.openhtmltopdf.pdfboxout.PdfRendererBuilder");
            return cls.getDeclaredConstructor().newInstance();
        } catch (ClassNotFoundException e) {
            throw new UnsupportedOperationException("未引入 PDF 依赖（openhtmltopdf-pdfbox），无法生成 PDF", e);
        } catch (Exception e) {
            throw new IllegalStateException("初始化 PDF 渲染器失败", e);
        }
    }

    private Object enumValue(String enumClassName, String name) {
        try {
            Class<?> enumClass = Class.forName(enumClassName);
            Method valueOf = enumClass.getMethod("valueOf", String.class);
            return valueOf.invoke(null, name);
        } catch (Exception e) {
            throw new IllegalStateException("读取 PDF 枚举失败: " + enumClassName + "." + name, e);
        }
    }

    private void invoke(Object target, String methodName) {
        invoke(target, methodName, new Class[0], new Object[0]);
    }

    private void invoke(Object target, String methodName, Class<?>[] parameterTypes, Object[] args) {
        try {
            Method m = target.getClass().getMethod(methodName, parameterTypes);
            m.invoke(target, args);
        } catch (Exception e) {
            throw new IllegalStateException("调用 PDF 方法失败: " + methodName, e);
        }
    }

}
