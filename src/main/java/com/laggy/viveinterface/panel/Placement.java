package com.laggy.viveinterface.panel;

import org.joml.Quaternionf;

/**
 * A hand/head-relative placement: a rotation (yaw→Y, pitch→X, roll→Z, degrees) and a position offset
 * (metres, in the rotated frame), applied on top of the live controller pose. This mirrors how a VR
 * item HUD sits at a fixed spot beside the controller and follows it — so a panel glued to a hand
 * never clips through it and needs no collision. Mutable so the settings screen can tune it in place.
 */
public final class Placement {

    public float posX, posY, posZ;   // metres, in the rotated frame
    public float yaw, pitch, roll;    // degrees
    public float scale = 1f;          // used for rendered item models; ignored for panel anchors

    public Placement() {}

    public Placement(float posX, float posY, float posZ, float yaw, float pitch, float roll) {
        this(posX, posY, posZ, yaw, pitch, roll, 1f);
    }

    public Placement(float posX, float posY, float posZ, float yaw, float pitch, float roll, float scale) {
        this.posX = posX; this.posY = posY; this.posZ = posZ;
        this.yaw = yaw; this.pitch = pitch; this.roll = roll;
        this.scale = scale;
    }

    /** Rotation quaternion for this placement (Y * X * Z order, matching the translate frame). */
    public Quaternionf rotation() {
        return new Quaternionf().rotationYXZ(
                (float) Math.toRadians(yaw), (float) Math.toRadians(pitch), (float) Math.toRadians(roll));
    }

    public Placement copy() {
        return new Placement(posX, posY, posZ, yaw, pitch, roll, scale);
    }

    /** Freshly-cut piece carried in front of the off hand so you can see it. */
    public static Placement held() {
        return new Placement(0f, 0.05f, -0.15f, 0f, -20f, 0f);
    }

    /** Glued beside/above the dominant hand (wrist-HUD style). */
    public static Placement onHand() {
        return new Placement(0f, 0.10f, -0.12f, 0f, -30f, 0f);
    }

    /** Worn as a head/visor HUD, facing back toward the eyes. */
    public static Placement onHead() {
        return new Placement(0f, -0.05f, -0.40f, 180f, 0f, 0f);
    }
}
