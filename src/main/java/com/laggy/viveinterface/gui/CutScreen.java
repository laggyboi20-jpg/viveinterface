package com.laggy.viveinterface.gui;

import com.laggy.viveinterface.config.ViveConfig;
import com.laggy.viveinterface.cut.CutTool;
import com.laggy.viveinterface.panel.Panel;
import com.laggy.viveinterface.panel.PanelManager;
import com.laggy.viveinterface.panel.PanelStore;
import com.laggy.viveinterface.render.GuiSnapshot;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * The cut UI, as a real Minecraft {@link Screen}. In VR, Vivecraft renders any open screen as the
 * flat pointer panel — reliable visibility, native movement-lock + input capture, and a close button
 * so you never get stuck. It shows a still of the live HUD (the "desktop GUI") filling the panel;
 * drag a rectangle on it with the pointer (VR laser / mouse) and press <b>Cut</b> to lift that region
 * out as a floating panel. A strip down the right shows every piece you've cut so far.
 */
public class CutScreen extends Screen {

    private static final int STRIP_W = 108;   // right-hand column that shows cut-piece thumbnails

    // The HUD still, and the on-screen rectangle we draw it into.
    private int imgX, imgY, imgW, imgH;

    // Drag selection, in screen pixels (clamped to the image rect).
    private boolean dragging;
    private boolean hasSelection;
    private double selX0, selY0, selX1, selY1;

    private Component status = Component.literal("§7Drag a box over the HUD, then press Cut.");

    /** Hit rects for the thumbnails in the right strip: {x, y, w, h, panelIndex}. Rebuilt each frame. */
    private final List<int[]> thumbRects = new ArrayList<>();
    /** Index of the piece whose little options popup is open, or -1. */
    private int menuFor = -1;
    private int menuX, menuY;
    private static final int MENU_W = 84, MENU_H = 42;

    public CutScreen() {
        super(Component.literal("ViveInterface — Cut"));
    }

    @Override
    protected void init() {
        // Fill as much of the panel as possible (keeping the HUD aspect) so the real GUI behind it
        // doesn't peek out — leaving a slim title strip on top, a button bar below, and the
        // cut-pieces column on the right.
        float aspect = (GuiSnapshot.width() > 0 && GuiSnapshot.height() > 0)
                ? (float) GuiSnapshot.width() / GuiSnapshot.height()
                : 16f / 9f;
        int top = 22, bottom = 28, leftPad = 6;
        int availW = this.width - STRIP_W - leftPad - 6;
        int availH = this.height - top - bottom;
        imgW = availW;
        imgH = (int) (imgW / aspect);
        if (imgH > availH) { imgH = availH; imgW = (int) (imgH * aspect); }
        imgX = leftPad + (availW - imgW) / 2;
        imgY = top + (availH - imgH) / 2;

        int by = this.height - 24;
        int mid = (imgX + imgW / 2);
        addRenderableWidget(Button.builder(Component.literal("Cut selection"), b -> cutSelection())
                .bounds(mid - 150, by, 150, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Clear box"), b -> { hasSelection = false; })
                .bounds(mid + 4, by, 70, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
                .bounds(mid + 78, by, 72, 20).build());
        // Close (X) in the very top-right corner so you can always bail out of cut mode.
        addRenderableWidget(Button.builder(Component.literal("§cX"), b -> onClose())
                .bounds(this.width - 22, 2, 20, 20).build());
    }

    /**
     * No vanilla dirt/blur backdrop. In VR this screen is the flat pointer panel, and anything we don't
     * draw stays see-through — so leaving the backdrop out lets you keep watching the world while you
     * cut. The HUD still gets its own optional backing from the "Piece background" setting.
     */
    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        // intentionally empty
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        g.text(this.font, this.title, imgX, 8, 0xFFFFFF, false);

        if (GuiSnapshot.ready()) {
            // Backing first, so translucent parts of the HUD read as a sheet rather than showing the
            // world behind. Alpha 0 in the config means "no background" — leave it see-through.
            int bg = ViveConfig.get().backgroundColor;
            if (((bg >>> 24) & 0xFF) != 0) g.fill(imgX, imgY, imgX + imgW, imgY + imgH, bg);
            drawHudSub(g, GuiSnapshot.view(), imgX, imgY, imgW, imgH, 0f, 0f, 1f, 1f);
        } else {
            g.centeredText(this.font,
                    Component.literal("§eHUD not captured yet — look around in-world once, then reopen."),
                    imgX + imgW / 2, imgY + imgH / 2, 0xFFFF66);
        }
        g.outline(imgX - 1, imgY - 1, imgW + 2, imgH + 2, 0xFF303040);

        if (hasSelection) {
            int x0 = (int) Math.min(selX0, selX1), y0 = (int) Math.min(selY0, selY1);
            int x1 = (int) Math.max(selX0, selX1), y1 = (int) Math.max(selY0, selY1);
            g.fill(x0, y0, x1, y1, 0x3033CC55);           // translucent green wash
            g.outline(x0, y0, x1 - x0, y1 - y0, 0xFF33FF66);
        }

        g.centeredText(this.font, status, imgX + imgW / 2, this.height - 40, 0xCCCCCC);
        drawPieceStrip(g);
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        drawPieceMenu(g);   // on top of everything
    }

    /** The right-hand column: a thumbnail of every placed piece (its cut-out region of the HUD). */
    private void drawPieceStrip(GuiGraphicsExtractor g) {
        int sx = this.width - STRIP_W + 4;
        int sw = STRIP_W - 10;
        thumbRects.clear();
        g.text(this.font, Component.literal("§fCut pieces"), sx, 8, 0xFFFFFF, false);
        List<Panel> all = PanelManager.all();
        if (all.isEmpty()) {
            g.text(this.font, Component.literal("§8(none yet)"), sx, 24, 0x888888, false);
            return;
        }
        int y = 24;
        GpuTextureView tex = GuiSnapshot.view();
        for (int i = 0; i < all.size(); i++) {
            Panel p = all.get(i);
            float du = Math.abs(p.u1 - p.u0), dv = Math.abs(p.v1 - p.v0);
            float snapAspect = (GuiSnapshot.width() > 0 && GuiSnapshot.height() > 0)
                    ? (float) GuiSnapshot.width() / GuiSnapshot.height() : 16f / 9f;
            float thumbAspect = (dv > 1e-4f) ? (du / dv) * snapAspect : 1f;
            int tw = sw, th = Math.max(6, (int) (tw / Math.max(0.05f, thumbAspect)));
            if (th > 46) { th = 46; tw = (int) (th * thumbAspect); }
            if (y + th + 12 > this.height - 6) break;   // ran out of room
            if (GuiSnapshot.ready() && tex != null) {
                drawHudSub(g, tex, sx, y, tw, th,
                        Math.min(p.u0, p.u1), Math.min(p.v0, p.v1), Math.max(p.u0, p.u1), Math.max(p.v0, p.v1));
            }
            boolean sel = (menuFor == i);
            g.outline(sx - 1, y - 1, tw + 2, th + 2, sel ? 0xFF33FF66 : 0xFF404050);
            g.text(this.font, Component.literal("§7#" + (i + 1)), sx, y + th + 1, 0xAAAAAA, false);
            thumbRects.add(new int[]{sx, y, tw, th, i});
            y += th + 12;
        }
        g.text(this.font, Component.literal("§8click a piece"), sx, Math.min(y, this.height - 12),
                0x777777, false);
    }

    /** The tiny per-piece options popup (currently just Delete). */
    private void drawPieceMenu(GuiGraphicsExtractor g) {
        if (menuFor < 0) return;
        int x = menuX, y = menuY;
        g.fill(x, y, x + MENU_W, y + MENU_H, 0xF0101018);
        g.outline(x, y, MENU_W, MENU_H, 0xFF55FF88);
        g.text(this.font, Component.literal("§fPiece #" + (menuFor + 1)), x + 5, y + 4, 0xFFFFFF, false);
        int[] del = deleteRect();
        g.fill(del[0], del[1], del[0] + del[2], del[1] + del[3], 0xFF802020);
        g.outline(del[0], del[1], del[2], del[3], 0xFFFF6666);
        g.centeredText(this.font, Component.literal("§fDelete"),
                del[0] + del[2] / 2, del[1] + 4, 0xFFFFFF);
    }

    private int[] deleteRect() {
        return new int[]{menuX + 5, menuY + 18, MENU_W - 10, 16};
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x(), my = event.y();
        int button = event.button();
        // The per-piece popup takes priority while it's open.
        if (menuFor >= 0) {
            int[] d = deleteRect();
            if (in(mx, my, d[0], d[1], d[2], d[3])) {
                List<Panel> all = PanelManager.all();
                if (menuFor < all.size()) {
                    PanelManager.remove(all.get(menuFor));
                    PanelStore.save();
                    status = Component.literal("§aPiece deleted.");
                }
                menuFor = -1;
                return true;
            }
            if (!in(mx, my, menuX, menuY, MENU_W, MENU_H)) {
                menuFor = -1;    // clicked away → dismiss
                return true;
            }
            return true;
        }
        // Clicking a thumbnail in the right strip opens its options popup.
        for (int[] r : thumbRects) {
            if (in(mx, my, r[0], r[1], r[2], r[3])) {
                menuFor = r[4];
                menuX = Math.max(2, r[0] - MENU_W - 6);
                menuY = Math.min(r[1], this.height - MENU_H - 2);
                return true;
            }
        }
        if (button == 0 && inImage(mx, my)) {
            dragging = true;
            hasSelection = true;
            selX0 = selX1 = clampX(mx);
            selY0 = selY1 = clampY(my);
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        double mx = event.x(), my = event.y();
        int button = event.button();
        if (dragging && button == 0) {
            selX1 = clampX(mx);
            selY1 = clampY(my);
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        double mx = event.x(), my = event.y();
        int button = event.button();
        if (dragging && button == 0) {
            dragging = false;
            return true;
        }
        return super.mouseReleased(event);
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
                ? Component.literal("§aCut placed. Drag another, or press Done.")
                : Component.literal("§eSelection too small — try a bigger box.");
        hasSelection = false;
    }

    /**
     * Draw a sub-region (u0..u1, v0..v1, top-left origin) of the HUD snapshot into a screen rect.
     *
     * <p>On 1.21.4 this was a hand-rolled POSITION_TEX quad; 26.2 offers a blit that takes a
     * GpuTextureView directly, which is exactly what {@link GuiSnapshot} hands out. V is flipped
     * because framebuffer textures are bottom-left origin.
     */
    private static void drawHudSub(GuiGraphicsExtractor g, GpuTextureView tex, int x, int y, int w, int h,
                                   float u0, float v0, float u1, float v1) {
        g.blit(tex, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR),
                x, y, w, h, u0, u1, 1f - v0, 1f - v1);
    }

    private boolean inImage(double mx, double my) {
        return mx >= imgX && mx <= imgX + imgW && my >= imgY && my <= imgY + imgH;
    }
    private static boolean in(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }
    private double clampX(double v) { return Math.max(imgX, Math.min(imgX + imgW, v)); }
    private double clampY(double v) { return Math.max(imgY, Math.min(imgY + imgH, v)); }

    @Override
    public boolean isPauseScreen() { return false; }   // keep VR + world ticking behind the panel
}
