# Developer Guide

For people building, modifying or reviewing this extension. If you only want to use it,
the [user guide](user-guide.md) is the one you want.

All QuPath line references below were read from the local QuPath 0.7 source tree at
`qupath-qpsc-dev/` (commit `8dfb88fde`). Line numbers move between QuPath revisions; the
method names do not.

---

<details><summary><strong>Building from source</strong></summary>

## Building from source

```
./gradlew shadowJar            # -> build/libs/qupath-extension-class-visibility-<version>-all.jar
cp build/libs/*-all.jar ~/QuPath/v0.7/extensions/
```

QuPath does not load new extensions on the fly -- restart it after dropping in a jar. To
smoke-test from WSL, see `WSL_LAUNCH.md` at the monorepo root.

Three things in `build.gradle.kts` are not obvious and should not be "tidied up":

- **`options.release.set(21)`.** QuPath 0.7 runs on Java 21, so the bytecode target is 21
  whatever JDK you build with.
- **The `TargetJvmVersion` override, forced to 25.** QuPath 0.7.0's Maven artifacts are
  published declaring `org.gradle.jvm.version=25` even though the app runs on Java 21. With
  `release=21`, Gradle resolves a JVM-21-compatible classpath and then rejects those
  artifacts on a clean build. Forcing resolvable configurations to request JVM 25 makes the
  dependencies resolve; the bytecode target is unaffected, so the jar still loads on Java 21.
  This is an upstream metadata problem -- remove the block if it is fixed.
- **`testRuntimeOnly("org.junit.platform:junit-platform-launcher")`.** Required from Gradle 9;
  without it `test` dies with *"Failed to load JUnit Platform"*, which does not name the
  missing dependency.

Shadow stays on **8.3.5**. Nothing here relocates, so there is no reason to move to 9.x --
see the monorepo root `CLAUDE.md`.

JavaFX is on the test classpath, and no test starts the JavaFX **toolkit**. Those are two
different things, and the distinction is the reason the suite runs with no extra flags. The
rule, rather than a roster that goes stale: **a test may touch JavaFX; it must never start the
toolkit.** Three shapes of test exist under it:

- `ClassHarvester`, `ClassCensus`, `VisibilityRuleModel`, `VisibilityPreset` and
  `MatchHighlighter` are JavaFX-free outright -- `qupath-core` types only, or no QuPath at all.
- **`ViewerVisibilityContractTest`** builds a real `OverlayOptions` so it can assert on
  `isHidden(PathObject)`, the predicate the painter actually consults, rather than on a mock.
  That touches `javafx.base` observable collections and properties.
- **`CloseGuardTest`, `OpeningStateTest`, `StartupReconciliationTest` and `SessionRestoreTest`**
  are the ones that surprise people. They call statics -- `ClassVisibilityPane.applyCloseGuard`,
  `ClassVisibilityPane.applyOpeningState`, `ClassVisibilityPane.restoreQuietly`,
  `ClassVisibilityExtension.reconcileStartupVisibility` -- and calling a static method **loads
  the declaring class**, which resolves its superclass, `BorderPane`. So they reach
  `javafx.graphics`, not merely `javafx.base`, without ever mentioning a JavaFX type
  themselves. That is also why those entry points are static: the close guard runs at QuPath
  shutdown with no panel alive, and the startup reconciliation runs before any panel exists.
- **`SourceDisciplineTest`** reads the source files as text and asserts on their shape. That
  is how the one-call-site rule for `restoreOpeningState()` is enforced, and how the
  `setPathClass(` ban is: neither is provable from behaviour without a running QuPath.
  **`CloseMessageTest`** exercises `ClassVisibilityExtension.closeMessage`, a pure static over
  four booleans and an int, so the whole close-notification truth table is testable with no
  panel, no options and no toolkit. Keep that decision a pure static; the moment it reads live
  state, the table stops being checkable.

None of them starts a toolkit. **The line is not which JavaFX modules get loaded -- it is
whether anything is instantiated that needs a live toolkit.** Loading `BorderPane` is fine;
constructing a `Control` is not, and that is what would force `Platform.startup`. The
testability is a side effect of a design constraint, not luck.

**Do not add `--add-modules` on the strength of those bullets.** These are classpath jars,
not modules, and the flag would fail to resolve them. A future test that genuinely needs a live
toolkit needs `--add-modules javafx.base,javafx.graphics,javafx.controls`, the matching
`--add-opens`, *and* the openjfx Gradle plugin so the modules are there to resolve -- which is
the complication the current split exists to avoid. The comment in `build.gradle.kts` carries
the same warning next to the code.

</details>

---

<details><summary><strong>How visibility actually works in QuPath 0.7</strong></summary>

## How visibility actually works in QuPath 0.7

This section is the reason a reader can trust the user guide. Everything the panel does is a
write into three properties on one shared `OverlayOptions`
(`qupath-gui-fx/src/main/java/qupath/lib/gui/viewer/OverlayOptions.java`). **The extension
reimplements none of QuPath's matching.**

### The three properties

| Property | Line | Type |
|---|---|---|
| `selectedClassesProperty()` | `:698` | `ObservableSet<PathClass>` -- the set of rules |
| `selectedClassVisibilityModeProperty()` | `:735` | `HIDE_SELECTED` / `SHOW_SELECTED` |
| `useExactSelectedClassesProperty()` | `:707` | `boolean` |

### The matching rule

`isPathClassHidden(PathClass)` (`:632`) is the whole decision:

```
showByDefault  = (mode == HIDE_SELECTED)
checkContains  = !getUseExactSelectedClasses()
matched        = isSelectedClass(pathClass)
                 || (checkContains && containsSelectedClass(pathClass))
return matched ? showByDefault : !showByDefault
```

- `isSelectedClass` (`:645`) is an identity/equality lookup in the set. It folds `null` and
  `PathClass.NULL_CLASS` together, which is why the panel can show one `Unclassified` row and
  write either sentinel.
- `containsSelectedClass` (`:661`) is set containment over `PathClass.toSet()`
  (`PathClass.java:404`), order-independent, per its own javadoc: *"hiding 'CD3' will hide
  'CD3', 'CD3: CD8', 'CD8: CD3'"*. It is **not** a substring match, which is the bug the old
  Groovy script had. Note it runs in **both** directions and is not restricted to
  single-name selections: a selected `CD3: CD8` matches `CD3: CD8: PD1` and every other
  superset. That is why the classes list is not exact by default, and why the user guide
  documents [what a checked class row acts on](user-guide.md#what-a-checked-class-row-acts-on)
  with its own worked table rather than saying "exactly this class". (`containsSelectedClass`
  only runs its containment test when one of the two classes is derived, so it never widens a
  single-name-against-single-name comparison that `isSelectedClass` has already decided.)
- The set is evaluated as an **OR over its elements**; each element requires *all* of its own
  parts to be present. That is the entire mechanism behind `Any` and `All`:
  - **`Any`** -- N checked components become N entries, each a single-name `PathClass`.
  - **`All`** -- N checked components become **one** composite entry via
    `PathClass.fromCollection(names)` (`PathClass.java:574` / `:595`).

### The interning trap

`fromCollection` builds the derived class **in iterator order** (`PathClass.java:610-617`),
so `["CD3","CD4"]` and `["CD4","CD3"]` produce *different* interned instances, and
`isSelectedClass`'s lookup is identity-sensitive. Feed it a deterministically ordered list and
**keep the returned reference** for removal. Likewise, a class row must write back the
harvested `PathClass` instance, never a reconstruction from its name.

### What persists and what does not

`createSharedInstance()` (`:131`) binds a list of properties to `PathPrefs`. Read it
carefully, because the asymmetry is the source of the panel's central risk:

- **persisted**: `useExactSelectedClasses`, `selectedClassVisibilityMode`, `cellDisplayMode`,
  and the show/fill booleans;
- **not persisted**: `selectedClasses` itself.

So the *interpretation* of the set survives a restart while the set does not. `SHOW_SELECTED`
plus an empty set hides every object in every image, and that state is reachable from stock
QuPath with no extension installed. The method's own javadoc gestures at the intent -- *"a
confused user who has hidden annotations will be relieved to find them back when they
restart"* -- but the mode was included in the persistent list anyway.

**Do not "fix" this by persisting `selectedClasses` in the extension.** That would make this
panel the only thing in QuPath capable of hiding objects across a restart, which widens the
failure rather than narrowing it. The mitigations are the status strip, the toolbar button's
rules tooltip, the guard at panel close **and at quit**, the snapshot/restore route, and -- as
of 0.1.1 -- the restore on close, which means the panel can no longer leave a filter of its own
in force with nothing on screen to own it.

### The opening state, and the three guards around it

`ClassVisibilityPane` ends its constructor with `applyOpeningState()`, which snapshots, clears
solo state and then calls the public static `applyOpeningState(OverlayOptions,
VisibilityRuleModel)`:

```java
model.clearAllRules();
options.setSelectedClassVisibilityMode(ClassVisibilityMode.SHOW_SELECTED);
```

**Rules first, mode second, and the order is load-bearing.** Flipping the mode while old rules
are still in the set shows *only those classes* for a frame -- a view built from rules the user
may not remember setting. Clearing first passes through "everything visible", which is the
state the panel is about to leave anyway.

Two consequences worth holding on to:

- **`clearAllRules()` drops every entry in the set, including entries written from QuPath's own
  class list.** That is what an empty checked set requires, and it is why the snapshot is taken
  *before* it rather than at the first user mutation. The user guide says so plainly; do not
  quietly narrow it.
- **No new preference.** `selectedClassVisibilityMode` and `useExactSelectedClasses` are
  already bound to `PathPrefs` by `createSharedInstance()`. A second persistent store for
  either would be a second source of truth, and the two would fight on every write. The
  opening state is applied to the live options and to nothing else. `useExactSelectedClasses`
  is deliberately **not** reset on open: it is a QuPath-wide setting the user may have set for
  their own reasons, and the status strip already warns when it is on.

**Closing replays the snapshot -- 0.1.1.** `closePanel()` calls
`ClassVisibilityPane.restoreOpeningState()`, which replays the very `VisibilitySnapshot`
instance `applyOpeningState()` stored, and then runs the guard behind it. Three constraints on
that, all pinned by `SourceDisciplineTest.theRestoreIsOnTheClosePathAndNotOnTheReparentingOnes`:

- **`restoreOpeningState()` has exactly one call site, and it is inside `closePanel()`.** Every
  dismissal route funnels there, including QuPath shutdown as of 0.1.1.
- **`dockAsTab` and `undockToWindow` must reach neither the restore nor `closePanel()`.** They
  move a running panel; ending the session on a re-parent would silently discard the user's
  work. `attachToWindow` keeps its `if (!reparenting)` gate on the `WINDOW_HIDDEN` handler for
  the same reason.
- **The store and the panel hold the same snapshot object**, not two captures. `capture` now
  returns what it stored so the on-demand restore and the close replay can never disagree about
  what "before" was.

Listeners are detached before the replay -- `unbind`, `dispose()`, then restore -- because the
replay is a burst of option writes (one uncoalesced overlay-cache clear per rule) and a panel
on its way out has no business rebuilding tables for a view nobody will see.

There are then three checks on the empty-`SHOW_SELECTED` state, and they cover disjoint
routes. Do not merge them:

| Where | What it does |
|---|---|
| `closePanel()` -> restore -> `applyCloseGuard` | every close route goes through it: the Extensions menu, the toolbar toggle, the context menu, `WINDOW_HIDDEN`, tab removal. After a successful restore the guard is usually silent; it still fires when the *snapshot itself* is the empty-`SHOW_SELECTED` pair, which is a state QuPath must not be left in whoever set it |
| the `WINDOW_CLOSE_REQUEST` filter | the quit path, installed at extension load, so it runs in sessions where the panel was never opened. As of 0.1.1 it calls `closePanel()` first when the panel is open, so quitting restores like any other close, and then runs the guard again for the panel-never-opened case |
| `reconcileStartupVisibility` | the crash / force-quit path, called first thing in `installExtension`'s `Platform.runLater`, before any UI exists. It delegates to `applyCloseGuard` so there is one implementation of the rule, and logs the startup-specific line on top |

The reconciliation must leave the mode alone whenever any rule is present. It is a rescue from
a state with no information in it, never a thing that discards someone else's filter.

**The guard's notification is conditional; the guard is not.** Since the panel opens into
exactly the state the guard undoes, an unconditional notification would fire on every ordinary
close -- the extension announcing its own default being tidied up, until the user stopped
reading the one notification that matters. The guard always runs; only the message is gated.
Anything added here inherits that split.

**One close, at most one message, and the decision is a pure static.**
`ClassVisibilityExtension.closeMessage(...)` returns `NONE | RESTORE_FAILED | GUARD |
RULES_ACTIVE` and is tested without a toolkit (`CloseMessageTest`). Two of its rules are
load-bearing and easy to undo by accident:

- **`RESTORE_FAILED` wins over `GUARD`.** "We could not put your view back" is the bigger fact,
  and two notifications for one close is how a user learns to dismiss both.
- **The C1 "rules are still in force" notification is suppressed when the rules in force are
  the ones we just restored** (`VisibilitySnapshot.matchesRules`). Firing it after a successful
  restore would announce the user's own pre-existing rules back at them on every close. It
  still fires when the restore did not land, because then the rules may be ours -- which is the
  case it was built for. **The eye icon is not gated by any of this**: rules set from QuPath's
  own class list are rules, and `rulesAreActive()` reports them either way.

The guard message's own gate moved with the restore. It used to be gated on `hasUserChanges()`,
because without a restore the guard's usual job was tidying up the panel's opening default.
After a successful restore the guard can only be undoing a state the user themselves had, so it
is always worth saying.

**A restore may not throw out of a close.** `ClassVisibilityPane.restoreQuietly` returns
`RestoreOutcome.RESTORED | FAILED | NOTHING_TO_RESTORE` and never propagates; every
notification goes through `notifyQuietly`. Same discipline as the pre-existing shutdown
notification, for the same reason -- a message to the user is a courtesy, refusing them their
quit is not.

**Do not move the shutdown guard off `WINDOW_CLOSE_REQUEST`, and do not make it an event
handler.** It was on `WINDOW_HIDING` until Phase 5, where it never ran once. QuPath 0.7 never
hides its main stage: `QuPathGUI.handleCloseMainStageRequest` is installed with
`stage.setOnCloseRequest`, runs the whole quit sequence inline -- including
`PathPrefs.savePreferences()` -- and then calls `Platform.exit()` and `System.exit(0)` from
inside that handler, so the JVM is gone before any hide event exists. A JVM shutdown hook is
no better: preferences are already written by then. `WINDOW_CLOSE_REQUEST` does fire, and a
**filter** runs in the capturing phase, ahead of the `onCloseRequest` property handler -- both
facts verified with a standalone JavaFX probe rather than reasoned about (finding B1).

The residual: QuPath can consume that event and cancel the quit (unsaved viewers, a running
script, the script editor). As of 0.1.1 the filter has by then closed the panel and replayed
the snapshot, so a cancelled quit leaves a live session with the panel shut and the view
restored -- as though the user had closed it themselves. Accepted, and the alternative was
worse: restoring the view while leaving the panel open puts a panel on screen whose rules have
all been undone underneath it. The state is visible and one button press undoes it. Anything
added to that path inherits the same constraint.

Related dead code worth knowing about so nobody trusts it:
`getAllPathClassesVisible()` (`:545`) returns `selectedClasses.isEmpty()`, i.e. it claims
"all visible" in exactly the `SHOW_SELECTED` + empty state where everything is hidden. It has
zero callers repo-wide.

### Who else writes the set

`PathClassPane` (`qupath-gui-fx/.../panes/PathClassPane.java`) writes the same shared set --
`toggleSelectedClassesVisibility()` (`:491`), the eye icon on each row, and the keyboard
handlers for `Space`/`T`/`S`/`H` (`:313-325`). Its "Restore class visibility to default
settings" (`:423`) does exactly three things -- `restoreClassVisibilityDefaults()`, `:443-448`:

```
setSelectedClassVisibilityMode(HIDE_SELECTED);
setUseExactSelectedClasses(false);
selectedClassesProperty().clear();
```

The panel's `Reset all` mirrors those three, in that order -- not two of them, not a superset.

Two consequences for any change to the extension:

1. **Every write must be a minimal delta.** Compute `toAdd` / `toRemove` and touch only
   those. Never `clear()` then `addAll()`. A blanket rewrite clobbers entries the user set in
   QuPath's own pane, and each element change triggers its own overlay invalidation.
2. **Foreign entries must be rendered, not absorbed.** `Active rules` shows them with
   `Source = Set elsewhere`. Any design that pretends to own the set will eventually delete
   something the user set elsewhere.

</details>

---

<details><summary><strong>Architecture</strong></summary>

## Architecture

**The panel is window-first with optional docking**, not a tab that undocks. That distinction
is the whole reason the surface is split the way it is: `ClassVisibilityPane` is a
self-contained `BorderPane` that does not know which surface it is in, so docking and
undocking are a re-parenting of one live instance rather than a rebuild. Nothing is
serialised across the move, which is why nothing is lost across it.

The three surface states are `CLOSED`, `FLOATING` and `DOCKED`, and the state machine lives
in `ClassVisibilityExtension`. `ClassVisibilityStage` owns window geometry, minimum sizes, the
multi-monitor clamp and `WINDOW_HIDDEN` teardown; the Pane owns none of it and is told what
its dock/undock button should say via `setSurfaceToggle` / `hideSurfaceToggle`.

| Class | Package | Role |
|---|---|---|
| `ClassVisibilityExtension` | `qupath.ext.classvisibility` | `QuPathExtension`; the `installed` re-entry guard; the CLOSED / FLOATING / DOCKED state machine; the toolbar button, its `EyeIcon` and its context menu; the Extensions menu; the shutdown guard |
| `ClassVisibilityStage` | `.ui` | The floating window: geometry persistence, minimum sizes, the clamp onto an existing screen, `Modality.NONE` owned by the main stage, `WINDOW_HIDDEN` teardown |
| `ClassVisibilityPane` | `.ui` | The entire UI. A `BorderPane`, responsive across a wide and a narrow profile. Owns no window geometry |
| `ClassVisibilityController` | `.core` | Lifecycle: listener install/uninstall, image-follow, update gating, debounce |
| `ClassCensus` | `.core` | Immutable harvest result. Class -> count, component -> (count, class spread), plus `matchedObjectsForClass` behind the `Affects` column. `null` and `PathClass.NULL_CLASS` folded to one `Unclassified` key |
| `ClassHarvester` | `.core` | Off-FX-thread walk via `PathObject.getClassifications()`. Pure, JavaFX-free, unit-testable |
| `VisibilityRuleModel` | `.core` | Exact selections plus the component rule -> minimal delta against `selectedClasses`, **and the mode** where an operation implies one (solo). Two single-method interfaces to the outside -- `SelectedClassSet` and `VisibilityModeSwitch` -- so tests supply a `LinkedHashSet` and a lambda. Re-entrancy guard. Snapshot/restore. Pure, JavaFX-free, unit-testable |
| `VisibilitySnapshot` | `.core` | An immutable capture of the whole `OverlayOptions` visibility surface (see below) |
| `VisibilityStateStore` | `.core` | Holds the one snapshot. `capture` **replaces** it and runs when the panel opens; `captureIfAbsent` covers the menu actions that run with the panel closed |
| `VisibilityPreset` | `.core` | One named preset as JSON: class rules and panel checks as **strings**, plus the mode, exact flag, cell display, opacity and the per-type booleans. Versioned, and tolerant of a file written before a field existed |
| `VisibilityPresetStore` | `.core` | The project's own `ResourceManager` at `resources/class-visibility`, the mechanism Brightness & Contrast uses for its settings. Degrades to an empty list with no project; never throws at a click |
| `MatchHighlighter` | `.ui` | Splits a name into matched and unmatched runs for the `Find` bolding. Mirrors `applyFilter`'s predicate exactly, and declines to highlight when case folding changes the string's length. Pure, JavaFX-free |
| `ClassRow` / `ComponentRow` / `RuleRow` | `.ui` | Row view models for the three tables |
| `Strings` | `.ui` | Accessor over `strings.properties`. **Every user-facing string lives in that file**, not in Java source |
| `ClassVisibilityPreferences` | `.preferences` | `PathPrefs` namespace, following `qupath-extension-confusion-matrix`'s `CMPreferences` |

The unit tests belong on `ClassHarvester`, `ClassCensus` and `VisibilityRuleModel` -- the
three classes that are pure by design. Keep `VisibilityRuleModel` behind a one-method set
interface so they can stay free of JavaFX: `OverlayOptions` lives in `qupath-gui-fx` and is
JavaFX properties throughout.

### Invariants

These are not style preferences. Each one is load-bearing for a stated risk, and each has
already been got wrong once somewhere.

1. **Row check-state is derived, never stored.** Compute it from
   `selectedClasses.contains(...)` at render time. That makes image-switch, third-party
   writes and undo correct for free.
2. **A whole delta goes in one FX event**, so repaints coalesce.
3. **Never hand-roll a `PathClass`.** Use the harvested instance, `PathClass.getInstance` or
   `PathClass.fromCollection`, so interning holds.
4. **Spread is over classes, not objects.** The component list's `Spread` column counts how
   many distinct classes contain the component. Deriving it from object counts is wrong, and
   it is what stops a degenerate component like `positive` reading as one.
5. **A count shown beside a control must be the count that control acts on, or be joined by
   one that is.** The class list ships two columns for this reason: `Count` (objects carrying
   exactly this class) and `Affects` (objects a click would move, right now, under the current
   `Exact matches only` setting). `ClassCensus.matchedObjectsForClass` mirrors
   `OverlayOptions.isPathClassHidden` including its `isDerivedClass()` guard; if that
   predicate ever drifts from QuPath's, `Affects` becomes a confident lie, which is worse than
   the single-column state it replaced (finding S1).
6. **The status strip counts set entries, not rows.** A rule with no visible row is still a
   rule.
7. **Scope `All objects` must not use the `synchronized` hierarchy accessors**
   (`getAllObjects(boolean)`, `getFlattenedObjectList(...)`) -- they contend with a running
   classifier for the length of a million-object walk.
8. **Nothing captures `imageData`, `hierarchy` or `server` at construction.** Listeners come
   off in `dispose()`.
9. **`QuPathGUI.getAvailablePathClasses()` is read-only for us.** QuPath syncs it back into
   the project (`QuPathGUI.java:602-611`), so writing it would mutate the user's project class
   list -- which is exactly what "Populate from image" does and what this panel promises not
   to do.
10. **No control calls any setter on a `PathObject`, on the project class list, or
   `resetDetectionClassifications()`.** Worth a grep test in CI.
11. **"Base class" appears in no shipped string.** `PathClass.getBaseClass()` means something
    else entirely (the outermost ancestor), and base-class *matching* semantics were
    explicitly refused upstream. The shipped word is **component**, in labels, tooltips,
    status text and notifications alike.
12. **Any string pointing a user at QuPath's own recovery names the class list in the
    Annotations tab, and the More options button beside the show/hide dropdown,
    positionally.** There is no "Classes pane" in QuPath 0.7 -- the tab is `Annotations`
    (`qupath-gui-strings.properties:84`) and the titled pane inside it is `Class list`
    (`PathClassPane.java:131`) -- and `PathClassPane` has three More buttons (`:165`, `:223`,
    `:257`), so "the More menu" is ambiguous three ways. These strings exist to rescue someone
    whose viewer is already blank; a landmark they cannot find is worse than no landmark.
13. **The near-universal component signal reports; it never advises.** No shipped string
    about spread may contain a verb of advice. Ratios only.
14. **The toolbar button carries two facts on two channels, and they must not be merged.**
    `EyeIcon`'s open/slashed state reads `rulesAreActive()` -- a non-empty rule set **or**
    `SHOW_SELECTED` at all, so the empty "show only" state that hides everything without any
    rule slashes the eye too; the button's pressed state reads `isOpen()`. Note the resulting
    equivalence, worth preserving because it is the pair most likely to drift silently: the eye
    is open exactly when `status.s1`'s condition holds (`count == 0 && !showOnly`,
    `ClassVisibilityPane:1382`) -- `rulesAreActive()` is its exact negation. The equivalence is
    between the two **conditions**; while a harvest is in flight the strip displays
    `status.s6` instead, so the words are not on screen even though the condition holds. Putting rules on the pressed state as well was tried and reverted: the pressed
    state vanishes when the panel closes, which is exactly the moment C1 is about, and a
    toggle button whose pressed state does not mean "this thing is open" is its own defect.
    **The slash carries the rules meaning; the warning-toned iris is a second channel and
    never the only one**, and the tooltip and accessible text state both facts in words so
    nothing is glyph- or colour-only.
15. **Presets store class names, never `PathClass` instances.** `PathClass.toString()` out,
    `PathClass.fromString()` back, and **unclassified is JSON `null`**, not a sentinel word: a
    null cannot collide with a real class name, and `fromString(null)` returns `NULL_CLASS`,
    while round-tripping `NULL_CLASS.toString()` would invent an ordinary class with that
    display name. Storing names is also what makes a preset portable to another project that
    names its classes the same way, which is most of the value in having presets at all.
    Serialization is checked through `GsonTools.getInstance()`, the serializer
    `JsonFileResourceManager` actually uses -- a field that survives only an in-memory round
    trip is still a bug in the product.
16. **A preset's restore writes viewer state first and rules last.** The mode and the exact
    flag change what a given rule set *means*, so writing them after the rules repaints once
    through a combination the preset does not describe.
17. **The everything-hidden signal is never colour-only.** The halo on `Check all listed` is
    one of four channels, and the other three are text: the status strip's `status.s2`, the
    button's `tooltip.button.checkAllListed.allHidden`, and its
    `accessible.checkAllListed.allHidden`. `isEverythingHidden()` and the strip's `status.s2`
    branch read the same two facts, so the halo and the sentence explaining it cannot
    disagree; keep them reading the same predicate.
18. **Solo is one operation, not two.** `VisibilityRuleModel.soloClass` / `soloComponent` set
    the rule contents *and* switch the mode, inside one FX event. Splitting them across layers
    -- the model owning the set, the Pane owning the mode -- leaves a caller who uses only the
    model half hiding **exactly the class it asked to isolate**, and lets a repaint land
    between the two writes showing the inverse for a frame. There is deliberately no
    `VisibilityRuleModel` constructor without a `VisibilityModeSwitch` (finding L1).

### The snapshot, and how it differs from a preset

`Restore the state from when the panel opened` restores a snapshot of the *whole* visibility
surface, not just this panel's three properties: `selectedClasses`,
`selectedClassVisibilityMode`, `useExactSelectedClasses`, `showObjectPredicate`, `opacity`,
`cellDisplayMode`, and the per-type booleans `showDetections`, `showAnnotations`,
`showTMAGrid`, `showConnections`, `fillDetections`, `fillAnnotations`, `showTMACoreLabels`,
`showGrid`, `showPixelClassification`. All are on `OverlayOptions`.

**The snapshot has two readers.** The menu item replays it on demand; `closePanel()` replays
it as the panel goes. `capture` returns the instance it stored precisely so both readers hold
the *same* object -- two independent captures could disagree about what "before" was, and the
disagreement would surface as a close that put the user somewhere they had never been.

**`VisibilityStateStore.capture` replaces; `captureIfAbsent` does not.** The panel calls
`capture` on every open, so the menu item's label is literally true on the second opening as
well as the first -- a user who built a view, closed the panel and reopened it wants their way
back to be *that* view, not to whatever the session started with an hour earlier.
`captureIfAbsent` covers the other route: the menu actions that run with the panel closed and
may be the first thing to touch the surface at all. That automatic capture is what makes this
a recovery route rather than a power-user feature; removing it would quietly downgrade the
feature to the latter.

**`matchesRules(OverlayOptions)` compares rules only** -- the set, the mode and the exact flag.
It answers one question, "are the rules in force the ones we put back", which is the C1
notification's gate. Opacity is restored either way and is not a class rule; widening this
comparison would suppress a notification the user needs on a difference that is not about
hiding anything.

A **preset** is deliberately a different object. It is named, saved in the project, and it
captures `classRules` alongside the panel's own `checkedClasses` / `checkedComponents` /
`combination` -- **the class set is authoritative on restore**, the checks ride along so the
panel can explain itself. It does **not** capture `showObjectPredicate`: that is a
`Predicate<PathObject>`, code rather than data, so a preset leaves whatever predicate it finds
alone rather than pretending to restore one. Which means a preset cannot rescue a viewer
blanked by an object predicate, and the snapshot can. Keep both; the single manual save slot
they replaced is gone, and `VisibilityStateStore.save` with it.

</details>

---

<details><summary><strong>Why there is no scripting API</strong></summary>

## Why there is no scripting API

Everything this panel does is two lines against `OverlayOptions` from a Groovy script:

```groovy
def options = getCurrentViewer().getOverlayOptions()
options.selectedClassesProperty().add(PathClass.fromCollection(["CD3", "CD8"]))
options.setSelectedClassVisibilityMode(OverlayOptions.ClassVisibilityMode.SHOW_SELECTED)
```

A wrapper API around that would add a maintenance surface and a second thing to keep in sync
with QuPath, for no capability a user does not already have. The panel is a UI over an API
that is already public and already scriptable.

The **user guide** carries the working snippet, under
[Doing this from a script](user-guide.md#doing-this-from-a-script), because that is the first
question a scripting user asks and the developer guide is not the book they open. Keep the
two copies in step, or delete this one and link.

If a scripting entry point is ever added anyway, it belongs in a `*Scripts.java` following the
InstanSeg pattern.

</details>

---

<details><summary><strong>Contributing</strong></summary>

## Contributing

- **ASCII only** in code, logs, comments and any internal string. Production runs on Windows
  with cp1252, where a Unicode arrow in a log line is a `UnicodeEncodeError`. Check with
  `grep -rn '[^\x00-\x7F]' src/`. Unicode is fine in this documentation.
- **`qupath.fx.dialogs.Dialogs` only.** `qupath.lib.gui.dialogs.Dialogs` is deprecated.
- **No test starts the JavaFX toolkit.** The three core classes are pure by design; keep them
  that way. Loading JavaFX classes is allowed where it buys real coverage: two tests already do
  it -- `ViewerVisibilityContractTest` (a real `OverlayOptions`, so `javafx.base`) and
  `CloseGuardTest` (a static call that loads `ClassVisibilityPane`, so `javafx.graphics` via
  `BorderPane`). **Instantiating** something that needs a live toolkit is the line, and a
  toolkit initializer in the suite is a different and much larger cost. See
  *Building from source*.
- **`SourceDisciplineTest` is a lint, not a proof.** It greps the main sources for a list of
  object-mutating call texts (`setPathClass(`, `resetDetectionClassifications(` and the rest)
  and fails the build on a hit. It catches the accident, which is the point and the right
  cost. It cannot catch a method reference, a reflective call, or a mutating method nobody
  put on the list. Do not describe it, in a README or anywhere else, as a guarantee about
  behaviour; it is a guarantee that nobody adds one of those calls without noticing.
- **Where a contribution goes.** Issues and pull requests belong on this repository's
  tracker; the README's *Reporting a problem* section is the user-facing version of the same
  thing. If the repository has moved, fix both.
- **Singular and plural are separate complete format strings.** No
  `n + " rule" + (n == 1 ? "" : "s")`. Integers via `NumberFormat.getIntegerInstance()`.
- **Docs and strings move together.** Every user-visible string lives in
  `src/main/resources/qupath/ext/classvisibility/ui/strings.properties` and has a matching
  passage in the user guide. If you change a label there, grep `docs/` for the old text --
  the guide quotes these strings verbatim so that a user searching for what they see on
  screen finds the page about it.

</details>

---

<details><summary><strong>Releasing</strong></summary>

## Releasing

- **Catalog-distributed**, through `uw-loci/qupath-catalog-mikenelson`.
  `.github/workflows/notify-catalog.yml` fires a `repository_dispatch` on
  `release: published`, and the catalog bumps `catalog.json` from it. **Read the catalog
  section of the monorepo's root `CLAUDE.md` before touching any of this** -- in particular,
  a catalog bump must **prepend** the new release and keep every prior entry, or installed
  users lose their update path entirely.
- After the first release, verify the four things that root `CLAUDE.md` lists: the workflow
  ran; the catalog got an `Auto-bump <repo> -> <tag>` commit; the new entry's `main_url`
  returns 200 and matches the published asset name; the entry was prepended with all prior
  entries kept. The org-level `CATALOG_DISPATCH_TOKEN` secret has failed to be reachable from
  a brand-new repo before, so check rather than assume.
- Tag plus a GitHub release with the shadow jar attached. Give the version its release date in
  `CHANGELOG.md` at that point, not before -- a heading stays *unreleased* until the tag and
  the release both exist.
- **Do not claim macOS or Windows verification** in the README, the release notes or
  anywhere else until someone has actually run it there.

</details>
