# ViveInterface

Cut regions out of the flat VR HUD and place them as floating panels anywhere in VR — on your
arm, your body, or fixed in the world. Maps, Xaero's Minimap, JourneyMap, Cobblemon overlays, the
hotbar — anything that draws to the HUD works, because ViveInterface never touches those mods.

**Minecraft 1.20.1 · Fabric · requires Vivecraft.** This is a test/prototype scaffold.

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

| Step | Action |
|------|--------|
| 1 | Press **N** → a "menu" (HUD on a solid dark backing) spawns in front of you; a wooden sword appears on your dominant hand. |
| 2 | Hold the **right trigger** and swipe the sword through the menu. The **whole blade** cuts wherever it crosses. |
| 3 | When one stroke reaches **two edges** of the menu, that region detaches and floats where you cut it. The menu stays. |
| 4 | Bring your **left hand** to the floating piece to **grab** it → the menu disappears; the piece rides your off hand. |
| 5 | **Left trigger** → let go; the piece stays where your hand is. A fresh menu re-arms for the next cut. |
| — | Press **N** again anytime → leave cut mode. |

As the blade drags across the menu it leaves a **green line where it's in bounds** and a **red line
where it strays off the edge**, so you can see the cut path as you make it.

**Fully modal input:** while cut mode is active, every vanilla binding is neutralised
(`KeyMappingMixin` → `CutInputGate`) — no block-breaking, item use, inventory, hotbar, or movement.
Only Vivecraft's own bindings (VR menu / turning) and ViveInterface's keys stay live. The cut/release
triggers are read **raw** from the ATTACK/USE key state via `KeyMappingAccessor`, so suppressing them
for the game doesn't stop us reading them (`VrTriggers`).

> **Trigger mapping assumption:** right/dominant trigger = vanilla ATTACK (cut), left/off trigger =
> vanilla USE (release). If your Vivecraft bindings differ, flip the two keys in `vr/VrTriggers.java`.

Keybind (N) is rebindable under **Controls → ViveInterface**. M is a desktop fallback for "release",
**K** opens the pieces settings tab.

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

### Persistence & settings (K)

Placed pieces save to `config/viveinterface/panels.json` and reload on join — the UV rects re-sample
the live HUD, so a glued minimap comes back **live** without re-cutting. The settings tab lets you
step through pieces and adjust **scale**, **position** (WORLD nudge, 0,0,0 = where left; or the
hand/head offset), **rotation** (hand/head only), **anchor**, or delete — every change saves.

### Debugging (so we don't guess)

Modelled on ViveTaCZ's `DebugLog`/`DebugState`: events (cut / grab / release / save / snapshot /
mask) log to the game log under the `ViveInterface` logger, gated by a master toggle and rate-limited
on per-frame paths. The risky GL (snapshot + mask) is wrapped in try/catch and always logs failures.

- **J** — dump full live state (VR active, framebuffer size, snapshot texId, panels, hand poses).
- **L** — toggle logging on/off.

## Build

Needs **JDK 17** and the Vivecraft production jar next to this folder
(`../vivecraft-1.20.1-1.3.6-fabric.jar`, set via `vivecraft_jar` in `gradle.properties`).

```bash
JAVA_HOME=".../jdk-17" ./gradlew build
```

Output: `build/libs/viveinterface-0.1.0.jar`. Drop it in `mods/` alongside Fabric API + Vivecraft.

## Code map

| File | Role |
|------|------|
| `vr/VrPoses.java` | Wraps Vivecraft's public `VRClientAPI` — hand/head poses + haptics. |
| `render/GuiTexture.java` | Reads `GuiHandler.GUI_FRAMEBUFFER` (color tex id + size). |
| `panel/Panel.java` | A placed slice: UV rect + anchor + transform → resolved world transform. |
| `panel/PanelManager.java` | Session list of placed panels. |
| `cut/CutTool.java` | State machine (ARMED → CUTTING → CUT_READY → HOLDING): whole-blade cut, edge-completion, stick-tip grab, glue-on-release, trail. |
| `cut/CutInputGate.java` | Policy: which vanilla bindings are suppressed during cut mode (all but Vivecraft's + ours). |
| `mixin/KeyMappingMixin.java` | Applies that policy to `KeyMapping.isDown/consumeClick`. |
| `mixin/KeyMappingAccessor.java` | Reads the raw `isDown` field so triggers can be read past the gate. |
| `vr/VrTriggers.java` | Right (ATTACK) = cut, left (USE) = release. |
| `panel/Placement.java` | Hand/head offset + yaw/pitch/roll + scale (ammo-HUD-style follow); tuned defaults. |
| `panel/PanelHitbox.java` | Hand-sphere vs panel-box test — used only while grabbing, not a physics loop. |
| `panel/PanelStore.java` | Save/load panels to `config/viveinterface/panels.json` (survives relog). |
| `config/ViveConfig.java` | Global settings (`settings.json`): debug, trigger swap, real models, cutting geometry. |
| `gui/GlobalSettingsScreen.java` | Global settings (Mod Menu cog / ⚙ button): toggles, tunables, transform editors. |
| `gui/PlacementEditScreen.java` | Reusable XYZ + yaw/pitch/roll + scale editor for one `Placement`. |
| `gui/ViveInterfaceScreen.java` | Per-piece tab (K): scale, position, rotation, anchor, delete. |
| `gui/ViveInterfaceModMenu.java` | Mod Menu entry point → opens the global settings screen. |
| `debug/DebugLog.java` | Toggle-gated logging (`logf`/`throttled`/`once`) + state dump (J). |
| `render/PanelRenderer.java` | Draws panels (from the snapshot), the paper + backing, the green/red trail, and the blade. |
| `render/GuiSnapshot.java` | Per-frame copy of the HUD framebuffer so panels keep full content while the flat panel gets holed. |
| `render/HudMask.java` | Snapshots the HUD, then punches transparent holes for each placed panel (no double render). |
| `ViveInterfaceClient.java` | Keybinds + tick + render registration (idle if Vivecraft absent). |

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
- **Real item models** (wooden sword / stick) render at the hands, toggleable in settings. Their
  starting orientation is a guess, but now **fully tunable in-menu** — the sword/stick each have an
  XYZ + yaw/pitch/roll + scale editor, so a wrong-facing model is fixed from the settings screen (no
  code change), or turn "Real item models" off for the coloured-quad fallback.
- **Everything the mod renders is menu-configurable** via `config/viveinterface/settings.json` and the
  settings screen: trigger swap, real models, cutting geometry (blade/stick length, grab/glue radii,
  menu size), and per-element transforms (sword, stick, and the default hand/head/held panel
  placements) each with XYZ + rotation + scale.
- **V-flip / pose-space assumptions** may need a tweak on first in-headset run (framebuffer origin,
  world vs. render pose). All isolated in `PanelRenderer` / `VrPoses`.
- **Trigger→hand mapping** (ATTACK=cut, USE=release) is assumed; release also accepts *either*
  trigger as a safety net. Flip in `VrTriggers` if needed.
- Untested in-headset — compiles and builds clean; needs a real Vivecraft session to validate.
