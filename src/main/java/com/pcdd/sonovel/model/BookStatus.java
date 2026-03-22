package com.pcdd.sonovel.model;

import cn.hutool.core.util.StrUtil;

public enum BookStatus {
    ONGOING,
    COMPLETED,
    HIATUS,
    UNKNOWN;

    public static BookStatus from(String statusText) {
        if (StrUtil.isBlank(statusText)) {
            return UNKNOWN;
        }
        String text = statusText.toLowerCase();
        if (text.contains("完结") || text.contains("已完结") || text.contains("完本") || text.contains("全本")) {
            return COMPLETED;
        }
        if (text.contains("停更") || text.contains("断更") || text.contains("太监")) {
            return HIATUS;
        }
        if (text.contains("连载") || text.contains("更新") || text.contains("连載")) {
            return ONGOING;
        }
        return UNKNOWN;
    }
}
