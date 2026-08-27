# Changelog

All notable changes to this extension are recorded here. The version in
`build.gradle.kts` is the authority for what a build calls itself; this file says what
changed between those versions.

## 0.1.0 -- unreleased

First implementation. Nothing has been tagged or released yet, and no platform has been
verified -- see *Reporting a problem* in the [README](README.md).

- Class visibility panel: a list of the classes present in the current image and a list of
  the components those class names are built from, each with object counts, driving QuPath's
  own `OverlayOptions` class-visibility setting.
- `Any` / `All` combination over checked components. `All` builds a single composite class,
  which is a matching capability QuPath supports but exposes nowhere else in its interface.
- Two number columns on each class row, because one was not honest: `Count` is the objects
  carrying exactly that class, `Affects` is the objects a click would hide or show right now.
  With QuPath's default matching a class rule also reaches every class containing all of its
  parts, so on multiplexed data the two differ routinely; `Affects` renders bold when it is
  the larger.
- A `Spread` column (`26/28`) on each component row, with emphasis above 80%, so a component
  that is a near-synonym for "everything" reads as one before it is clicked.
- `Copy rules to the clipboard` on the `Active rules` table (right-click, or `Ctrl+C`), so the
  filter behind a screenshot can be recorded. Rules are deliberately never persisted.
- A toolbar button drawn as an eye -- **open** when nothing is being hidden by class,
  **slashed** whenever something is, following QuPath's own showing/hidden vocabulary. The eye
  is on its own channel from the button's pressed state, which means "the panel is open", so a
  filter left running with the panel closed is still visible without hovering anything.
- Recovery reachable two ways: the toolbar button's right-click menu **and**
  `Extensions > Class visibility`. Toolbar insertion is best-effort, so a recovery route with
  one door was the wrong design. The toolbar button's tooltip also reports how many class
  rules are in force, whether or not the panel is open, and closing the panel with rules set
  says so.
- Floating window by default; **`Dock as tab`** moves it into the analysis pane and
  **`Undock to window`** moves it back, with no loss of state either way. Installing the
  extension adds nothing to the analysis pane.
- A permanent status strip stating in words whether anything is hidden, with a one-click
  route out of the state where every object is invisible, and a one-step `Undo` that names the
  action it will reverse. `Ctrl+Z` is deliberately left to QuPath.
- A guard against QuPath's own restart footgun: `Show only checked classes` persists across a
  restart while the checked set does not, so the pair is put back to `Hide checked classes` at
  panel close and at quit rather than leaving every object invisible at the next launch.
- Save and restore of the whole visibility surface -- class rules, mode, exact-match setting,
  overlay opacity, object-type toggles, fills, grid and pixel classification -- with an
  automatic snapshot taken before the panel's first change of a session.
- Ports the community Groovy script *"Show specific classes of objects v3"*
  ([image.sc topic 31828](https://forum.image.sc/t/31828)), which stopped working when
  `OverlayOptions.hiddenClassesProperty()` was removed in QuPath 0.7. Behaviour differences
  from the script, including the substring-matching bug it had and the reversed meaning of a
  ticked box, are listed in
  [Coming from the Groovy script](docs/migration-from-the-script.md).
