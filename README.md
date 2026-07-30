# ViveInterface

Cut regions out of the flat VR HUD and place them as floating panels anywhere in VR — on your
arm, your body, or fixed in the world. Maps, Xaero's Minimap, JourneyMap, Cobblemon overlays, the
hotbar — anything that draws to the HUD works, because ViveInterface never touches those mods.

**Minecraft 1.21.4 · Fabric · requires Vivecraft.** This is a test/prototype scaffold.

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
to see and easy to get stuck in.)

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

Pressing **Done** on the cut screen opens the optional **placement mode**: it locks movement and
draws the anchor volumes (cyan hands, yellow head) so you can place pieces carefully without walking
around. Touch the green **DONE** box and squeeze to leave. The same gestures work outside it.

> **VR buttons:** Vivecraft translates controller buttons into its own SteamVR input actions, so a
> modded key binding can't be put on a face button — only the triggers/grips arrive as vanilla keys.
> That's why placement mode exits via a box you touch rather than a keybind. "Grab with off hand" is
> a bindable key if a grip works better for you than the trigger.

**N** is the mod's only keybind (rebindable under **Controls → ViveInterface**). Everything else
(cutting is the screen; all tuning is in **Mod Menu → ViveInterface**, a Cloth Config screen).

> **Coming next (per the plan):** richer in-VR interaction while the cut screen is open — Vivecraft
> tracks your hands over an open screen, so hand-drawn selection and in-VR grab/reposition of placed
> pieces are the next iteration. For now, placed pieces sit where they're cut and are tuned from the
> menu.

### Selecting & repositioning pieces (hand hitboxes, no physics engine)

Reach your **off hand** into a placed piece and it grabs — `PanelHitbox` tests your hand (a sphere,
radius = grab radius, shown as a translucent cube) against each panel's thin oriented box. This runs
**only while you're reaching/carrying**; the moment you release, the transform is baked to a static
`Placement` (ammo-HUD style), so nothing keeps simulating. A panel your hand is colliding with gets a
**green tint** so you can see it's grabbable.

While carrying a piece:
- **Change hand** key (bind your **Quest A** button to it) → the piece jumps to the other hand.
- **Either trigger** → let go. Right trigger works whichever hand holds it; left also works (it's the
  reflex a lot of people reach for). Release near a hand/head glues it there; elsewhere drops it in the
  world.

### Gluing to your body (hand-follow, not collision)

Release a held piece **near your main hand or head** and it **glues there** — it then follows that
body part while you walk. Release it anywhere else and it stays put in the world.

Following the approach of ViveTaCZ's ammo HUD, a body-anchored panel sits at a tuned **`Placement`**
(offset + yaw/pitch/roll) relative to the live controller pose — so it rides the hand at a fixed spot
*beside* it and never clips through, with no collision system needed. Defaults live in `Placement`
(`onHand` / `held` / `onHead`); fine-tune per piece in the settings screen. (More anchors — elbows,
waist — are a small extension of `PanelAnchor` + `Placement`.)

### Persistence & settings (Mod Menu → ViveInterface)

Placed pieces save to `config/viveinterface/panels.json` and reload on join — the UV rects re-sample
the live HUD, so a glued minimap comes back **live** without re-cutting. All settings live in the
**Cloth Config** screen (Mod Menu cog): General (real models / trigger swap / debug logging), Cutting
geometry (grab & glue radii, blade/stick length, menu size), a transform page each for the sword,
stick, and the default hand/head/held panel placements (XYZ + yaw/pitch/roll + scale), and a
**Placed pieces** page to resize or delete each placed panel. Everything persists on Save.

### Debugging (so we don't guess)

Modelled on ViveTaCZ's `DebugLog`/`DebugState`: events (cut / grab / release / save / snapshot /
mask) log to the game log under the `ViveInterface` logger, gated by the **Debug logging** toggle in
the config screen and rate-limited on per-frame paths. The risky GL (snapshot + mask) is wrapped in
try/catch and always logs failures.

## Build

Needs **JDK 21** and a Vivecraft 1.21.4 production jar next to this folder
(e.g. `../vivecraft-1.21.4-1.3.4-fabric.jar`, set via `vivecraft_jar` in `gradle.properties`).

```bash
JAVA_HOME=".../jdk-21" ./gradlew build
```

Output: `build/libs/viveinterface-0.2.0.jar`. Drop it in `mods/` alongside Fabric API + Vivecraft.

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
| `cut/CutTool.java` | `placeFromUv()` lifts a UV rect into a placed world panel. (Also holds a dormant grab/hold state machine kept for the next in-VR-interaction iteration.) |
| `cut/CutInputGate.java` | Legacy modal-input policy (dormant now that cutting is a native screen). |
| `mixin/KeyMappingMixin.java` | Applies that policy to `KeyMapping.isDown/consumeClick`. |
| `mixin/KeyMappingAccessor.java` | Reads the raw `isDown` field so triggers can be read past the gate. |
| `vr/VrTriggers.java` | Reads the raw ATTACK/USE trigger state (used by the dormant grab/hold flow). |
| `panel/Placement.java` | Hand/head offset + yaw/pitch/roll + scale (ammo-HUD-style follow); tuned defaults. |
| `panel/PanelHitbox.java` | Hand-sphere vs panel-box test — used only while grabbing, not a physics loop. |
| `panel/PanelStore.java` | Save/load panels to `config/viveinterface/panels.json` (survives relog). |
| `config/ViveConfig.java` | Global settings (`settings.json`): debug, trigger swap, real models, cutting geometry. |
| `gui/ViveInterfaceModMenu.java` | Mod Menu entry → the whole **Cloth Config** settings screen (general toggles, cutting geometry, per-element transform pages, placed-piece resize/delete). |
| `debug/DebugLog.java` | Toggle-gated logging (`logf`/`throttled`/`once`) + `dumpState()`. |
| `render/PanelRenderer.java` | Draws each placed panel in the world (textured quad sampling the HUD snapshot) + the grab tint. |
| `render/GuiSnapshot.java` | Per-frame copy of the HUD framebuffer so panels keep full content while the flat panel gets holed. |
| `render/HudMask.java` | Snapshots the HUD, then punches transparent holes for each placed panel (no double render). |
| `ViveInterfaceClient.java` | Registers the one keybind (**N** → `CutScreen`) + render/HUD hooks (idle if Vivecraft absent). |

## Known rough edges / TODO

- **Hand-follow replaces collision** (per ViveTaCZ's ammo HUD). Panels glued to a hand ride it at a
  fixed `Placement` offset, so they don't clip *through* the hand. World-placed panels still don't
  physically collide with hands (by design — they're stationary UI). Default placements are un-tuned
  guesses; expect to adjust offset/yaw/pitch/roll per piece in the settings screen on the first run.
- **Double-render masking is implemented but headset-dependent** (`GuiSnapshot` + `HudMask`): panels
  render from a per-frame snapshot, and cut regions are erased from the flat panel by writing alpha 0
  into `GUI_FRAMEBUFFER`. Two assumptions to verify in-headset: (a) the HUD callback runs while
  `GUI_FRAMEBUFFER` is the bound read target (so the snapshot copies the right buffer), and (b)
  Vivecraft's flat panel honours the alpha channel (so alpha-0 reads as a hole, not black). If (b) is
  false, switch the punch to draw the world-background colour instead of clearing alpha.
- **In-VR reposition of placed pieces is deferred** to the next iteration (Vivecraft tracks the hands
  over an open screen — the plan is to move the whole cut/grab experience in there). For now a cut
  piece is placed a fixed distance in front of you and is resized/deleted from the menu; the old
  off-hand grab/glue state machine still exists in `CutTool` but is dormant.
- **Placed-piece config** is menu-configurable via the Cloth Config screen: placed-piece distance &
  base width, grab/glue radii, and the default hand/head/held panel transforms (XYZ + rotation +
  scale). Stored in `config/viveinterface/settings.json`; pieces in `panels.json`.
- **V-flip / pose-space assumptions** may need a tweak on first in-headset run (framebuffer origin,
  world vs. render pose). Isolated in `PanelRenderer` / `CutTool.placeFromUv` / `VrPoses`.
- Compiles & builds clean and loads in-headset (VR connects, HUD snapshot captured); the
  screen-based cut flow still needs an in-headset pass to confirm feel and piece placement.
