package com.laggy.viveinterface.debug;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;

import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * Mirrors the <b>game's own</b> log into {@code logs/viveinterface.log}.
 *
 * <p>The mod's own messages were already easy to find, but the interesting failures are usually
 * about how ViveInterface, Vivecraft and Minecraft interact — a Vivecraft framebuffer complaint or a
 * GL error three lines before our own message is exactly the context that was missing when reading
 * our file alone. This attaches a log4j appender to the root logger and copies matching records
 * across, so one file tells the whole story in order.
 *
 * <p>By default it captures Vivecraft, Minecraft/rendering and ViveInterface plus anything at WARN or
 * worse from anywhere. Verbose mode captures <i>everything</i>, which is noisy but occasionally what
 * you need. Everything here is failure-tolerant: logging must never take the game down.
 */
public final class GameLogTap {

    private static final String NAME = "ViveInterfaceTap";

    /** Loggers we always mirror, whatever their level. */
    private static final String[] INTERESTING = {
            "vivecraft", "viveinterface", "minecraft", "mojang", "render", "sodium", "iris", "vivemonke"
    };

    private static boolean installed;

    private GameLogTap() {}

    /** Attach to the root logger. Safe to call more than once. */
    public static synchronized void install() {
        if (installed) return;
        installed = true;
        try {
            LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
            Logger root = ctx.getRootLogger();

            Appender appender = new AbstractAppender(NAME, null, null, true, Property.EMPTY_ARRAY) {
                @Override
                public void append(LogEvent event) {
                    try { mirror(event); } catch (Throwable ignored) { /* never break the game's logging */ }
                }
            };
            appender.start();
            root.addAppender(appender);
            DebugLog.log("LOGTAP", "mirroring the game log into logs/viveinterface.log");
        } catch (Throwable t) {
            // Wrong log4j version, a security manager, whatever — the mod's own logging still works.
            DebugLog.error("LOGTAP", "could not attach to the game log (mod-only logging continues)", t);
        }
    }

    private static void mirror(LogEvent event) {
        String logger = event.getLoggerName() == null ? "" : event.getLoggerName().toLowerCase();
        if (logger.contains("viveinterface")) return;   // our own lines are already written directly

        Level level = event.getLevel();
        boolean bad = level.isMoreSpecificThan(Level.WARN);
        if (!bad && !DebugLog.VERBOSE && !interesting(logger)) return;

        StringBuilder sb = new StringBuilder(160);
        sb.append('[').append(level).append("] ")
          .append(shortName(event.getLoggerName())).append(": ")
          .append(event.getMessage() == null ? "" : event.getMessage().getFormattedMessage());

        Throwable t = event.getThrown();
        if (t != null) {
            StringWriter sw = new StringWriter();
            t.printStackTrace(new PrintWriter(sw));
            sb.append('\n').append(sw);
        }
        DebugLog.raw(sb.toString());
    }

    private static boolean interesting(String logger) {
        for (String s : INTERESTING) if (logger.contains(s)) return true;
        return false;
    }

    /** "net.minecraft.client.Minecraft" -> "Minecraft" — keeps the file readable. */
    private static String shortName(String logger) {
        if (logger == null || logger.isEmpty()) return "?";
        int dot = logger.lastIndexOf('.');
        return dot < 0 ? logger : logger.substring(dot + 1);
    }
}
