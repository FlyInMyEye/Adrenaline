# Adrenaline

<p align="center">
  <img src="https://raw.githubusercontent.com/FlyInMyEye/Adrenaline/dev/assets/logo.png" alt="Adrenaline" width="200">
</p>

<p align="center">
  <a href="https://github.com/FlyInMyEye/Adrenaline"><img src="https://img.shields.io/github/stars/FlyInMyEye/Adrenaline?style=flat&label=GitHub" alt="GitHub"></a>
  <a href="https://modrinth.com/mod/flys-adrenaline"><img src="https://img.shields.io/modrinth/dt/flys-adrenaline?logo=modrinth&label=Modrinth&style=flat&color=242629&labelColor=5CA424&logoColor=1C1C1C" alt="Modrinth"></a>
  <a href="https://www.curseforge.com/minecraft/mc-mods/flys-adrenaline"><img src="https://img.shields.io/curseforge/dt/1497006?label=CurseForge&logo=curseforge&style=flat&logoColor=1C1C1C&labelColor=F16436&color=242629" alt="CurseForge downloads"></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/license-GPL--3.0--only-blue.svg?style=flat" alt="License"></a>
</p>

Adrenaline is a Minecraft mod focused on world generation performance and feel. The current implementation supports Fabric and Forge 1.20.1, but version/loader range will be expanded soon.

<p align="center"><img src="https://raw.githubusercontent.com/FlyInMyEye/Adrenaline/dev/media/home.gif" alt="Adrenaline world generation"></p>

## Why not C2ME?

Because Adrenaline is built with large modpacks in mind. It combines parallel generation with optimized noise, biome, and surface calculations, including supported modded generation work, using native code where possible.

Modpack makers can disable individual optimizations, and Adrenaline backs off when it detects another mod controlling the same functionality. Adaptive worker counts help leave room for the rest of the game while chunks generate.

Warmup is optional. It trades a longer game startup for less work when creating a world, so you can turn it off if startup time matters more to your pack.

C2ME also optimizes generation and uses native code, with extensive changes to chunk scheduling, loading, and I/O. It's a good mod, and to be honest, its much larger user base means there are more people who can help when something breaks. Adrenaline is here because I wanted to push generation speed further in heavy modpacks and make playing them feel better.

## Installation

1. Install Fly's Config Lib 1.2.0 or newer
2. Download the Adrenaline jar from the project pages below
3. Place the jar in your `mods` folder
4. Launch the game

Project pages:

- [GitHub](https://github.com/FlyInMyEye/Adrenaline)
- [Modrinth](https://modrinth.com/mod/flys-adrenaline)
- [CurseForge](https://www.curseforge.com/minecraft/mc-mods/flys-adrenaline)

## Can I use this mod in a modpack?

Son, did you see the comparison above? You SHOULD use this mod in your modpack, but remember to also add Fly's Config Lib. You can also build a debug version from source for extra debugging and profiling tools.

If your pack includes ModernFix, launch it once with Adrenaline, click **Fix and Restart** if prompted, then relaunch. Ship the updated `config/modernfix-mixins.properties` with your pack so players don't have to repeat the fix themselves.

---

P.S. The translations were AI-generated. If you spot a mistake in your language, feel free to open a pull request.

P.P.S. I've read a lot of other optimization mods' code while making Adrenaline. This mod has been around for a while, and I honestly don't remember where every bit came from. If you recognize your code and I forgot to credit you, please poke me so I can add the credit or sort out anything that shouldn't be here. And sorry if I missed you!
