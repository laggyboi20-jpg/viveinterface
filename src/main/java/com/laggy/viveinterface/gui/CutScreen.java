package com.laggy.viveinterface.gui;

import com.laggy.viveinterface.cut.CutTool;
import com.laggy.viveinterface.render.GuiSnapshot;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.CoreShaders;
import net.minecraft.network.chat.Component;
import org.joml.Matrix4f;

/**
 * The cut UI, as a real Minecraft {@link Screen}. In VR, Vivecraft renders any open screen as the
 * flat pointer panel — so this gives us reliable visibility, native movement-lock + input capture
 * (no fragile keybind-suppression), and a place to put a close button so you never get stuck in cut
 * mode. It shows a still of the live HUD (the "desktop GUI"); drag a rectangle on it with the pointer
 * (VR laser or mouse) and press <b>Cut</b> to lift that region out as a floating panel in the world.
 *
 * <p>This is the "get it working" version — a flat drag-to-select box. Richer in-VR hand interaction
 * (Vivecraft tracks the hands over an open screen) is a planned follow-up.
 */
public class CutScreen extends Screen {

    // The HUD still, and the on-screen rectangle we draw it into.
    private int imgX, imgY, imgW, imgH;

    // Drag selection, in screen pixels (clamped to the image rect).
    private boolean dragging;
    private boolean hasSelection;
    private double selX0, selY0, selX1, selY1;

    private Component status = Component.literal("§7Drag a box over the HUD, then press Cut.");

    public CutScreen() {
        super(Component.literal("ViveInterface — Cut"));
    }

    @Override
    protected void init() {
        // Fit the HUD still into the middle of the screen, keeping its aspect ratio, leaving room for
        // the title (top) and the button bar (bottom).
        float aspect = (GuiSnapshot.width() > 0 && GuiSnapshot.height() > 0)
                ? (float) GuiSnapshot.width() / GuiSnapshot.height()
                : 16f / 9f;
        int top = 34, bottom = 40;
        int availH = Math.max(40, this.height - top - bottom);
        int availW = (int) (this.width * 0.9f);
        imgH = availH;
        imgW = (int) (imgH * aspect);
        if (imgW > availW) { imgW = availW; imgH = (int) (imgW / aspect); }
        imgX = (this.width - imgW) / 2;
        imgY = top + (availH - imgH) / 2;

        int by = this.height - 30;
        addRenderableWidget(Button.builder(Component.literal("Cut selection"), b -> cutSelection())
                .bounds(this.width / 2 - 154, by, 150, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Clear"), b -> { hasSelection = false; })
                .bounds(this.width / 2 + 4, by, 60, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
                .bounds(this.width / 2 + 68, by, 86, 20).build());
        // Close (X) in the top-right corner so you can always bail out of cut mode.
        addRenderableWidget(Button.builder(Component.literal("§cX"), b -> onClose())
                .bounds(this.width - 24, 4, 20, 20).build());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g, mouseX, mouseY, partialTick);
        g.drawCenteredString(this.font, this.title, this.width / 2, 12, 0xFFFFFF);

        if (GuiSnapshot.ready() && GuiSnapshot.texId() != 0) {
            drawHudStill(g, GuiSnapshot.texId(), imgX, imgY, imgW, imgH);
        } else {
            g.drawCenteredString(this.font,
                    Component.literal("§eHUD not captured yet — look around in-world once, then reopen."),
                    this.width / 2, imgY + imgH / 2, 0xFFFF66);
        }
        g.renderOutline(imgX - 1, imgY - 1, imgW + 2, imgH + 2, 0xFF303040);

        if (hasSelection) {
            int x0 = (int) Math.min(selX0, selX1), y0 = (int) Math.min(selY0, selY1);
            int x1 = (int) Math.max(selX0, selX1), y1 = (int) Math.max(selY0, selY1);
            g.fill(x0, y0, x1, y1, 0x3033CC55);           // translucent green wash
            g.renderOutline(x0, y0, x1 - x0, y1 - y0, 0xFF33FF66);
        }

        g.drawCenteredString(this.font, status, this.width / 2, imgY + imgH + 6, 0xCCCCCC);
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0 && inImage(mx, my)) {
            dragging = true;
            hasSelection = true;
            selX0 = selX1 = clampX(mx);
            selY0 = selY1 = clampY(my);
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (dragging && button == 0) {
            selX1 = clampX(mx);
            selY1 = clampY(my);
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (dragging && button == 0) {
            dragging = false;
            return true;
        }
        return super.mouseReleased(mx, my, button);
    }

    /** Turn the current selection rectangle into UVs on the HUD and hand it to {@link CutTool}. */
    private void cutSelection() {
        if (!hasSelection || imgW <= 0 || imgH <= 0) {
            status = Component.literal("§eDrag a box first.");
            return;
        }
        float u0 = (float) ((Math.min(selX0, selX1) - imgX) / imgW);
        float u1 = (float) ((Math.max(selX0, selX1) - imgX) / imgW);
        float v0 = (float) ((Math.min(selY0, selY1) - imgY) / imgH);
        float v1 = (float) ((Math.max(selY0, selY1) - imgY) / imgH);
        boolean ok = CutTool.placeFromUv(u0, v0, u1, v1);
        status = ok
                ? Component.literal("§aCut placed in the world. Drag another, or press Done.")
                : Component.literal("§eSelection too small — try a bigger box.");
        hasSelection = false;
    }

    // Draw our raw HUD-snapshot GL texture into the screen rect (V-flipped: framebuffers are
    // bottom-left origin). GuiGraphics.blit only takes a ResourceLocation, so we draw it by hand.
    private static void drawHudStill(GuiGraphics g, int texId, int x, int y, int w, int h) {
        RenderSystem.enableBlend();
        RenderSystem.setShader(CoreShaders.POSITION_TEX);
        RenderSystem.setShaderTexture(0, texId);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        Matrix4f m = g.pose().last().pose();
        Tesselator tess = Tesselator.getInstance();
        BufferBuilder bb = tess.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        bb.addVertex(m, x,     y,     0).setUv(0f, 1f);   // top-left
        bb.addVertex(m, x,     y + h, 0).setUv(0f, 0f);   // bottom-left
        bb.addVertex(m, x + w, y + h, 0).setUv(1f, 0f);   // bottom-right
        bb.addVertex(m, x + w, y,     0).setUv(1f, 1f);   // top-right
        BufferUploader.drawWithShader(bb.buildOrThrow());
    }

    private boolean inImage(double mx, double my) {
        return mx >= imgX && mx <= imgX + imgW && my >= imgY && my <= imgY + imgH;
    }
    private double clampX(double v) { return Math.max(imgX, Math.min(imgX + imgW, v)); }
    private double clampY(double v) { return Math.max(imgY, Math.min(imgY + imgH, v)); }

    @Override
    public boolean isPauseScreen() { return false; }   // keep VR + world ticking behind the panel
}
