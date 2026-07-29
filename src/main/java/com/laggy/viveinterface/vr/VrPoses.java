package com.laggy.viveinterface.vr;

import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import org.vivecraft.api.client.VRClientAPI;
import org.vivecraft.api.data.VRBodyPart;
import org.vivecraft.api.data.VRBodyPartData;
import org.vivecraft.api.data.VRPose;

/**
 * Thin wrapper over Vivecraft's public {@link VRClientAPI}. Every access is null/inactive
 * tolerant so the rest of the mod never has to touch Vivecraft types directly. Only load
 * this class when the "vivecraft" mod is present (see ViveInterfaceClient).
 */
public final class VrPoses {

    /** A body-part transform in world space (interpolated for the current render frame). */
    public record BodyPose(Vec3 pos, Vec3 dir, Quaternionf rot) {}

    private VrPoses() {}

    public static boolean vrActive() {
        VRClientAPI api = VRClientAPI.instance();
        return api != null && api.isVRInitialized() && api.isVRActive();
    }

    /** World-space pose stack for the current render frame, or null if VR isn't active. */
    private static VRPose renderPose() {
        if (!vrActive()) return null;
        return VRClientAPI.instance().getWorldRenderPose();
    }

    public static BodyPose head() {
        return of(renderPose(), VRBodyPart.HEAD);
    }

    /** Dominant hand — the one that holds the cutting sword. */
    public static BodyPose mainHand() {
        return of(renderPose(), VRBodyPart.MAIN_HAND);
    }

    /** Off hand — the one that receives / places the cut-out panel. */
    public static BodyPose offHand() {
        return of(renderPose(), VRBodyPart.OFF_HAND);
    }

    private static BodyPose of(VRPose pose, VRBodyPart part) {
        if (pose == null) return null;
        VRBodyPartData d = pose.getBodyPartData(part);
        if (d == null) return null;
        Quaternionfc q = d.getRotation();
        return new BodyPose(d.getPos(), d.getDir(), new Quaternionf(q));
    }

    /** Short haptic buzz on a hand, e.g. when the sword bites into the paper. */
    public static void haptic(boolean mainHand, float strength) {
        if (!vrActive()) return;
        VRClientAPI.instance().triggerHapticPulse(
                mainHand ? VRBodyPart.MAIN_HAND : VRBodyPart.OFF_HAND, strength);
    }
}
