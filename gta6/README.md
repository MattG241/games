# NEON DRIVE — Leonida 🌴🚗

A **GTA 6-style open-world sandbox** built with [Three.js](https://threejs.org/), designed to be
played on your **TV with an Xbox controller**. Drive around a neon Miami-style beach city, run
delivery jobs for cash, cause chaos, and outrun the cops.

It's a **single self-contained folder** — no build step, no internet needed to play. Just open
`index.html` in a browser.

![Neon Drive](https://img.shields.io/badge/Three.js-open%20world-ff2d95) ![Controller](https://img.shields.io/badge/🎮-Xbox%20ready-00e5ff)

---

## ▶️ How to play on your TV

You need a browser on (or connected to) your TV, and an Xbox controller.

**Easiest — PC/laptop plugged into the TV via HDMI:**
1. Connect your computer to the TV with an HDMI cable (or cast the browser tab).
2. Pair your Xbox controller (USB cable, or Bluetooth: hold the pair button until the Xbox logo flashes, then add it in your OS Bluetooth settings).
3. Open `gta6/index.html` in Chrome, Edge, or Firefox.
4. Press **F11** for fullscreen.
5. Press **A** on the controller (or **Enter**) to start driving.

**On a game console / smart-TV browser:**
- **Xbox Series X|S / Xbox One:** open the built-in **Microsoft Edge** browser, navigate to the file (put the `gta6` folder on a USB stick or host it — see below), and the controller works automatically.
- **Smart TVs / Steam Deck / Chromebook:** any modern browser + a paired Xbox controller works the same way.

> The controller is detected automatically through the browser Gamepad API — the title screen
> will confirm **"Controller connected!"** when it sees it. No drivers or setup needed.

### Hosting it (optional)
Opening `index.html` directly works because Three.js is bundled locally. If your device won't open
local files, serve the folder over HTTP from any computer on the same network:

```bash
cd gta6
python3 -m http.server 8080
# then browse to http://<that-computer's-ip>:8080 on the TV
```

---

## 🎮 Controls

| Xbox | Keyboard | Action |
|------|----------|--------|
| **Left Stick** | `W` `A` `S` `D` | Drive / walk |
| **Right Stick** | Mouse / arrow keys | Look around |
| **RT** | `W` / `↑` | Accelerate |
| **LT** | `S` / `↓` | Brake · Reverse |
| **X** | `Space` | Handbrake (drift!) |
| **Y** | `F` | Enter / exit vehicle |
| **A** | `Shift` | Sprint (on foot) · confirm on menus |
| **Start** | `Esc` | Pause |

---

## 🌆 What's in the world

- **Open neon city** — a procedurally generated Miami/Vice-style grid with pastel art-deco towers,
  lit windows, rooftop billboards, palm trees, streetlights, a beach and an animated ocean under a
  sunset sky.
- **Drive anything** — walk up to any car and press **Y/F** to jack it. Arcade physics with
  acceleration, reverse, speed-sensitive steering, and handbrake drifting. Cars collide with buildings.
- **On-foot mode** — get out and explore on foot; the camera follows you third-person.
- **Living streets** — AI traffic cruises the roads and pedestrians wander the sidewalks.
- **Wanted system** — hit peds or ram traffic and you'll earn ★ stars. Police cruisers spawn and
  chase you; lose them to cool down, or get **busted** and lose cash. (Jacking a police car is an
  instant 2 stars.)
- **Delivery jobs** — drive to the glowing **yellow** marker to grab a package, then race it to the
  **green** drop-off for cash. New job every time. Build up your bankroll.
- **HUD** — speedometer + gear, cash, wanted stars, a rotating **minimap**, and objective tracker.

---

## 🛠️ Technical notes

- Pure **Three.js** (r160), vendored locally as `three.min.js` — the game runs fully offline.
- Everything (world generation, physics, AI, audio, HUD) lives in `index.html`. No frameworks, no build.
- Audio is synthesized live with the Web Audio API (engine, sirens, pickups, crashes) — no audio files.
- One directional (sun) light with soft shadows; neon is done with emissive materials so it stays
  fast enough for TV / console GPUs.

---

## ⚖️ Disclaimer

This is a **fan-made tribute** and an original game. It is **not affiliated with, endorsed by, or
connected to Rockstar Games or the Grand Theft Auto series**. "GTA 6-style" describes the genre and
aesthetic only. All code and assets here are original/procedural.
