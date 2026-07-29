# ViveInterface — Developer Handoff / Architecture

This document is the portable "brain dump" of the project: what the mod does, how it's built, how
every piece fits together, and what was planned but not yet done. It exists so the project can be
picked up on a fresh machine without the original chat history. **Read this first.**

---

## 1. What the mod is

**ViveInterface** is a client-only **Fabric 1.21.4** mod that requires **Vivecraft** (VR). It lets you
**cut regions out of the flat VR HUD and place them as floating panels anywhere in VR** — on your arm,
your head, or fixed in the world. Targets: Xaero's Minimap, JourneyMap, Cobblemon overlays, the
vanilla hotbar — anything that draws to the HUD. The mod never touches those other mods.

### The core insight that makes it possible

Vivecraft already renders the **entire** flat 2D HUD into one off-screen framebuffer so it can show it
as a floating screen in VR. That framebuffer is exposed as a **public static field**:

```java
org.vivecraft.client_vr.gameplay.screenhandlers.GuiHandler.GUI_FRAMEBUFFER   // a net.minecraft.class_276 (RenderTarget)
```

So the whole mod is essentially: **sample a UV sub-rectangle of that one texture and draw it on a
world-space quad anchored to a VR body pose.** Because the framebuffer re-renders every frame, a
minimap cut onto your wrist stays **live** for free. No per-mod adapters, and the core render path
needs **no mixins** — just that public field plus Vivecraft's public `VRClientAPI` for poses/haptics.

### The in-VR user flow

1. Press **N** → a "menu" (the full HUD on a solid dark backing) spawns in front of you, and a wooden
   sword appears on your dominant hand. Cut mode is **fully modal** — every other button is inert.
2. Hold the **right trigger** and swipe the sword through the menu. The **whole blade** cuts wherever
   it crosses, leaving a **green line** (in bounds) / **red line** (off the edge).
3. When one stroke reaches **two edges** of the menu, that rectangular region **detaches and floats**
   where you cut it. The menu stays.
4. Reach your **off hand** into the floating piece (hand hitbox) → it grabs (the piece turns **green**
   while your hand is colliding with it) and the menu disappears.
5. While carrying: the **Change-hand** key (bind Quest **A**) moves the piece to the other hand.
   **Either trigger** lets go. Releasing **near a hand or head glues it there** (it then follows that
   body part while you walk); releasing elsewhere **drops it in the world**.
6. Placed pieces **persist** to disk and reload on next join, re-sampling the live HUD.

---

## 2. Build environment (IMPORTANT for the new machine)

| Thing | Value | Notes |
|-------|-------|-------|
| Minecraft | 1.21.4 | ported from 1.20.1 (see §9) |
| Loader | Fabric Loader 0.19.3 | |
| Fabric API | 0.119.4+1.21.4 | |
| Mappings | **official Mojang** (`loom.officialMojangMappings()`) | so Vivecraft's `RenderTarget`/`Vec3`/`PoseStack` names line up |
| **JDK** | **21** | 1.21.4 targets Java 21 (`options.release = 21`). `JAVA_HOME` must point at a JDK 21. |
| Gradle | 9.5 (wrapper committed) | |
| **Fabric Loom** | **`fabric-loom-remap` 1.16.3** | -remap variant: 1.21.4 is obfuscated so Loom remaps Minecraft. Same plugin the sibling ViveMonkeCraft project builds with. |
| Mod Menu | 13.0.4 (`com.terraformersmc:modmenu`) | `modCompileOnly`, optional at runtime |
| Gson | bundled by Minecraft | used for JSON config/persistence |

### Vivecraft dependency (the non-obvious part)

Vivecraft is **not on any Maven**. It's pulled from a **local production jar** that must sit next to
the project folder:

```
gradle.properties:  vivecraft_jar=../vivecraft-1.21.4-1.3.4-fabric.jar
build.gradle:       modCompileOnly files(vc)     // Loom remaps intermediary -> mojmap automatically
```

So on the new machine you must **also copy a Vivecraft 1.21.4 fabric jar** and place it **one
directory above** the `ViveInterface` folder (i.e. as a sibling), then point `vivecraft_jar` at it.
It's `modCompileOnly` because at runtime the user launches with the real Vivecraft mod installed.

### To build

```bash
# from the ViveInterface folder, with JAVA_HOME set to a JDK 21:
JAVA_HOME=".../jdk-21" ./gradlew build
```

Output: `build/libs/viveinterface-0.2.0.jar`. Drop it in `mods/` with Fabric API + Vivecraft (+ Mod
Menu optional). On Windows/Git-Bash the exact incantation used during development was:

```bash
export JAVA_HOME="C:/Program Files/Java/jdk-21"
./gradlew build --no-daemon
```

### What to copy to the new machine
- The whole **`ViveInterface`** folder (source, gradle wrapper, `build.gradle`, `gradle.properties`).
- A **Vivecraft 1.21.4 fabric jar** as a sibling of that folder (repoint `vivecraft_jar`).
- Install **JDK 21**. The Gradle wrapper handles Gradle itself; Loom/Fabric/ModMenu download on first
  build.

> The project is currently **not a git repo**. Consider `git init` on the new machine.

---

## 3. Architecture — package by package

Root package: `com.laggy.viveinterface`. Entry point: **`ViveInterfaceClient`** (`ClientModInitializer`).
It **does nothing unless Vivecraft is loaded** (`FabricLoader.isModLoaded("vivecraft")`), to avoid
classloading Vivecraft-referencing code when VR isn't present. It registers keybinds, the renderer,
the HUD mask, loads config + saved panels, and drives a client-tick loop.

### `vr/` — the Vivecraft bridge (public API only)
- **`VrPoses`** — wraps `org.vivecraft.api.client.VRClientAPI`. `vrActive()`, and `head()/mainHand()/
  offHand()` returning a `BodyPose(pos: Vec3, dir: Vec3, rot: Quaternionf)` from
  `getWorldRenderPose()`. Also `haptic(mainHand, strength)`. **All null/inactive tolerant.** Vivecraft
  has **no input/button API** — that constraint shapes everything in `cut/`.
- **`VrTriggers`** — reads the VR controller triggers. Vivecraft maps them onto the vanilla ATTACK /
  USE key bindings, so `cut()` reads ATTACK and `release()` reads USE (swappable via config). Reads
  the **raw** key-down field via the accessor mixin (see below), because our own input gate makes the
  normal `isDown()` getter lie during cut mode.

### `render/` — drawing
- **`GuiTexture`** — thin accessor for `GuiHandler.GUI_FRAMEBUFFER` (availability, color tex id, size).
- **`GuiSnapshot`** — copies the HUD framebuffer into our **own GL texture** every frame
  (`glCopyTexSubImage2D`, in the HUD callback while GUI_FRAMEBUFFER is bound). Panels sample this
  snapshot, not the live framebuffer — see masking below.
- **`HudMask`** — registered on Fabric's `HudRenderCallback`. Each frame: (1) `GuiSnapshot.capture()`,
  then (2) **punch transparent holes** into `GUI_FRAMEBUFFER` over every placed panel's UV rect
  (writes alpha 0 via `colorMask(false,false,false,true)` + `GuiGraphics.fill`). Result: the cut
  region **vanishes from Vivecraft's flat panel** (no double render) while the world panels keep full
  content from the snapshot. Wrapped in try/catch, logs failures.
- **`PanelRenderer`** — the main draw, on `WorldRenderEvents.END`. Draws every placed panel (textured
  quad sampling the snapshot sub-UV, with a V-flip because framebuffers are bottom-left origin),
  a **green tint** on the panel the hand is touching, and in cut mode: the paper + dark backing, the
  green/red sword trail, the sword item model, the selection stick item model, and the translucent
  **hand-hitbox cube**. Sword/stick are real `Items.WOODEN_SWORD`/`Items.STICK` models via
  `ItemRenderer.renderStatic` (toggleable → coloured-quad fallback), positioned by a configurable
  `Placement`.

### `panel/` — the data model
- **`PanelAnchor`** — enum: `WORLD`, `MAIN_HAND`, `OFF_HAND`, `HEAD`.
- **`Placement`** — a hand/head-relative transform: posX/Y/Z (metres), yaw/pitch/roll (degrees), and
  scale. `rotation()` = `rotationYXZ`. Static tuned defaults `onHand()/held()/onHead()`. This mirrors
  ViveTaCZ's ammo-HUD placement concept (see §6).
- **`Panel`** — a placed slice: UV rect + anchor + transform + `scale`. WORLD anchors use
  `worldPos/worldRot` (+ a `userOffset` nudge, 0,0,0 = where left); hand/head anchors use a
  `Placement` applied on the live body pose (`fromBody`). `anchorToBody()` snaps to the configured
  default placement; `dropToWorld()` bakes the current transform to a static WORLD anchor.
- **`PanelManager`** — the session list of placed panels.
- **`PanelHitbox`** — hand-sphere vs panel-oriented-box distance test. Used **only while grabbing** —
  it is deliberately **not** a continuous physics sim. Once a panel is released its transform is baked
  static, so nothing keeps running.
- **`PanelStore`** — Gson save/load to `config/viveinterface/panels.json`. Panels reload on join and
  re-sample the live HUD, so a glued minimap comes back live without re-cutting.

### `cut/` — the cutting state machine
- **`CutTool`** (singleton) — the heart. States: `OFF → ARMED → CUTTING → CUT_READY → HOLDING`.
  - `tick()` reads the triggers, advances the machine.
  - **Whole-blade cut**: `updateBladeCut()` intersects the sword **segment** (hand→tip) with the menu
    plane; records a green/red trail point; tracks which of the 4 edges the stroke has reached; when
    ≥2 edges are reached, `finalizeCut()` lifts the bounding-box region off the menu as a floating
    WORLD panel.
  - **Grab**: `tryGrab()` (in ARMED/CUT_READY) grabs whatever placed panel the **off-hand hitbox**
    reaches into (`PanelHitbox.nearestTouched`). `touchedPanel()` powers the green tint.
  - **Hold/release**: `doRelease()` glues near a hand/head (`glueIfNearBody`) or drops to world; fires
    on **either trigger**. `changeHand()` swaps the carried piece between hands.
  - Reads all geometry tunables from `ViveConfig`.
- **`CutInputGate`** — policy: while cut mode is active, **suppress every vanilla binding** except
  Vivecraft's own and ViveInterface's own keys (fully modal). Applied by the mixin.

### `mixin/` — the only two mixins (both client, target `net.minecraft.client.KeyMapping`)
- **`KeyMappingMixin`** — `@Inject` on `isDown` / `consumeClick`; when `CutInputGate.suppress()` says
  so, returns false and drains the queued `clickCount`. This is how "every other VR button is inert in
  cut mode" works, given Vivecraft has no input API.
- **`KeyMappingAccessor`** — `@Accessor("isDown")` reads the **raw** key field, bypassing the gate, so
  `VrTriggers` can still read the physical trigger state while the game itself sees the button as
  inert.

### `config/` and `gui/`
- **`ViveConfig`** — global settings persisted to `config/viveinterface/settings.json`: `debugLogging`,
  `swapTriggers`, `realModels`, cutting geometry (blade/stick length, grab/glue radii, menu
  distance/width), and per-element `Placement`s (sword, stick, hand/head/held panel defaults). All
  read live by CutTool/VrTriggers/PanelRenderer.
- **`ViveInterfaceModMenu`** — Mod Menu entry point AND the entire settings UI, built with **Cloth
  Config** (matching ViveMonkeCraft's style). Categories: General (real models / swap triggers /
  debug logging), Cutting geometry (grab & glue radii, blade/stick length, menu distance/width),
  a transform page each for sword / stick / hand-panel / head-panel / held-piece (`Placement`: X/Y/Z +
  yaw/pitch/roll + scale), and a Placed-pieces page (per-panel scale + delete). Reads live `ViveConfig`;
  persists via `ViveConfig.save()` + `PanelStore.save()` in the saving runnable. No-ops if Cloth Config
  isn't installed. (The old vanilla-widget screens — GlobalSettingsScreen / PlacementEditScreen /
  ViveInterfaceScreen — were removed in favour of this; see §10.)

### `debug/`
- **`DebugLog`** — toggle-gated logging (`log/logf/throttled/once`, `v(Vec3)`/`q(Quat)` formatters) to
  the "ViveInterface" logger, plus `dumpState()` (a full live snapshot). Modelled on ViveTaCZ's
  DebugLog/DebugState. Risky GL is wrapped in try/catch and always logs failures.

---

## 4. Controls

Just **one keybind** now (rebindable under Controls → ViveInterface); everything else moved into the
Mod Menu → ViveInterface config screen (see §10 for why).

| Input | Action |
|-------|--------|
| **N** (only keybind) | Toggle cut mode |
| Right trigger (ATTACK) | Hold to cut |
| Off hand reaching in | Grab a piece (hitbox) |
| Either trigger | Let go of the carried piece |

Config files live in `config/viveinterface/`: `settings.json` (global) and `panels.json` (placed
pieces). Everything in `settings.json` is editable from the Cloth Config screen.

> **Change-hand** (was **G** / Quest A) currently has no keybind — it lost its key in the one-keybind
> cleanup. `CutTool.changeHand()` still exists; re-add a keybind or a VR gesture if you want it back.

---

## 5. Status & things that need in-headset tuning

**Everything compiles and builds cleanly, but the mod has NEVER been tested in a headset.** The
following are deterministic in code but are educated guesses on VR specifics — all isolated and, where
possible, made tunable from the settings screen:

1. **HUD masking assumptions** (`GuiSnapshot` + `HudMask`): assumes (a) the HUD callback runs while
   `GUI_FRAMEBUFFER` is the bound read target (so the snapshot copies the right buffer), and (b)
   Vivecraft's flat panel honours the alpha channel (so alpha-0 reads as a see-through hole, not
   black). If (b) is false, change the punch to draw the world-background colour instead of clearing
   alpha. Also, if a minimap mod draws in a *later* HUD callback, the mask could be overdrawn → move
   the injection later.
2. **Item-model orientation** (sword/stick): starting rotation is a guess but fully tunable in-menu
   (Sword/Stick transform editors). Turn "Real item models" off for the coloured-quad fallback.
3. **Trigger→hand mapping**: assumes right=ATTACK=cut, left=USE=release; "Swap triggers" toggle fixes
   it. Release accepts either trigger as a safety net.
4. **Default Placements** (where glued panels sit): un-tuned guesses; expect to dial offset/rotation
   per anchor in the settings on first run.
5. **V-flip / pose space**: framebuffer origin and world-vs-render pose may need a tweak; isolated in
   `PanelRenderer` / `VrPoses`.
6. **Change-hand key must be bound to A** in Vivecraft's controls the first time (can't auto-detect).

---

## 6. Relationship to ViveTaCZ (design lineage — important)

The hand-follow approach and the debug system are **modelled on the author's other mod, ViveTaCZ**
(at `Coding shit/Intellij IDEA/ViveTacZ Refabricated`), specifically its **ammo HUD**:
- `client/VrRenderUtil.applyPlacement` (rotate yaw/pitch/roll → translate → scale on the controller
  matrix) → inspired ViveInterface's `Placement`.
- `config/Placement` (posX/Y/Z + rot + scale, tuned defaults) → same idea.
- `debug/DebugState` + `DebugLog` → same idea for ViveInterface's `DebugLog`.

**License note:** ViveTaCZ is **GPL-3.0**; ViveInterface is **MIT**. The ideas were re-implemented from
scratch — **do not copy ViveTaCZ source into ViveInterface.**

The key steer that produced the current design: *"make it like ViveTaCZ's ammo HUD because it follows
the VR hand"* → instead of a physics/collision push-out, panels glued to a hand ride a fixed
`Placement` (so they never clip through the hand), and hand-hitbox tests run **only during grabbing**,
with the result baked to a static transform. No always-on physics.

---

## 7. What was planned / TODO (roughly in priority order)

- **In-headset test pass**, then tune the items in §5 (this is the real next step).
- **More body anchors**: elbows / waist, so panels can be pinned along the arm. Small extension of
  `PanelAnchor` + `Placement` + `VrPoses` (VRBodyPart already exposes RIGHT_ELBOW/WAIST/etc.).
- **Gate grab behind the grab trigger** if touch-to-grab proves too eager in-headset (currently
  reaching a hand into a panel grabs immediately).
- **Read the Quest A button raw** (like the triggers) as an alternative to the bindable keymapping, if
  a fixed mapping can be identified.
- Possibly **default-placement presets** per HUD source (Xaero vs JourneyMap vs Cobblemon).
- Nice-to-have: an in-VR nudge gesture so placements can be tuned without opening a 2D screen.

---

## 8. Persistent project memory (old machine only)

On the development machine there is a Claude "project memory" file summarising all of the above at
`~/.claude/projects/.../memory/viveinterface-project.md`. That won't transfer automatically — **this
HANDOFF.md is the portable version.** If continuing with an AI assistant on the new machine, point it
at this file first.

---

## 9. 1.20.1 → 1.21.4 port notes

The mod was ported from Minecraft 1.20.1 to **1.21.4**. The API surface breakage was small and fully
contained — the VR/cut/panel/config/gui logic was untouched. What changed:

**Build config**
- `gradle.properties`: MC 1.21.4, Fabric Loader 0.19.3, Fabric API 0.119.4+1.21.4, Mod Menu 13.0.4.
- `build.gradle`: plugin `fabric-loom-remap` 1.16.3 (was `fabric-loom` 1.9-SNAPSHOT); `options.release`
  + source/target = **21** (was 17); Mod Menu now `com.terraformersmc:modmenu` from the TerraformersMC
  maven (was `maven.modrinth:modmenu`).
- `gradle-wrapper.properties`: Gradle **9.5.0** (was 8.12).
- `viveinterface.mixins.json`: `compatibilityLevel` **JAVA_21** (was JAVA_17).
- `fabric.mod.json`: `minecraft ~1.21.4`, `fabricloader >=0.16.0`, added `java >=21`.

**Code — only two files** (the 1.21 immediate-mode render rewrite + one Fabric callback signature):
- `render/PanelRenderer.java`:
  - Shaders: `RenderSystem.setShader(GameRenderer::getPositionTexShader/…ColorShader)` →
    `RenderSystem.setShader(CoreShaders.POSITION_TEX / POSITION_COLOR)`.
  - Immediate mode: `Tesselator.getInstance().getBuilder()` + `bb.begin(…)` →
    `BufferBuilder bb = tess.begin(…)`; `bb.vertex(m,x,y,z).uv(…)/.color(…).endVertex()` →
    `bb.addVertex(m,x,y,z).setUv(…)/.setColor(…)` (no `endVertex`); `tess.end()` →
    `BufferUploader.drawWithShader(bb.buildOrThrow())`.
- `render/HudMask.java`: `HudRenderCallback` handler param `float tickDelta` → `DeltaTracker tickDelta`
  (Fabric `onHudRender(GuiGraphics, DeltaTracker)`).

**Unchanged and verified against the 1.21.4 mojmap jar:** `ItemRenderer.renderStatic` (8-arg sig
identical), `KeyMapping` ctor + `isDown`/`consumeClick`/`clickCount`/`getName` (mixins intact),
`GuiGraphics.fill/flush/drawCenteredString`, `Button.builder`/`Component.literal` (gui screens),
`WorldRenderContext.camera()/matrixStack()`, and the `GlStateManager._*` texture calls in
`GuiSnapshot`. No `ResourceLocation`/registry breakage (the mod uses neither).

**Vivecraft API** (`org.vivecraft.api.client.VRClientAPI`, `org.vivecraft.api.data.*`, and the internal
`client_vr…GuiHandler.GUI_FRAMEBUFFER`) is assumed stable across the version bump — it is the one thing
to reverify against whatever Vivecraft 1.21.4 jar you compile/run against.

**Verified building & running** against `vivecraft-1.21.4-1.3.15-fabric.jar`: the mod loads, VR
connects, and `[VI/SNAP] first HUD capture 1280x720` confirms the framebuffer snapshot/render path
works on 1.21.4. Not yet tuned in-headset (placements etc.).

---

## 10. Control-scheme redesign → Cloth Config (post-first-headset-test)

First in-headset test feedback: the six keybinds were confusing and "did nothing visible" (e.g. the
sword appeared at the body, not the hand). Decision: **collapse to a single keybind and move every
other control into a Mod Menu settings screen**, built with **Cloth Config** to match ViveMonkeCraft.

- **Only `N` remains a keybind** (toggle cut mode) — kept because cut mode is fully modal in VR, so a
  2D menu can't be opened to exit it. Removed: `M` (release), `G` (change hand), `K` (settings),
  `J` (debug dump), `L` (debug toggle). In VR, release/grab are trigger-driven anyway.
- **`gui/ViveInterfaceModMenu`** rewritten from a one-line `GlobalSettingsScreen::new` factory into a
  full Cloth `ConfigBuilder` screen (see §3 for the category list). Deleted `GlobalSettingsScreen`,
  `PlacementEditScreen`, `ViveInterfaceScreen` (the vanilla-widget screens).
- **Build**: added `me.shedaniel.cloth:cloth-config-fabric:17.0.144` (shedaniel maven) as
  `modCompileOnly`, excluding its transitive `fabric-loader` (0.16.9, not in the offline cache — we
  provide 0.19.3). Added `cloth-config` to `fabric.mod.json` suggests. `ViveInterfaceModMenu` no-ops
  if Cloth isn't installed, so the mod still loads standalone.
- **Regression to note**: change-hand has no trigger yet (see §4 note).

---

## 11. Cut mode → a real Screen (second headset iteration)

Feedback after the Cloth change: the world-space cut menu "didn't open a visible menu." Root issue is
architectural — the old menu was a **world-space quad** spawned at `head.pos + head.look × distance`
(`CutTool.enterArmed`), so any mismatch in Vivecraft's head pose put it off-view, and the sword-swing
cutting + modal `KeyMapping` suppression were fragile. Decided (with the user) to **make cut mode a real
Minecraft `Screen`** — Vivecraft renders any open screen as the flat pointer panel, giving reliable
visibility, native movement-lock + input capture, and a close button.

- **`gui/CutScreen`** (new) — extends `Screen`, `isPauseScreen()=false`. Draws the live HUD **still**
  (`GuiSnapshot` texture, drawn by hand with a POSITION_TEX quad since `GuiGraphics.blit` needs a
  `ResourceLocation`, V-flipped). Drag a box with the pointer (VR laser / mouse via
  `mouseClicked/Dragged/Released`); **Cut** maps the selection to HUD UVs and calls
  `CutTool.placeFromUv`; **Done / corner X / Esc** closes.
- **`CutTool.placeFromUv(u0,v0,u1,v1)`** (new, static) — lifts the UV rect into a `WORLD` `Panel`
  placed `paperDistance` in front of the head (VR) or camera (desktop fallback), width from
  `paperWidth × Δu`. Adds to `PanelManager` + `PanelStore.save()`.
- **`ViveInterfaceClient`** — `N` now `setScreen(new CutScreen())` instead of `CutTool.toggle()`.
- **`PanelRenderer`** — gutted down to just placed-panel rendering + grab tint. Removed the world-space
  sword / paper / trail / selection-stick / hand-hitbox visuals and the `ItemRenderer` item-model path.
- **Menu** — dropped the now-dead controls (real-models toggle, blade/stick length, Sword & Stick
  transform pages); renamed menu-distance/width to placed-piece distance/base-width.

**Deferred (agreed with user):** the in-VR hand grab/reposition + hand-drawn selection ("Vivecraft
tracks hands in the inventory menu"). `CutTool`'s ARMED/CUTTING/HOLDING machine, `CutInputGate`, the
`KeyMapping` mixins, `VrTriggers`, and the sword/stick/hand/head `Placement`s in `ViveConfig` are left
in place but **dormant** (not invoked) for that next pass. A placed piece currently sits where it was
cut and is only resized/deleted from the menu.

**Builds clean** against `vivecraft-1.21.4-1.3.15` (Gradle 9.5 offline, JDK 21). Screen-based flow not
yet confirmed in-headset.
