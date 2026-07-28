# NEON DRIVE — Leonida 🌴🚗

A **GTA-style open-world sandbox** built with [Three.js](https://threejs.org/), designed to be
played on your **TV with an Xbox controller**. You spawn behind the wheel of a sports car in a neon
sunset city — drive, drift, grab cash, run delivery jobs, and outrun the cops. Uses real low-poly
**3D `.glb` models** (cars, buildings, palms, props) from the included asset packs.

![Neon Drive](https://img.shields.io/badge/Three.js-open%20world-ff2d95) ![Controller](https://img.shields.io/badge/🎮-Xbox%20ready-00e5ff)

---

## ▶️ How to play on your TV

The game loads 3D models with ES modules, so it must be **served over HTTP(S)** — opening the raw
file with `file://` won't work. Easiest options:

**Play instantly (no download) — hosted link:**
Open the GitHub-hosted URL of `gta6/index.html` (e.g. via `raw.githack.com` or GitHub Pages) in any
modern browser, including the **Xbox console's Microsoft Edge** browser. Press **A** to drive.

**Local — PC/laptop plugged into the TV via HDMI:**
1. Connect your computer to the TV via HDMI (or cast the tab).
2. Pair your Xbox controller (USB, or Bluetooth: hold the pair button until the Xbox logo flashes, then add it in your OS Bluetooth settings).
3. Serve the folder and open it:
   ```bash
   cd gta6
   python3 -m http.server 8080
   # then open http://localhost:8080  (or http://<this-pc-ip>:8080 from the TV)
   ```
4. Press **F11** for fullscreen, then **A** / **Enter** to start.

> The controller is auto-detected through the Gamepad API — the title screen confirms
> **"Controller connected!"** when it sees it. Give the stick a nudge if it doesn't register at first.

---

## 🎮 Controls

Controls follow the **official GTA V Xbox layout**:

| Xbox | Keyboard | Action |
|------|----------|--------|
| **Left Stick** | `A` `D` / `W` `S` | Steer · move |
| **RT** | `W` / `↑` | Accelerate |
| **LT** | `S` / `↓` | Brake · Reverse |
| **RB** | `Space` (in car) | Handbrake (drift!) |
| **Y** | `F` | Enter / exit vehicle |
| **A** | `Shift` | Sprint (on foot) |
| **X** | `Space` (on foot) | Jump |
| **L3** (click stick) | `G` | Horn |
| **D-Pad Left** | `R` | Cycle radio station |
| **D-Pad Right** | `H` | Headlights on/off |
| **View** | `V` | Camera mode (near / mid / far / first-person / cinematic) |
| **Right Stick** | `Q` `E` / drag mouse | Look around |
| **Start** | `Esc` | Pause / Settings |

On the menus: **D-pad / left stick** move, **A / Enter** select, **←→** change a setting,
**LB/RB** switch category, **B / Esc** back.

The chase camera automatically swings behind the car as you drive, so "forward" is always up the
screen — steering feels natural on a controller. The stick uses a quadratic response curve for
fine steering near center, and the controller **rumbles** on crashes, jumps, pickups, and busts.
Cars you leave stay parked (green dot on the minimap) — walk back and press **Y** to get back in.

---

## 🌆 What's in the world

- **Downtown city in daylight** — real street/intersection/sidewalk tiles from the Downtown City
  MegaKit, brick and metal towers assembled from its modular panels, palms and street lamps, under
  a bright GTA-style day sky.
- **You start in a car** on a central road — straight into the action, no wandering.
- **Arcade driving** — punchy acceleration, reverse, speed-sensitive steering, and handbrake
  **drifting**. Cars collide with buildings. Hit a **jump ramp** at speed to catch air.
- **Drive anything** — hop out (**Y/F**), walk up to any other car, and take it.
- **Cash to grab** — gold coins are scattered along the roads; scoop them up as you drive.
- **Living streets** — AI traffic cruises the grid and pedestrians wander the sidewalks.
- **Wanted system** — hit peds or ram traffic to earn ★ stars. Police cruisers spawn and chase you;
  break line-of-sight and they drop into a **search** state and cool down, or get **busted**. At
  3★+ the cops throw up **roadblocks** ahead of you. (Jacking a police car is an instant 2 stars.)
- **Health & armor** — bars bottom-right; crashes and cops hurt you. Grab the glowing **green**
  (health), **blue** (armor) and **yellow** (vehicle repair) pickups around the city. Run out of
  health and you get a cinematic **WASTED**; a slow bump from a cop is **BUSTED** — either way you
  respawn nearby minus some cash.
- **Delivery jobs** — drive to the **yellow** marker for a package, then race it to the **green**
  drop-off for a payout. Endless jobs.
- **Checkpoint races** — drive into the **magenta** pad to start a timed run through blue rings for
  a big cash prize.
- **Stunts & drifting** — hit a ramp for a slow-mo **stunt jump** (spins pay extra); hold a long
  **drift** to bank a cash bonus; hold throttle + brake for a smoky burnout.
- **HUD** — speedometer + gear, cash, wanted stars, a north-up **minimap**, and objective tracker.

---

## 🛠️ Technical notes

- **Three.js r160** (ES modules) + **GLTFLoader**, all vendored locally under `vendor/` and `utils/`.
- 3D assets are the included GTA-style `.glb` packs, extracted into `assets/` (110 models; ~25 are
  loaded and used at runtime). Models are authored Z-up and normalized at load (converted to Y-up,
  scaled to real-world sizes, dropped to the ground) via their runtime bounding box.
- **The city** is built from the *Downtown City MegaKit* by [Quaternius](https://quaternius.com)
  (**CC0**, from the repo's `assetsv2` release): all 153 pieces are merged into one optimized
  `downtown_kit.glb` (~10 MB, shared texture atlas, wear-mask vertex colors stripped). Streets,
  intersections and sidewalks are the kit's real tiles; buildings are a mix of the kit's complete
  buildings and towers procedurally assembled from its modular wall/window/cornice/roof panels —
  everything drawn with `InstancedMesh`, so the whole city costs only a handful of draw calls.
- **People** are rigged humanoids from the *Universal Base Characters* kit by
  [Quaternius](https://quaternius.com) (**CC0 / public domain**, from the repo's `assets-v1`
  release), repacked into compact GLBs (`char_male.glb`, `char_female.glb`, ~1.2 MB each with
  512px textures). They're skinned meshes with a full UE-style skeleton; the game lowers the
  T-pose arms and drives a procedural walk cycle by rotating thigh/calf/arm bones. Peds get
  random gender, skin tone, and a colored cap for variety; cloning uses `SkeletonUtils`.
- All game logic (world generation, vehicle physics, AI, wanted/police, missions, camera, HUD, audio)
  lives in `index.html`. No framework, no build step.
- Audio is synthesized live with the Web Audio API (engine, sirens, pickups, crashes) — no audio files.
- One directional (sun) light with soft shadows keeps it fast enough for TV / console GPUs.

### Folder layout
```
gta6/
  index.html                 the whole game
  vendor/three.module.js      Three.js (ESM)
  vendor/GLTFLoader.js        glTF loader
  utils/BufferGeometryUtils.js
  assets/*.glb                110 low-poly models (the included asset packs)
```

---

### Time of day & lighting
Five moods (Morning / Midday / Sunset / Night / Storm) cycle automatically, or pick one in
**Settings → Display → Time of Day**. The sun moves (shadows follow it), the sky, fog, ambient,
exposure and a colour grade all shift per mood; stars and a moon come out at night; roads turn wet
and reflective in the storm/night. Neon signs, rooftop billboards and lit windows glow after dark.

### Vehicles
Cars are the 10-model **generic passenger pack** (`assets/carpack.glb`, from the `car` release),
extracted at load (body + 4 wheels + lights). Wheels roll and steer; headlights, tail lights,
brake lights (on braking), reverse lights (in reverse) and police flashers all work. Drifting kicks
up **tyre smoke** and leaves **skid marks**; crashes throw **sparks**.

### Menu icons
The home/settings menus use optional icon PNGs in `assets/icons/` — see
[`assets/icons/README.md`](./assets/icons/README.md) for the exact filenames and style.
Missing icons are skipped automatically, so the menus stay clean text-only until you add them.

### Front end
- **Loading screens** — 8 hand-picked neon artworks (`assets/loading/*.webp`) rotate at random each
  load, with a **live** progress bar tied to real asset loading and rotating gameplay tips.
- **Home menu** — Continue / New Game / Missions / Settings / Controls / Fullscreen, a player card
  (cash + rank + XP), and a news bar. Fully navigable by controller (D-pad/stick + A) or keyboard.
- **Pause + Settings** — categories (Gameplay / Display / Graphics / Audio / Controls) with working
  toggles and steppers that actually change the game and persist to `localStorage`:
  units (MPH/KMH), auto-center camera, invert look, minimap on/off, difficulty, default camera
  distance, shadow quality, draw distance, master/music/SFX volumes, vibration, steering & look
  sensitivity. Open with **Start/Esc**.
- **Fullscreen** — defaults on for the Xbox browser and can be toggled in Display; the game enters
  fullscreen on the first button press (browsers require a user gesture).

### Title / loading music
Plays `gta6/assets/title_theme.mp3` on the loading and home screens (loops, fades out when you start
driving). If the file is missing the game is silent — no error.

## ⚖️ Disclaimer

This is a **fan-made tribute** and an original game. It is **not affiliated with, endorsed by, or
connected to Rockstar Games or the Grand Theft Auto series**. "GTA-style" describes the genre and
aesthetic only. The included `.glb` asset packs are low-poly generic props. Character models are
CC0 by [Quaternius](https://quaternius.com).
