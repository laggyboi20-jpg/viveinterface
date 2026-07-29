package com.laggy.viveinterface.debug;

import com.laggy.viveinterface.cut.CutTool;
import com.laggy.viveinterface.panel.Panel;
import com.laggy.viveinterface.panel.PanelManager;
import com.laggy.viveinterface.render.GuiSnapshot;
import com.laggy.viveinterface.render.GuiTexture;
import com.laggy.viveinterface.vr.VrPoses;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Logging + on-demand state dump, so problems can be diagnosed instead of guessed. Events flow to the
 * game log (latest.log) under the "ViveInterface" logger. Everything is gated on {@link #ENABLED} and
 * cheap when off; per-frame paths use {@link #throttled}/{@link #once} to avoid spam. The debug key
 * (default J) dumps {@link #dumpState()}; toggle key (default L) flips logging on/off.
 */
public final class DebugLog {

    private static final Logger LOG = LoggerFactory.getLogger("ViveInterface");
    public static boolean ENABLED = true;

    private static final Map<String, Long> THROTTLE = new HashMap<>();
    private static final java.util.Set<String> ONCE = new java.util.HashSet<>();

    private DebugLog() {}

    public static boolean enabled() { return ENABLED; }

    /** Log the current on/off state — printed regardless of the toggle. */
    public static void announceState() {
        LOG.info("[VI] debug logging {}", ENABLED ? "ON" : "OFF");
    }

    public static void log(String tag, String msg) {
        if (ENABLED) LOG.info("[VI/{}] {}", tag, msg);
    }

    public static void logf(String tag, String fmt, Object... args) {
        if (ENABLED) LOG.info("[VI/{}] {}", tag, String.format(fmt, args));
    }

    /** Errors are always logged, toggle or not. */
    public static void error(String tag, String msg, Throwable t) {
        LOG.error("[VI/{}] {}", tag, msg, t);
    }

    /** Log at most once per {@code ms} for a key — for per-frame paths. */
    public static void throttled(String key, long ms, String tag, String fmt, Object... args) {
        if (!ENABLED) return;
        long now = System.currentTimeMillis();
        Long last = THROTTLE.get(key);
        if (last == null || now - last >= ms) {
            THROTTLE.put(key, now);
            LOG.info("[VI/{}] {}", tag, String.format(fmt, args));
        }
    }

    /** Log once ever per key (e.g. first successful HUD capture). */
    public static void once(String key, String tag, String msg) {
        if (ENABLED && ONCE.add(key)) LOG.info("[VI/{}] {}", tag, msg);
    }

    public static String v(Vec3 p) {
        return p == null ? "<null>" : String.format("(%.3f, %.3f, %.3f)", p.x, p.y, p.z);
    }

    public static String q(Quaternionf r) {
        return r == null ? "<null>" : String.format("(%.2f, %.2f, %.2f, %.2f)", r.x, r.y, r.z, r.w);
    }

    /** Full live-state snapshot — the "don't guess" button. */
    public static void dumpState() {
        boolean vr = VrPoses.vrActive();
        LOG.info("[VI/DUMP] ----- state snapshot -----");
        LOG.info("[VI/DUMP] vrActive={} guiFb avail={} size={}x{}",
                vr, GuiTexture.available(), GuiTexture.width(), GuiTexture.height());
        LOG.info("[VI/DUMP] snapshot ready={} texId={}", GuiSnapshot.ready(), GuiSnapshot.texId());
        CutTool cut = CutTool.get();
        LOG.info("[VI/DUMP] cutState={} trailPts={} panels={}",
                cut.state(), cut.trail().size(), PanelManager.all().size());
        if (vr) {
            LOG.info("[VI/DUMP] head={} main={} off={}",
                    fmt(VrPoses.head()), fmt(VrPoses.mainHand()), fmt(VrPoses.offHand()));
        }
        for (int i = 0; i < PanelManager.all().size(); i++) {
            Panel p = PanelManager.all().get(i);
            LOG.info("[VI/DUMP]   panel[{}] anchor={} uv=({},{})-({},{}) w={} scale={} place=({},{},{} / {},{},{})",
                    i, p.anchor, f(p.u0), f(p.v0), f(p.u1), f(p.v1), f(p.widthMeters), f(p.scale),
                    f(p.place.posX), f(p.place.posY), f(p.place.posZ),
                    f(p.place.yaw), f(p.place.pitch), f(p.place.roll));
        }
        LOG.info("[VI/DUMP] --------------------------");
    }

    private static String fmt(VrPoses.BodyPose p) {
        return p == null ? "<null>" : v(p.pos());
    }

    private static String f(float x) {
        return String.format("%.2f", x);
    }
}
