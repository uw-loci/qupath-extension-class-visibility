# Changelog

All notable changes to this extension are recorded here. The version in
`build.gradle.kts` is the authority for what a build calls itself; this file says what
changed between those versions.

## 0.1.1 -- unreleased

**A behaviour change, and the reason for this release.** 0.1.0 is out and installed, so read
this before upgrading: **the panel is now a session, and closing it puts your view back.**

- **Closing the panel restores the state it opened onto.** Opening the panel has always
  recorded your whole visibility surface and then hidden everything; in 0.1.0 that was a
  one-way door, and getting back meant knowing about a menu item. Closing the panel now
  replays that recording in full -- the class rules you had, the visibility mode,
  `Exact matches only`, overlay opacity, the cell display mode, and the nine show and fill
  toggles for detections, annotations, the TMA grid, connections, TMA core labels, the grid
  and pixel classification. QuPath is left exactly as you found it.

  Every way of closing does it: the floating window's close button, the toolbar button, both
  menus' `Hide panel`, a docked panel, and **quitting QuPath with the panel open** -- the last
  of which was the one route 0.1.0's close handling never reached at all.

  **What this changes for you if you are on 0.1.0.** Opening the panel to look at an image
  now costs nothing: you get your view back on the way out. In exchange, a filter you build
  *inside* the panel no longer survives the close. Keep the ones worth keeping as a
  [preset](docs/user-guide.md#presets-a-view-you-named), or leave the panel open -- docked, if
  it is in the way -- while you work.

- **Docking and undocking still do not restore anything.** They move a running panel between
  the analysis pane and its own window; your rules, `Find` text and sort order survive both
  moves, and so does QuPath's own *Undock tab* gesture. A source-level test pins the restore to
  the close path and out of both re-parenting paths, because a move that quietly reset the
  user's work would be the worst version of this feature.

- **The close guard is kept, and now runs behind the restore.** It still refuses to leave
  QuPath in `Show only checked classes` with nothing checked, which is the pair that hides
  every object at the next launch. After an ordinary close it finds nothing to do. It still
  fires -- and now always says so -- when the state being restored is *itself* that pair, set
  from QuPath's own class list before the panel was opened.

- **`The panel is closed, but N class rules are still in force` no longer fires on an ordinary
  close.** After a successful restore the rules in force are the user's own pre-existing ones,
  and announcing those back at them on every close would be noise. It still fires when the
  restore did not land, which is the case it was written for. **The toolbar button's eye is
  unchanged** and still reports rules in force with the panel closed -- rules set from QuPath's
  own class list are still rules.

- **New message, for a failure nothing has been seen to cause:**
  `Could not put the view back to how it was before the panel opened.` The restore cannot throw
  out of a close, and a close can never refuse a quit, so the worst case is that you are told
  and pointed at `Restore the state from when the panel opened` to try again.

- **`Restore the state from when the panel opened` has changed job rather than gone away.** It
  is now mostly an undo *while the panel is open*; closing does the same restore for you. It
  stays on both menus, where it needs the panel open no more than `Reset all visibility` does.

- A cancelled quit -- unsaved viewers, a running script -- now leaves the panel closed and the
  view restored, as though you had closed it yourself. In 0.1.0 it left only the mode flipped.
  Press the toolbar button again to reopen.

- 105 unit tests, up from 89: `SessionRestoreTest` (every captured field round-trips, a failed
  restore is reported rather than thrown), `CloseMessageTest` (the whole close-notification
  truth table) and one addition to `SourceDisciplineTest` pinning the restore's single call
  site.

**Not verified.** All six close routes are checked at source and by unit test; none has been
exercised in a running QuPath. Still built and tested on Linux only -- see *Reporting a
problem* in the [README](README.md).

## 0.1.0 -- 2026-08-27

First implementation. Built and unit-tested on Linux and loaded into a running QuPath there;
never run on macOS or Windows -- see *Reporting a problem* in the [README](README.md).

Note for anyone reading this after 0.1.1: in 0.1.0, closing the panel left your class rules
in force and did **not** put back the view the panel opened onto. 0.1.1 changed that.

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
  panel close and at quit rather than leaving every object invisible at the next launch. The
  startup reconciliation below covers the case where neither guard can run.
- **Opening the panel hides every object.** The toolbar button records the view you had, then
  switches to `Show only checked classes` with nothing checked, and you check your way back to
  the populations you want to see -- the workflow of the script this ports, and the right way
  round for thirty overlapping classes. The status strip says so in words, `Check all listed`
  carries a blue halo for as long as it is true, and closing the panel without checking
  anything returns the mode to `Hide checked classes`.
- A startup reconciliation: if QuPath launches in `Show only checked classes` with nothing
  checked -- reachable by force-quitting or crashing with the panel open, and from stock QuPath
  with no extension installed -- the mode is put back to `Hide checked classes` before any
  window exists, and the reason is logged. This closes the one route into a blank viewer that
  neither the panel's close guard nor the shutdown guard can reach.
- **`Restore the state from when the panel opened`**, on both menus, puts back the whole
  visibility surface -- class rules, mode, exact-match setting, overlay opacity, object-type
  toggles, fills, grid and pixel classification. The snapshot is taken automatically every time
  the panel opens, and again before the first change made from either menu with the panel
  closed, so the person who needs it never had to plan for it.
- **Named visibility presets, saved in the project**, the way Brightness & Contrast saves its
  settings: a `Preset` combo with `Save` and `Delete` in the panel header. A preset carries the
  class rules, the show/hide mode, `Exact matches only`, the cell display mode, overlay opacity,
  the object-type toggles and the panel's own checked rows, stored as JSON under
  `resources/class-visibility` in the project directory -- so it survives a restart, travels
  with the project, and is there for whoever else opens it. Presets need a project open; with
  none, the combo says so and the buttons are disabled. This replaces the single manual save
  slot, which is gone; the automatic snapshot above is deliberately kept, because a preset
  requires forethought and the person who needs a way back has by definition saved none.
- A **`Cell display`** combo in the panel header, bound to QuPath's own `View > Cell display`
  setting: change it in either place and both follow. It changes how cells are drawn everywhere
  in QuPath, not only for the classes checked here, it changes nothing about what is hidden,
  and detections that are not cells look the same whichever option is chosen.
- **Solo -- show one row and hide everything else -- is now a double-click on the row**, a
  right-click menu item naming that row, or the `O` key. The `Only` button column is gone from
  both tables: it cost a column of width in the panel where `FoxP3 (Opal 570): 1+: ...` was
  already being truncated, and it lost most of its distinctness once checking a row came to
  mean "show this".
- The components list is headed **`Anything containing these components`**, which says what
  checking a row does rather than restating what the list contains.
- Text matched by `Find` renders in **bold** in both lists, underlined instead on a soloed row,
  which is already bold. Long names still fit on one line, with an ellipsis.
- Every tooltip leads with the plainest description of the control and qualifies afterwards --
  `Spread` opens `(classes containing this component / classes in this image)`. Fifty tooltips
  and the help text were swept; no correctness caveat was dropped, only moved to second place.
- Ports the community Groovy script *"Show specific classes of objects v3"*
  ([image.sc topic 31828](https://forum.image.sc/t/31828)), which stopped working when
  `OverlayOptions.hiddenClassesProperty()` was removed in QuPath 0.7. The panel keeps the
  script's central move -- open onto an empty viewer, tick your way back -- and fixes its
  substring matching, its crash on unclassified objects, and its group checkboxes overwriting
  individual ones. The differences are listed in
  [Coming from the Groovy script](docs/migration-from-the-script.md).
