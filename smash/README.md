# POCKET BRAWL 🥊 — Mobile Smash Arena

A **Super Smash Bros.–style platform fighter** for phones, rendered in real 3D with Three.js:
stylised 3D fighters battling on a classic 2D battle plane, with a dynamic pan/zoom camera —
just like the real thing. Single folder, no build step, fully self-contained (Three.js is
vendored locally in [`vendor/`](./vendor/)).

> Fan-made, non-commercial tribute. The fighters are lightweight stylised fan-art models built
> entirely from Three.js primitives — not official assets, and not affiliated with any of the
> characters' rights holders.

## The roster — 20 fighters

Spider-Man · Batman · Superman · Darth Vader · Mario · Mickey · Pikachu · Bugs Bunny · Sonic ·
Homer · Godzilla · SpongeBob · Goku · Barbie · Scooby-Doo · Optimus · Hello Kitty ·
Wonder Woman · Tom & Jerry · Kermit

Every fighter has its own **speed / jump / weight / power / reach** stats and one of four special
archetypes with a themed flavour:

| Special kind | Behaviour | Examples |
|---|---|---|
| **Projectile** | Arcing, ground-bouncing ball | Mario's Fireball, Bugs' Carrot Toss, Kermit's Lily Lob |
| **Bolt** | Fast straight shot | Pikachu's Thunderbolt, Godzilla's Atomic Breath, Superman's Heat Vision |
| **Spin** | 360° melee whirlwind | Sonic's Spin Dash, Vader's Force Slam, Wonder Woman's Lasso Whirl |
| **Quake** | Twin ground shockwaves (aerial: slam dive) | Homer's Belly Flop, Scooby Stomp, Tom & Jerry's Anvil Panic |

## How to play

1. Open `index.html` on your phone in landscape.
   Easiest: from a computer on the same Wi-Fi run `python3 -m http.server` inside this folder,
   then browse to `http://<computer-ip>:8000` on your phone. (A local server is needed because
   the game uses JS modules; any static host such as GitHub Pages also works.)
2. Tap **TAP TO BRAWL**, pick a fighter from the roster grid, hit **FIGHT!**
3. Rack up your rival's **%** — the higher it climbs, the further they fly.
   Knock them past the blast zone to take a stock. **3 stocks each; last one standing wins.**

## Controls

| Touch | Action |
|---|---|
| **Left side of screen** | Virtual joystick — move, flick **up** to jump, hold **down** in the air to fast-fall or drop through platforms |
| **A** (red) | Attack (ground jab / aerial) |
| **B** (purple) | Special move (per character, short cooldown) |
| **JUMP** (green) | Jump / double jump |
| **GUARD** (blue) | Shield — blocks hits but shrinks; it can break! |

Keyboard also works for desktop testing: **arrows/WASD** move, **Space/W** jump, **Z** attack,
**X** special, **C** shield, **Esc** pause.

## Features

- Real **3D rendering** (Three.js, vendored): shadows, sunset sky, drifting clouds, floating
  islands, Battlefield-style stage with pass-through platforms
- Smash-style **percent damage + scaling knockback**, hitstun tumbles, launch angles and
  blast-zone KOs with screen flash, slow-mo and shake
- **Stocks, respawn invincibility**, KO/GAME announcements, Smash-style bottom damage meters
  with live 3D-rendered character portraits
- **CPU opponent** with approach/attack/guard/edge-recovery AI
- Dynamic **camera** that pans and zooms to frame both fighters
- Procedural character animation: run lean, attack lunge, tumble spins, landing squash
- WebAudio **sound effects** (no audio files)
