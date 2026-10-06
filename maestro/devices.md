# Device registry

Devices trusted for Maestro runs, their quirks and measured times. A device is added only with its quirks and a measured smoke time.

## OnePlus 9 Pro (reference device)

| | |
|---|---|
| Model | OnePlus 9 Pro (`LE2123`, EEA variant), serial seen by adb: `e90abf1c` |
| OS | OxygenOS `LE2123_14.0.0.1902(EX01)`, Android 14 (API 34) |
| SoC | Snapdragon 888 (`SM8350`) |
| Display | 1080 x 2412 (panel 1440 x 3216), 480 dpi, up to 120 Hz, font scale 1.0 |
| Locale / zone (as found) | `de-DE`, `Europe/Berlin` (Kompound components localise: set `en-US`, see below) |
| Measured (2026-10-06) | Maestro JVM start about 8 s; `assertVisible` 0.2 to 0.5 s; `takeScreenshot` 0.3 s; `back` 1.1 s; `tapOn` 3.8 s (2.7 s with `retryTapIfNoChange: false`); `inputText` 2.5 s; `hideKeyboard` 2 s |

### Quirks found
- **adb cannot change settings.** `settings put system|global ...` fails with `WRITE_SETTINGS` / `WRITE_SECURE_SETTINGS` `SecurityException`, and `svc power stayon true` does nothing (`mStayOn=false`). So animation scales, screen timeout and stay-awake must be set by hand, once (see "One-time setup").
- **The screen sleeps after 30 s** and a sleeping screen makes Maestro steps fail (screenshots are black, `scrollUntilVisible` times out). `device-setup.sh` therefore also runs a keep-awake loop (`input keyevent KEYCODE_WAKEUP` every 8 s) for the duration of a session when the screen cannot be kept on any other way.
- The lock screen is a plain swipe (no PIN): `input swipe 540 2000 540 700 200` unlocks it.
- System UI nodes (status bar notification icons, clock) appear in the hierarchy in the device language; selectors by text can match them.
- Gboard's suggestion strip repeats what you typed: a `tapOn: "KSlider"` while the keyboard is open can tap the suggestion instead of the list item.

### One-time setup (by hand, Settings > System > Developer options)
1. **Stay awake**: on.
2. **USB debugging (Security settings)** (OxygenOS wording: "Allow granting permissions and simulating input via USB debugging"): on. This lets adb change settings and lets Maestro's driver work reliably.
3. **Window animation scale, Transition animation scale, Animator duration scale**: off (set all three to "Animation off").
4. **Disable permission monitoring**: on, if offered.
5. **Verify apps over USB**: off.
6. **Install via USB**: on.
7. Language: **English (United States)** and time zone fixed, so Kompound's own labels ("Close", "Clear search") match the flows.

### Stop the battery manager from freezing the Maestro driver (one time, by hand)
Oplus HANS freezes `dev.mobile.maestro` while it idles and thaws it only on the next binder call, so single steps stall for about 50 s. After the first `maestro.sh` run (it leaves the driver installed): Settings > Apps > App management > Maestro > Battery usage > Allow background activity, and enable Auto launch. Check with `adb logcat -d | grep "freeze uid.*maestro"`: it should print nothing during a run.
