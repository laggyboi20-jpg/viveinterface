package com.laggy.viveinterface.panel;

import com.laggy.viveinterface.config.ViveConfig;
import com.laggy.viveinterface.debug.DebugLog;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Makes a released piece lie <b>on</b> a surface instead of sinking into it.
 *
 * <p>Two cases, same idea — a piece is a flat sheet, so it should rest against things rather than
 * intersect them:
 * <ul>
 *   <li>{@link #snapToBlock} — push a piece at a wall/floor and it ends up flat on that block face.</li>
 *   <li>{@link #clearOfBody} — a piece stuck to a hand is pushed out far enough that it isn't buried
 *       halfway inside your arm.</li>
 * </ul>
 */
public final class SurfaceSnap {

    private SurfaceSnap() {}

    /**
     * If a block face is within snapping range of where this piece was released, lay the piece flat
     * against that face. Returns true if it snapped.
     *
     * <p>The probe is a ray straight through the piece along its own normal, so it finds whatever the
     * piece was being pushed into regardless of which way you were facing.
     */
    public static boolean snapToBlock(Panel panel, Vector3f centre, Quaternionf rot) {
        ViveConfig cfg = ViveConfig.get();
        if (!cfg.snapToBlocks) return false;
        Level level = Minecraft.getInstance().level;
        if (level == null) return false;

        Vector3f n = rot.transform(new Vector3f(0f, 0f, 1f)).normalize();
        float range = Math.max(0.05f, cfg.snapRange);

        // Cast both ways along the normal: the piece may have been pushed in from either side.
        BlockHitResult hit = cast(level, centre, n, range);
        if (hit == null) hit = cast(level, centre, new Vector3f(n).negate(), range);
        if (hit == null) return false;

        Direction face = hit.getDirection();
        Vec3 fv = face.getUnitVec3();
        Vector3f outward = new Vector3f((float) fv.x, (float) fv.y, (float) fv.z);
        Vec3 at = hit.getLocation();

        // Sit just off the face so the quad doesn't z-fight with the block.
        float gap = Math.max(0.001f, cfg.surfaceClearance);
        panel.worldPos.set(
                (float) at.x + outward.x * gap,
                (float) at.y + outward.y * gap,
                (float) at.z + outward.z * gap);
        panel.worldRot.set(facing(outward));
        panel.anchor = PanelAnchor.WORLD;
        panel.parentId = null;
        panel.userOffset.set(0, 0, 0);
        DebugLog.logf("SNAP", "piece snapped flat to %s face", face);
        return true;
    }

    private static BlockHitResult cast(Level level, Vector3f from, Vector3f dir, float range) {
        Vec3 a = new Vec3(from.x, from.y, from.z);
        Vec3 b = a.add(dir.x * range, dir.y * range, dir.z * range);
        BlockHitResult r = level.clip(new ClipContext(
                a, b, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, Minecraft.getInstance().player));
        return (r != null && r.getType() == HitResult.Type.BLOCK) ? r : null;
    }

    /**
     * A rotation whose +Z (the panel's normal) points along {@code forward}, kept upright so the piece
     * isn't rolled to a random angle when it lands on a wall.
     */
    private static Quaternionf facing(Vector3f forward) {
        Vector3f f = new Vector3f(forward).normalize();
        // Near-vertical faces (floor/ceiling) have no meaningful world "up" to align to.
        Vector3f up = (Math.abs(f.y) > 0.95f) ? new Vector3f(0f, 0f, 1f) : new Vector3f(0f, 1f, 0f);
        Vector3f right = new Vector3f(up).cross(f);
        if (right.lengthSquared() < 1e-6f) right.set(1f, 0f, 0f);
        right.normalize();
        Vector3f trueUp = new Vector3f(f).cross(right).normalize();
        return new Quaternionf().setFromNormalized(new Matrix3f(right, trueUp, f));
    }

    /**
     * Push a body-anchored piece out along its offset so it rests beside the limb rather than inside
     * it. {@code minDistance} is how far the piece's centre must sit from the body part's origin.
     */
    public static void clearOfBody(Panel panel, float minDistance) {
        float len = panel.relPos.length();
        if (len >= minDistance) return;
        Vector3f dir = (len > 1e-4f)
                ? new Vector3f(panel.relPos).div(len)
                // Sat exactly on the joint: push it out along the piece's own normal instead.
                : panel.relRot.transform(new Vector3f(0f, 0f, 1f)).normalize();
        panel.relPos.set(dir.mul(minDistance));
    }
}
