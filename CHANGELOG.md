# Changelog

All notable changes to this extension are recorded here. The version in
`build.gradle.kts` is the authority for what a build calls itself; this file says what
changed between those versions.

## 0.2.1

**Two labels and a header row.** No behaviour changed: every rule, preset and count works
exactly as it did in 0.2.0, and there is nothing to redo on upgrade.

### A label that leaned on "here", and a tooltip that was wrong about where your classes live

`Include classes with no objects here` is now **`Include classes not in this image`**. The old
label put a lot of weight on "here", which could as easily have meant this list, this panel or
this project.

Its tooltip is a **correction, not a rewording**. It used to describe the extra rows as *the
project's classes*, and that is not what QuPath keeps. Those rows come from QuPath's list of
available classes -- the one in the **Annotations** tab -- and QuPath holds that list in two
places at once: it belongs to the open project, which it is written back into on every edit,
**and** it is saved into your user preferences on quit and reloaded at startup. So with no
project open you get whatever the list held last, and a brand-new project starts out
inheriting it rather than empty. A user who read the old tooltip and concluded the list was
sealed inside one project would be surprised the first time their classes turned up somewhere
else.

The user guide repeated the same assumption wherever it named that list -- *your project's
available classes*, *your project's class list*, *what your project knows about*. Every one of
them now says QuPath's list of available classes instead, with the two-places story told once,
under [Zero-count classes](docs/user-guide.md#zero-count-classes).

### The cell-display pointer is shorter

The line under the visibility rule now reads **`See QuPath's View -> Cell Display menu`**, in
place of a sentence explaining why you might want it. It is a bare pointer now, so the reason
it is there at all moved into the guide, under
[`Cell display`: how cells are drawn](docs/user-guide.md#cell-display-how-cells-are-drawn):
"I cannot see my cells" is answered by `Cell centroids only` at least as often as by a class
rule, and nobody goes looking for a setting they do not know exists.

### The header pairs its controls up

On a **wide** panel the header controls now sit two to a row: `Preset:` with its combo, `Save`,
`Delete` and then `List:`; `Find:` with its field, the clear button and then
`Exact matches only`. That is one row back, and -- more to the point -- the preset combo stops
stretching into the right-hand edge of the panel and the width it gives up goes to the find
field, which is what you actually type into on an image with thirty near-identical class names.

On a **narrow** panel, which is what you get docked into the analysis pane, the header
**stacks** instead: `List:`, `Find:` and `Exact matches only` each keep a line of their own.
The merged rows need more width than the docked pane usually has, and below that width they do
not shrink politely -- the controls at the right-hand end are pushed off the edge and are
simply not there. Silently absent is worse than narrow, so the narrow profile does not attempt
it. **The row count in the docked panel is therefore unchanged from 0.2.0**; this round buys
back a row in the floating window only. One thing did move there: `Exact matches only` used to
sit under the two visibility-rule radios and now sits below `Find:`, with the warning it raises
directly beneath it.

The widths behind that decision come from JavaFX probes of the two rows built with the real
strings, not from the panel running inside QuPath.

### Room above the list headers

Both list headers -- `Classes on detections in this image (28)` and
`Anything containing these components (17)` -- were flush against whatever sat above them: the
`Find` field on a wide panel, the split divider on a narrow one. Both now have a clear line's
worth of space, set once in the code that builds both lists rather than twice in two places
that could drift apart.

## 0.2.0

**Two bug fixes and a simplification pass.** The bugs are first because one of them changed
what the panel *told* you about your own filter, and if you used 0.1.x on multiplexed data it
is worth two minutes.

An external tester, **Sara McArdle**, ran the extension on her own multiplexed images and
reported both bugs; her verdict on everything else -- *"I like a lot about it, there's a lot
that's clever, but overall, it's too much"* -- is why the rest of this release takes things
off the screen rather than adding them. Two combo boxes, one checkbox, three buttons and two
table columns are gone from the default view. **Presets are untouched**, in code and in
behaviour; every preset saved by 0.1.x still loads.

### A rule you were told was doing nothing may have been hiding thousands of objects

This is the one to read. **No object was ever hidden or shown wrongly** -- the viewer was
always right, and no preset, rule or saved file is affected. What was wrong was the sentence
describing it, and it was wrong in the direction that matters: it called a working rule inert.

`1 rule has no class in this image.` on the status strip, and `Not in this image` and
`Composite -- not in QuPath's class list` in the `Active rules` table, all asked the same
question, and it was the wrong one: **is this rule's name one of the class names I found in
this image?** That is set membership. It is not what a user is asking, and on combinatorial
class names it gets the answer backwards two different ways:

- A rule for `CD8+` on an image where every object is classified `CD8+: GzB+` or `CD8+: PD1+`
  matched no class name *literally*, so it was reported as having no class in the image --
  while hiding every one of those objects. This is the normal case on a multiplex panel rather
  than an edge case: when almost nothing is a bare marker class, almost every useful rule
  reaches its objects through derived class names rather than by exact name.
- An `All` combination built from the components list -- `CD8+ + GzB+ (all components)` -- is
  **never** a class name in anybody's image. That is the whole point of it. So every `All`
  composite rule ever made was miscounted this way, by construction, and the two messages
  openly contradicted each other in the same frame: the rules table called the entry a
  composite while the strip counted the same entry as having no class here.

Every one of those counts now asks what the rule actually **reaches** -- the same number the
`Affects` column has always shown, which is the number that mirrors QuPath's own matching.
The strip, the `Active rules` status column and the `Affects` column are now three renderings
of one figure and cannot disagree.

The wording changed with the meaning:

| where | 0.1.x | 0.2.0 |
|---|---|---|
| the status sentence | `1 rule has no class in this image.` | `1 rule matches nothing listed above.` -- and it moved into `Active rules` with the rest of the routine status, below |
| `Active rules` status, a class rule | `Not in this image` | `Matches classes listed above` -- or `Matches nothing listed above`, if it really does reach nothing |
| `Active rules` status, an `All` composite | `Composite -- not in QuPath's class list` | the same two, decided by the same test |

`Composite` is gone from the status column rather than corrected. The `Rule` column already
renders the entry as `CD8+ + GzB+ (all components)` and the `Source` column already reads
`Components (All)`, so the status column was spending itself on a fact stated twice beside it
instead of on the only thing it is there for: whether the rule is doing anything.

Two smaller consequences of the same fix. The clause is now **suppressed entirely when the
list is empty** -- with no objects in the chosen `List` scope every rule trivially reaches
nothing, and reporting that would be a statement about the scope dressed up as a statement
about your rules. And soloing a component with `Exact matches only` on no longer claims to be
showing a population over a blank viewer; the strip now appends
`1 rule matches nothing listed above.` to that sentence, which is what is happening.

### The `Active rules` table asserted the opposite of the truth when it was empty

Also reported by the tester, and visible on **every launch**. With no rules yet, the empty
`Active rules` table read `No rules are active. Every object is visible.` -- true under
`Hide checked classes`, and the exact inverse under `Show only checked classes` with nothing
checked, which is the state the panel deliberately opens in. So the table contradicted the
status strip four inches below it, in the state a first-time user meets first.

The empty table now reads, when that is the case:
`No rules are active, and "Show only checked classes" is on, so every object is hidden. Check
a class above to see it.` One function now decides the everything-hidden condition, and the
four places that report it -- the status strip, this placeholder, the haloed check control and
the toolbar button's tooltip -- all read it, so they cannot drift apart again.

### The classes header counted rows it then placed "in this image"

Found while sweeping for more of the same, not reported. With
`Include classes with no objects here` ticked, `Classes on detections in this image (28)`
included QuPath's available classes **this image does not use** -- so the header asserted "in this
image" over rows that by definition failed it, and disagreed with the spread denominator in
the component list right beside it. Both numbers in that header now count present rows only.
The unused rows are still listed, still sort last, and still show a count of zero.

### What came off the screen

- **The `Cell display` combo is gone.** It was the one control in the panel with nothing to do
  with classes, and it duplicated QuPath's own `View > Cell display`. In its place is a line of
  static text: `Cell visibility is also affected by Cell display settings -- see QuPath's View
  menu.` **Presets still save and restore the cell display mode**, and so does the automatic
  snapshot taken when the panel opens -- both were left exactly as they were.

- **`Auto-refresh counts` is gone, and counts always refresh.** The checkbox, the `Refresh`
  button it revealed and the saved preference are all deleted. Turning it off bought
  responsiveness on a very large image at the price of numbers that were silently the previous
  run's, which is the failure mode the `Count (stale)` marker exists to catch. Everything that
  made counting cheap is still there and was never a user-facing option: the 300 ms debounce,
  the off-thread harvest, the spinner and its grace period, and the dropping of superseded
  results. `Count (stale)` still marks the column while a recount is in flight, and now means
  only that.

- **Two table columns are off by default.** The classes list shows the checkbox, the class and
  **`Affects`**; `Count` is off. The components list shows the checkbox, the component and
  `Count`; `Spread` is off. Both are one click away in the **column menu button** at the right
  end of each table's header row, and neither number has changed.

  `Affects` is the one kept because it is the one that answers "what will this click do".
  `Count` is objects carrying exactly that class, and on a combinatorial panel a click reaches
  several times more than that -- showing both side by side put a trap next to the answer. The
  component list keeps `Count` instead, because for a component row there is no gap: a
  component's count is already "objects whose class contains this component", which is exactly
  what a component rule matches.

  **The classes list now sorts by `Affects` descending** by default, where it used to sort by
  `Count`; the intent is the same -- most populous first -- but a table sorted by a column
  nobody can see is sorted for no stated reason. **`Affects` is also no longer dropped when the
  panel is narrow**, which it used to be to give class names room. With `Count` off the names
  have that room anyway, and hiding `Affects` would have hidden the one number on the row that
  says what the click does.

  **The column menu was listing every column as a blank row**, which would have made "one click
  away" false as shipped. JavaFX reads those menu entries from a column's text, and every
  header in this panel had its text blanked so it could carry a tooltip. The names are back on
  the columns and the tooltips now attach to the header itself, so the menu names what it
  offers. One wart remains, reported rather than fixed: the checkbox columns have no name, so
  each still appears in its menu as a blank entry. Clicking it now does nothing -- the checkbox
  column re-shows itself immediately, in both tables -- where in 0.1.2 it removed the column.

- **`Check all listed` and `Uncheck all listed` are gone**, replaced by a single check box in
  the **header of the classes list's checkbox column**. It does both jobs, and it still acts on
  exactly what is listed, so a `Find` filter still limits it. It is three-state: ticked when
  everything listed is checked, clear when nothing is, and dashed when some are -- a plain box
  over a partly-checked list has to claim one of the two and is wrong about most of the rows
  under it. Clicking cycles checked and unchecked only; you are never made to click through the
  dashed state. With nothing listed it is disabled, as the two buttons were, so a click on an
  empty list cannot look ignored. `Undo` still names the step: `Undo "Check all listed"`.

  **The blue halo moved here** from `Check all listed`. When every object is hidden, this is
  now the control that puts them back, and it is haloed for as long as that is true. A bare box
  in a header has no visible label, so its tooltip and its screen-reader text carry the whole
  meaning and both change with that state.

- **The routine status sentence moved into the `Active rules` expander.** `[OK] No class rules
  active`, `[i] 3 rules active -- only objects matching them are shown`, the solo messages and
  the new matches-nothing clause now live inside it, instead of holding a line of the panel
  open all session. The expander's own title already carries the count -- `Active rules (3)` --
  so the number is still on screen without opening anything, and so is the toolbar button's
  tooltip, which carries it from outside the panel entirely.

  **The `[!] Every object is hidden` warning stays on the always-visible strip**, deliberately.
  It is the only on-screen explanation for a blank viewer, and burying it inside a collapsed
  control would be the exact failure this panel exists to prevent. The
  `"positive" is in 26 of 28 classes and 401,552 of 452,110 objects.` note stays out there too:
  it describes the click you just made and clears on the next one, so inside a collapsed
  expander it would be a reply nobody sees. `Undo`, `Reset all` and
  `Switch to "Hide checked classes"` are all where they were.

  **An empty strip now leaves the layout** rather than holding a blank line and its padding
  open for the whole session.

- 129 unit tests, up from 118. The new `SimplificationTest` pins each of the above where it
  could regress quietly: that a rule reaching objects only through derived class names is not
  reported as absent, that an `All` composite is not either while it is hiding things, that a
  rule which really does reach nothing is still reported, that the empty rules table and the
  status strip cannot disagree about whether everything is hidden, that the warning never moves
  inside the expander, and that the coverage note stays on the strip.

**Not verified.** Still built and tested on Linux only; nothing here has been exercised on
macOS or Windows. Within Linux, the new header check box has not been clicked in a running
QuPath, and neither the recovered column menu nor the collapsing status strip has been looked
at -- both are pinned by tests over the logic, not by rendering. See *Reporting a problem* in
the [README](README.md).

**One thing that would settle the first bug as a sighting rather than a class of cases.** Both
mechanisms above are fixed and tested either way, but if you saw
`1 rule has no class in this image.` on an image you knew carried that marker, the preset JSON
from that project (one small file under `resources/class-visibility` in the project directory)
or a screenshot of the expanded `Active rules` table says immediately which of the two
mechanisms you were on.

## 0.1.2

A layout fix for something users could see, and one small addition. Nothing about how the
panel behaves has changed -- if you are on 0.1.1, nothing here needs reading before you
upgrade.

- **The preset row no longer collapses into a line of ellipses.** In the docked tab the whole
  row rendered as `...` `[combo]` `...` `...` -- the `Preset:` label and the `Save` and
  `Delete` buttons each reduced to three dots, with `Undock to window` truncated beside them,
  at a panel width that was not remotely tight. Widening the panel did not help, which is the
  tell that this was never about running out of room.

  The cause was one control asking for too much and the rest paying for it. A `ComboBox` takes
  its *preferred* width from its contents, and the preset combo's empty-state prompt was the
  long string `(no presets saved in this project)`. So the row asked for more width than the
  pane had, and an `HBox` settles that by shrinking every child toward its **minimum** -- which,
  for a label or a button, is an ellipsis. The one control that should have given the width up
  was the only one that would not.

  Fixed in the three places that were wrong, in that causal order: controls whose label *is*
  the control -- the preset label and its two buttons, the dock and help buttons, the filter
  row's labels and buttons, the two bulk buttons -- now refuse to be squeezed below their own
  text; the preset combo takes its preferred width from the layout rather than from whatever
  text happens to be in it, while still being the first to give width up when the row really is
  tight; and the empty-state prompt is now the short **`(no presets yet)`**. The explanation it
  used to carry is in the combo's tooltip, where it does not set a layout.

  The audit that found it also gave `Include classes with no objects here` wrapping text -- it
  is a sentence, and pinning it on one line would have set the minimum width of the whole class
  pane.

- **The `Any` / `All` control draws attention to itself the first time it applies.** Below two
  checked components that control does nothing, and says so: its label reads
  `Checked components combine as: (check two or more)`. The moment a second component is
  checked is therefore the only moment at which it can be connected to an effect you just
  caused -- so at that moment the label and its two radios now glow gently for about five
  seconds.

  It is a **slow pulse** -- three swells over five seconds, the same blue halo `Check all
  listed` already uses -- and the rate is the point: at 0.6 Hz it is well under the three
  flashes per second above which blinking content is an accessibility hazard. It is emphasis
  and nothing else; the label beside it states the rule in words, and nothing is encoded in the
  motion or the colour alone. It cannot intercept a click and does not touch focus.

  It fires **once per QuPath session**, in memory only, so a fresh launch teaches it again and
  reopening the panel -- routine here, since opening hides every object -- does not. It stops
  the moment it stops pointing at anything: clicking either radio, the checked count dropping
  back below two, switching image, or the panel leaving the screen.

  **It can be switched off**, in **Extensions > Class Visibility >
  `Highlight the Any / All choice when it first applies`**, which is on by default. Some people
  find motion unpleasant, and JavaFX offers no reduced-motion signal from the operating system
  to honour on their behalf. The setting persists; the once-per-session showing does not.

- 118 unit tests, up from 105: `CombinationHintTest` covers the crossing to two, the session
  latch, the preference gate and the cancel, all without a JavaFX toolkit; two structural
  additions to `SourceDisciplineTest` pin the pulse's stop call sites, require every control in
  a shrinkable row to be either protected or a declared absorber -- so a control added later
  cannot inherit the ellipsis defect -- and pin the preset combo's preferred width.

**Not verified.** Still built and tested on Linux only, and neither change here has been
exercised on macOS or Windows. The layout fix is pinned by structural tests rather than by
rendering, because rendering needs a running toolkit -- see *Reporting a problem* in the
[README](README.md).

## 0.1.1

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
