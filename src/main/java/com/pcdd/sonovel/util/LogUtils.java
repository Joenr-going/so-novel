package com.pcdd.sonovel.util;

import lombok.experimental.UtilityClass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;

@UtilityClass
public class LogUtils {

    private final Logger log = LoggerFactory.getLogger(LogUtils.class);

    public void info(String template, Object... args) {
        log.info(template, args);
    }

    public void warn(String template, Object... args) {
        log.warn(template, args);
    }

    public void error(String template, Object... values) {
        log.error(template, values);
    }

    public void error(Throwable t, String template, Object... values) {
        if (t == null) {
            log.error(template, values);
            return;
        }
        Object[] args = Arrays.copyOf(values, values.length + 1);
        args[values.length] = t;
        log.error(template, args);
    }

}
