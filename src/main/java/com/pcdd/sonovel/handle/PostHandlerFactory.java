package com.pcdd.sonovel.handle;

import lombok.experimental.UtilityClass;

/**
 * @author pcdd
 * Created at 2024/12/4
 */
@UtilityClass
public class PostHandlerFactory {

    public PostProcessingHandler getHandler(String extName) {
        return switch (extName) {
            case "txt" -> new TxtMergeHandler();
            case "epub" -> new EpubMergeHandler();
            case "html" -> new HtmlTocHandler();
            case "pdf" -> new PdfMergeHandler();
            default -> throw new IllegalArgumentException("Unsupported format: " + extName);
        };
    }
}
