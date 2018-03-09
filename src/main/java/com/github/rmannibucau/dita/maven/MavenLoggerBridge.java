package com.github.rmannibucau.dita.maven;

import static java.util.Locale.ROOT;

import org.apache.maven.plugin.logging.Log;
import org.slf4j.helpers.MarkerIgnoringBase;
import org.slf4j.helpers.MessageFormatter;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class MavenLoggerBridge extends MarkerIgnoringBase {

    private final Log log;

    @Override
    public String getName() {
        return MavenLoggerBridge.class.getPackage() + ".dita";
    }

    private void log(final String level, final String message, final Throwable throwable) {
        if (throwable == null) {
            switch (level.toLowerCase(ROOT)) {
            case "trace":
            case "debug":
                log.debug(message);
                break;
            case "info":
                log.info(message);
                break;
            case "warn":
                log.warn(message);
                break;
            case "error":
                log.error(message);
                break;
            default:
                throw new IllegalArgumentException(level);
            }
        } else {
            switch (level.toLowerCase(ROOT)) {
            case "trace":
            case "debug":
                log.debug(message, throwable);
                break;
            case "info":
                log.info(message, throwable);
                break;
            case "warn":
                log.warn(message, throwable);
                break;
            case "error":
                log.error(message, throwable);
                break;
            default:
                throw new IllegalArgumentException(level);
            }
        }
    }

    @Override
    public boolean isTraceEnabled() {
        return log.isDebugEnabled();
    }

    @Override
    public void trace(final String msg) {
        log("TRACE", msg, null);
    }

    @Override
    public void trace(final String format, final Object arg) {
        log("TRACE", MessageFormatter.format(format, arg).getMessage(), null);
    }

    @Override
    public void trace(final String format, final Object arg1, final Object arg2) {
        log("TRACE", MessageFormatter.format(format, arg1, arg1).getMessage(), null);
    }

    @Override
    public void trace(final String format, final Object... arguments) {
        log("TRACE", MessageFormatter.arrayFormat(format, arguments).getMessage(), null);
    }

    @Override
    public void trace(final String msg, final Throwable throwable) {
        log("TRACE", msg, throwable);
    }

    @Override
    public boolean isDebugEnabled() {
        return log.isDebugEnabled();
    }

    @Override
    public void debug(final String msg) {
        log("DEBUG", msg, null);
    }

    @Override
    public void debug(final String format, final Object arg) {
        log("DEBUG", MessageFormatter.format(format, arg).getMessage(), null);
    }

    @Override
    public void debug(final String format, final Object arg1, final Object arg2) {
        log("DEBUG", MessageFormatter.format(format, arg1, arg1).getMessage(), null);
    }

    @Override
    public void debug(final String format, final Object... arguments) {
        log("DEBUG", MessageFormatter.arrayFormat(format, arguments).getMessage(), null);
    }

    @Override
    public void debug(final String msg, final Throwable throwable) {
        log("DEBUG", msg, throwable);
    }

    @Override
    public boolean isInfoEnabled() {
        return log.isInfoEnabled();
    }

    @Override
    public void info(final String msg) {
        log("INFO", msg, null);
    }

    @Override
    public void info(final String format, final Object arg) {
        log("INFO", MessageFormatter.format(format, arg).getMessage(), null);
    }

    @Override
    public void info(final String format, final Object arg1, final Object arg2) {
        log("INFO", MessageFormatter.format(format, arg1, arg1).getMessage(), null);
    }

    @Override
    public void info(final String format, final Object... arguments) {
        log("INFO", MessageFormatter.arrayFormat(format, arguments).getMessage(), null);
    }

    @Override
    public void info(final String msg, final Throwable throwable) {
        log("INFO", msg, throwable);
    }

    @Override
    public boolean isWarnEnabled() {
        return log.isWarnEnabled();
    }

    @Override
    public void warn(final String msg) {
        log("WARN", msg, null);
    }

    @Override
    public void warn(final String format, final Object arg) {
        log("WARN", MessageFormatter.format(format, arg).getMessage(), null);
    }

    @Override
    public void warn(final String format, final Object arg1, final Object arg2) {
        log("WARN", MessageFormatter.format(format, arg1, arg1).getMessage(), null);
    }

    @Override
    public void warn(final String format, final Object... arguments) {
        log("WARN", MessageFormatter.arrayFormat(format, arguments).getMessage(), null);
    }

    @Override
    public void warn(final String msg, final Throwable throwable) {
        log("WARN", msg, throwable);
    }

    @Override
    public boolean isErrorEnabled() {
        return log.isErrorEnabled();
    }

    @Override
    public void error(final String msg) {
        log("ERROR", msg, null);
    }

    @Override
    public void error(final String format, final Object arg) {
        log("ERROR", MessageFormatter.format(format, arg).getMessage(), null);
    }

    @Override
    public void error(final String format, final Object arg1, final Object arg2) {
        log("ERROR", MessageFormatter.format(format, arg1, arg1).getMessage(), null);
    }

    @Override
    public void error(final String format, final Object... arguments) {
        log("ERROR", MessageFormatter.arrayFormat(format, arguments).getMessage(), null);
    }

    @Override
    public void error(final String msg, final Throwable throwable) {
        log("ERROR", msg, throwable);
    }
}
