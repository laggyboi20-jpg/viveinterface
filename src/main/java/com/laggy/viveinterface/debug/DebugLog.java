package com.laggy.viveinterface.debug;

import com.laggy.viveinterface.cut.CutTool;
import com.laggy.viveinterface.panel.Panel;
import com.laggy.viveinterface.panel.PanelManager;
import com.laggy.viveinterface.render.GuiSnapshot;
import com.laggy.viveinterface.render.GuiTexture;
import com.laggy.viveinterface.vr.VrPoses;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * Logging + on-demand state dump, so problems can be diagnosed instead of guessed. Every line goes to
 * two places: the game log (latest.log) under the "ViveInterface" logger, AND a dedicated file
 * <b>{@code logs/viveinterface.log}</b> (fresh each session) so the mod's output is easy to find on its
 * own. Gated on {@link #ENABLED} (except errors) and cheap when off; per-frame paths use
 * {@link #throttled}/{@link #once} to avoid spam.
 */
public final class DebugLog {

    private static final Logger LOG = LoggerFactory.getLogger("ViveInterface");
    public static boolean ENABLED = true;

    private static final Map<String, Long> THROTTLE = new HashMap<>();
    private static final java.util.Set<String> ONCE = new java.util.HashSet<>();

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    private static Path logFile;
    private static boolean fileTried;

    private DebugLog() {}

    public static boolean enabled() { return ENABLED; }

    /** Log the current on/off state — printed regardless of the toggle. */
    public static void announceState() {
        emit("[VI] debug logging " + (ENABLED ? "ON" : "OFF"));
    }

    public static void log(String tag, String msg) {
        if (ENABLED) emit("[VI/" + tag + "] " + msg);
    }

    public static void logf(String tag, String fmt, Object... args) {
        if (ENABLED) emit("[VI/" + tag + "] " + String.format(fmt, args));
    }

    /** Errors are always logged, toggle or not. */
    public static void error(String tag, String msg, Throwable t) {
        LOG.error("[VI/{}] {}", tag, msg, t);
        StringWriter sw = new StringWriter();
        sw.append("[VI/").append(tag).append("] ").append(msg).append('\n');
        if (t != null) t.printStackTrace(new PrintWriter(sw));
        toFile(sw.toString());
    }

    /** Log at most once per {@code ms} for a key — for per-frame paths. */
    public static void throttled(String key, long ms, String tag, String fmt, Object... args) {
        if (!ENABLED) return;
        long now = System.currentTimeMillis();
        Long last = THROTTLE.get(key);
        if (last == null || now - last >= ms) {
            THROTTLE.put(key, now);
            emit("[VI/" + tag + "] " + String.format(fmt, args));
        }
    }

    /** Log once ever per key (e.g. first successful HUD capture). */
    public static void once(String key, String tag, String msg) {
        if (ENABLED && ONCE.add(key)) emit("[VI/" + tag + "] " + msg);
    }

    public static String v(Vec3 p) {
        return p == null ? "<null>" : String.format("(%.3f, %.3f, %.3f)", p.x, p.y, p.z);
    }

    /** Full live-state snapshot — the "don't guess" button. */
    public static void dumpState() {
        boolean vr = VrPoses.vrActive();
        emit("[VI/DUMP] ----- state snapshot -----");
        emit(String.format("[VI/DUMP] vrActive=%s guiFb avail=%s size=%dx%d",
                vr, GuiTexture.available(), GuiTexture.width(), GuiTexture.height()));
        emit(String.format("[VI/DUMP] snapshot ready=%s texId=%d", GuiSnapshot.ready(), GuiSnapshot.texId()));
        CutTool cut = CutTool.get();
        emit(String.format("[VI/DUMP] grabState=%s panels=%d",
                cut.state(), PanelManager.all().size()));
        if (vr) {
            emit(String.format("[VI/DUMP] head=%s main=%s off=%s",
                    fmt(VrPoses.head()), fmt(VrPoses.mainHand()), fmt(VrPoses.offHand())));
        }
        for (int i = 0; i < PanelManager.all().size(); i++) {
            Panel p = PanelManager.all().get(i);
            emit(String.format("[VI/DUMP]   panel[%d] anchor=%s uv=(%s,%s)-(%s,%s) w=%s scale=%s",
                    i, p.anchor, f(p.u0), f(p.v0), f(p.u1), f(p.v1), f(p.widthMeters), f(p.scale)));
        }
        emit("[VI/DUMP] --------------------------");
    }

    // --- output plumbing -----------------------------------------------------

    /** Send one line to both the game log and our dedicated file. */
    private static void emit(String line) {
        LOG.info(line);
        toFile(line);
    }

    private static synchronized void toFile(String line) {
        Path f = file();
        if (f == null) return;
        try {
            Files.writeString(f, LocalTime.now().format(TS) + "  " + line + System.lineSeparator(),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ignored) {
            // never let logging throw
        }
    }

    /** Resolve (and, once, freshly create) {@code logs/viveinterface.log} in the game directory. */
    private static synchronized Path file() {
        if (fileTried) return logFile;
        fileTried = true;
        try {
            Path dir = FabricLoader.getInstance().getGameDir().resolve("logs");
            Files.createDirectories(dir);
            logFile = dir.resolve("viveinterface.log");
            Files.writeString(logFile,
                    "==== ViveInterface log — session start " + java.time.LocalDateTime.now() + " ===="
                            + System.lineSeparator(),
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (Exception e) {
            LOG.error("[VI/LOG] could not open logs/viveinterface.log", e);
            logFile = null;
        }
        return logFile;
    }

    private static String fmt(VrPoses.BodyPose p) {
        return p == null ? "<null>" : v(p.pos());
    }

    private static String f(float x) {
        return String.format("%.2f", x);
    }
}
