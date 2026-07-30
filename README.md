# ViveInterface

Cut regions out of the flat VR HUD and place them as floating panels anywhere in VR — on your
arm, your body, or fixed in the world. Maps, Xaero's Minimap, JourneyMap, Cobblemon overlays, the
hotbar — anything that draws to the HUD works, because ViveInterface never touches those mods.

**Minecraft 1.21.4 · Fabric · requires Vivecraft.** Beta — the full cut → place → stick flow works in-headset.

## How it works (the core insight)

Vivecraft already renders the *entire* flat 2D HUD into one off-screen framebuffer so it can show it
as a floating screen in VR. That framebuffer is exposed as a public static field:

```java
org.vivecraft.client_vr.gameplay.screenhandlers.GuiHandler.GUI_FRAMEBUFFER   // a RenderTarget
```

ViveInterface samples **sub-rectangles** of that one texture and draws each on a world-space quad
anchored to a VR body pose (hand / head / world). Because the framebuffer re-renders every frame, a
minimap cut onto your wrist stays **live** for free. No per-mod adapters, no mixins for the core path
— just the public `VRClientAPI` (poses + haptics) and that one public field.

## The cutting flow (VR)

Cutting is a flat **screen** — Vivecraft renders any open Minecraft screen as the pointer panel, which
gives reliable visibility, native movement-lock + input capture, and a close button so you never get
stuck. (This replaced an earlier world-space "swing a sword through a floating HUD" flow that was hard
to see and easy to get stuck in — that code is gone.)

| Step | Action |
|------|--------|
| 1 | Press **N** → the cut screen opens (a still of your live HUD, shown as the flat pointer panel). |
| 2 | **Drag a box** over the HUD with the pointer (VR laser / mouse) — a green rectangle previews it. |
| 3 | Press **Cut** → that region lifts out as a floating panel placed in the world in front of you. Drag another box to cut more. |
| 4 | Press **Done**, the corner **X**, or **Esc** to leave the cut screen. |

Placed pieces keep re-sampling the live HUD, so a minimap you cut stays **live**. Resize or delete
them from **Mod Menu → ViveInterface → Placed pieces**.

### Moving pieces in VR (no mode needed)

Reach a hand into a placed piece — it tints **green** — and squeeze that hand's trigger to grab it.
Let go and it stays where you released it; let go **touching your other hand or your head** and it
sticks to that body part, following you as you walk. Reach across with the *other* hand to take a
stuck piece back off. (A piece ignores the hand it's already sitting on, so squeezing the trigger to
mine never re-grabs it.)

Release a piece against a **wall or floor** and it lies flat on that face instead of sinking in.
Release it touching **another piece** and it rides that one, so a cluster of panels moves as a unit.

> **Grabbing vs. mining.** By default grabbing listens to the right trigger — which is also *attack*,
> so a squeeze with your hand inside a piece grabs instead of mining. Bind **Grab with main hand** (and
> optionally **Grab with off hand**) to a controller **grip** and grabbing moves off the triggers
> entirely, leaving attack free. Grips arrive as ordinary key bindings; the face buttons do not,
> because Vivecraft translates those into its own SteamVR input actions.
>
> The left trigger is never read: Vivecraft owns it for teleport/walk and reads it from its own action,
> which the mod cannot consume — using it would grab *and* teleport.

**Controls → ViveInterface** has three bindings: **N** (open the cut screen) plus the two optional
grab keys. All tuning lives in **Mod Menu → ViveInterface** (Cloth Config).

### Persistence & settings (Mod Menu → ViveInterface)

Placed pieces save to `config/viveinterface/panels.json` and reload on join — the UV rects re-sample
the live HUD, so a glued minimap comes back **live** without re-cutting. All settings live in the
**Cloth Config** screen (Mod Menu cog): General (piece background / trigger swap / debug logging),
Pieces & placement (grab & glue radii, placed-piece distance & width, block snapping and the surface
gap), and a **Placed pieces** page to resize or delete each placed panel. Everything persists on Save.

### Debugging (so we don't guess)

Events (cut / grab / release / save / snapshot / mask) go to **`logs/viveinterface.log`** — a fresh
file each session, so the mod's output isn't buried in `latest.log` — as well as the game log under the
`ViveInterface` logger. Gated by the **Debug logging** toggle and rate-limited on per-frame paths. The
risky GL (snapshot + mask) is wrapped in try/catch and always logs failures.

## Build

Needs **JDK 21** and a Vivecraft 1.21.4 production jar next to this folder
(e.g. `../vivecraft-1.21.4-1.3.15-fabric.jar`, set via `vivecraft_jar` in `gradle.properties`).

```bash
JAVA_HOME=".../jdk-21" ./gradlew build
```

Output: `build/libs/viveinterface-<version>.jar`. Drop it in `mods/` alongside Fabric API + Vivecraft.

Build uses Fabric Loom (`fabric-loom-remap` 1.16.3), Fabric Loader 0.19.3, Fabric API
0.119.4+1.21.4, official Mojang mappings, and Gradle 9.5.

## Code map

| File | Role |
|------|------|
| `vr/VrPoses.java` | Wraps Vivecraft's public `VRClientAPI` — hand/head poses + haptics. |
| `render/GuiTexture.java` | Reads `GuiHandler.GUI_FRAMEBUFFER` (color tex id + size). |
| `panel/Panel.java` | A placed slice: UV rect + anchor + transform → resolved world transform. |
| `panel/PanelManager.java` | Session list of placed panels. |
| `gui/CutScreen.java` | The cut UI as a real MC `Screen` (Vivecraft flat panel): shows the HUD still, drag a box, **Cut** → `CutTool.placeFromUv`. |
| `cut/CutTool.java` | `placeFromUv()` lifts a UV rect into a world panel; owns VR grab / carry / release and body + piece sticking. |
| `cut/PlacementMode.java` | Optional movement-locked placing state. **No in-game entry point** — kept for testing and future ports. |
| `cut/CutInputGate.java` | Suppresses vanilla bindings while a piece is being carried, so a grab doesn't also mine. |
| `mixin/KeyMappingMixin.java` | Applies that policy to `KeyMapping.isDown/consumeClick`. |
| `mixin/KeyMappingAccessor.java` | Reads the raw `isDown` field so keys can be read past the gate. |
| `vr/VrTriggers.java` | Raw trigger state; prefers a bound grab key over the triggers. |
| `ViveKeys.java` | The mod's key bindings: **N** plus the two optional grab keys. |
| `panel/SurfaceSnap.java` | Lays a released piece flat on a block face, and keeps body-stuck pieces out of your arm. |
| `panel/Placement.java` | Legacy euler offset, kept only so older `panels.json` files still load. |
| `panel/PanelHitbox.java` | Hand-sphere vs panel-box test — used only while grabbing, not a physics loop. |
| `panel/PanelStore.java` | Save/load panels to `config/viveinterface/panels.json` (survives relog). |
| `config/ViveConfig.java` | Global settings (`settings.json`): debug, trigger swap, background colour, placement geometry. |
| `gui/ViveInterfaceModMenu.java` | Mod Menu entry → the whole **Cloth Config** settings screen (general toggles, cutting geometry, per-element transform pages, placed-piece resize/delete). |
| `debug/DebugLog.java` | Toggle-gated logging (`logf`/`throttled`/`once`) + `dumpState()`. |
| `render/PanelRenderer.java` | Draws each placed panel in the world (textured quad sampling the HUD snapshot) + the grab tint. |
| `render/GuiSnapshot.java` | Per-frame copy of the HUD framebuffer so panels keep full content while the flat panel gets holed. |
| `render/HudMask.java` | Snapshots the HUD, then punches transparent holes for each placed panel (no double render). |
| `ViveInterfaceClient.java` | Registers the one keybind (**N** → `CutScreen`) + render/HUD hooks (idle if Vivecraft absent). |

## Known rough edges / TODO

- **Hand-follow replaces collision.** A piece stuck to a body part rides it at the exact offset you
  released it at, so it never clips through; `SurfaceSnap` keeps it clear of the limb. World-placed
  panels don't physically collide with hands (by design — they're stationary UI).
- **Double-render masking is implemented but headset-dependent** (`GuiSnapshot` + `HudMask`): panels
  render from a per-frame snapshot, and cut regions are erased from the flat panel by writing alpha 0
  into `GUI_FRAMEBUFFER`. Two assumptions to verify in-headset: (a) the HUD callback runs while
  `GUI_FRAMEBUFFER` is the bound read target (so the snapshot copies the right buffer), and (b)
  Vivecraft's flat panel honours the alpha channel (so alpha-0 reads as a hole, not black). If (b) is
  false, switch the punch to draw the world-background colour instead of clearing alpha.
- **Body anchors are limited to hands + head.** Vivecraft also exposes waist/elbows/knees/feet, but
  those need full-body tracking; with a headset and two controllers only head and hands report poses.
- **Placed-piece config** is menu-configurable via the Cloth Config screen: placed-piece distance &
  base width, grab/glue radii, and the default hand/head/held panel transforms (XYZ + rotation +
  scale). Stored in `config/viveinterface/settings.json`; pieces in `panels.json`.
- **V-flip / pose-space assumptions** may need a tweak on first in-headset run (framebuffer origin,
  world vs. render pose). Isolated in `PanelRenderer` / `CutTool.placeFromUv` / `VrPoses`.
- Compiles & builds clean and loads in-headset (VR connects, HUD snapshot captured); the
  screen-based cut flow still needs an in-headset pass to confirm feel and piece placement.
