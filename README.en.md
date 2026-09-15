# 🎚️ BBS Curve Fixer

An addon for the **BBS** mod. Makes shader settings properly animatable.

## Fixes

- **There was barely anything to animate** — only the options a pack author marked as sliders (usually 2–3 out of hundreds). Now **every float setting** is available: for Photon that's the difference between ~2 options and almost 300.
- **Options behind on/off guards didn't work.** Now they do — Complementary's depth-of-field focus (`DOF_FOCUS_DISTANCE`), for instance, simply couldn't be animated before, and now it can.
- **Sun, brightness, weather and sky-colour curves only worked in the editor** — during normal film playback in the world they silently did nothing. Now they work everywhere.

## Adds

- **A proper option picker** instead of a flat list of raw names. It mirrors the shader's own Iris menu: the same screens, tabs, columns and toggles. Click a setting to add a curve, already-animated ones are outlined in green, and the old list is one button away.

---

Minecraft 1.20.1 / 1.20.4 / 1.21.1 / 1.21.11 · Fabric · requires **BBS 2.6+** and Fabric API · **Iris** is what provides the picker and the shaders themselves · BBS itself is never modified.
