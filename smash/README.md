# POCKET BRAWL 🥊 — Mobile Smash Arena

A **Super Smash Bros.–style platform fighter** built for phones in a single self-contained
HTML/JS file. No build step, no dependencies, no internet needed — just open
[`index.html`](./index.html) in a mobile browser (or serve the folder and visit it on your phone).

> Fan-made tribute to the platform-fighter genre. All fighters are original homage characters —
> not affiliated with Nintendo.

## How to play

1. Open `index.html` on your phone (landscape).
   Easiest: from a computer on the same Wi-Fi run `python3 -m http.server` inside this folder,
   then browse to `http://<computer-ip>:8000` on your phone.
2. Tap **TAP TO BRAWL**, pick a fighter, hit **FIGHT!**
3. Rack up your rival's **%** — the higher it climbs, the further they fly.
   Knock them past the edge of the screen to take a stock. **3 stocks each; last one standing wins.**

## Controls

| Touch | Action |
|---|---|
| **Left side of screen** | Virtual joystick — move, flick **up** to jump, hold **down** in the air to fast-fall or drop through platforms |
| **A** (red) | Attack (ground jab / aerial) |
| **B** (purple) | Special move (per character, short cooldown) |
| **JUMP** (green) | Jump / double jump |
| **GUARD** (blue) | Shield — blocks hits but shrinks; it can break! |

Keyboard also works for desktop testing: **arrows** move, **Space/W** jump, **Z** attack, **X** special, **C** shield, **Esc** pause.

## The roster

| Fighter | Style | Special |
|---|---|---|
| **ROCCO** — red-capped brawler | All-rounder | **FIREBALL** — bouncing flame projectile |
| **ZAP** — electric mouse | Fast & light, easy to launch | **VOLT SHOT** — high-speed lightning bolt |
| **LIEF** — green-clad swordsman | Long sword reach | **SPIN SLASH** — 360° blade whirlwind |
| **GORRO** — heavyweight gorilla | Slow, heavy, hits like a truck | **QUAKE FIST** — ground-shockwaves both ways (aerial: slam dive) |

## Features

- Smash-style **percent damage + scaling knockback**, hitstun, launch angles and blast-zone KOs
- **Stocks, respawn invincibility**, KO announcements, hit-pause slow-mo and screen shake
- **CPU opponent** with approach/attack/guard/recovery AI
- Dynamic **camera** that pans & zooms to frame both fighters
- Hand-drawn **procedural canvas characters** with run/jump/attack/tumble animations
- Pass-through platforms, fast-fall, double jump, shields with break-stun
- WebAudio **sound effects** (no audio files)
- Works offline; also playable on desktop with keyboard
