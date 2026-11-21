<p align="center">
  <img src="docs/assets/eternal-watch-banner.svg" alt="Eternal Watch — your Android companion for Eternal Lands" width="100%">
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Android-8%2B-3DDC84?logo=android&amp;logoColor=white" alt="Android 8 or newer">
  <img src="https://img.shields.io/badge/Languages-DE%20%C2%B7%20EN%20%C2%B7%20ES%20%C2%B7%20RU-58b5ad" alt="German, English, Spanish and Russian">
  <img src="https://img.shields.io/badge/License-GPL--3.0-d7bc81" alt="GPL 3.0">
</p>

<p align="center">
  <a href="https://github.com/Agundur-KDE/Eternal-Watch/releases"><strong>📥 Download the alpha</strong></a>
  &nbsp; · &nbsp;
  <a href="https://eternal-lands.agundur.de/">🌍 Quest &amp; daily guides</a>
</p>

## ⚔️ A little help on your adventures

| | In your pocket |
|---|---|
| 🕰️ **EL time** | Game clock and calendar at a glance. |
| ✨ **Special Days** | See the current day and pick your favourites from 49 days for alerts. |
| 🐉 **Invasion reports** | Read the latest warning and its age, with optional alerts and sound. |
| 📱 **Home-screen widget** | A timestamped clock/day sample and a refresh button. |
| 🧭 **Guides** | Jump to quests, dailies, Haidir, Daritha and Xaquelina in your browser. |

**No ads. No account. No tracking SDK.** Interface in 🇩🇪 German, 🇬🇧 English,
🇪🇸 Spanish and 🇷🇺 Russian; game names and live reports stay in English.

<p align="center">
  <img src="docs/assets/screenshot-overview.png" alt="Eternal Watch overview in German, with the EL clock and current Special Day" width="280">
</p>

## 🎮 Get started

Download the **APK** from [Releases](https://github.com/Agundur-KDE/Eternal-Watch/releases)
and install it on **Android 8+**. Choose your Special Days in **Alerts & settings**;
turn on background monitoring if you want alerts while the app is closed.

📶 **Unmetered connections by default.** Mobile data and metered hotspots require
an explicit opt-in. Android Data Saver pauses metered updates.

🌱 **Early alpha.** Battery saving can delay alerts. The widget shows a saved
sample, and an invasion report does not confirm that an invasion is still active.
See each release for the current test status and known limits.

<details>
<summary>🛠️ Build it yourself</summary>

Requires JDK 17+, Android SDK 36 and Python 3.9+.

```sh
bash tools/build_apk.sh
```

Produces `dist/Eternal-Watch-debug.apk`.
[Development & testing](docs/development.md) · [Release builds](docs/release.md)

</details>

---

An unofficial **Eternal Lands** companion by Agundur. Game data from
[Mercator](https://mercator.holy-eternalland.de/api), clock/alarm rules adapted from
[EL-Klock](https://github.com/Agundur-KDE/EL-Klock).
[GPL-3.0-only](LICENSE) · [Credits & provenance](NOTICE.md)
