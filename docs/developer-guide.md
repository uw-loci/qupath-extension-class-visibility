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

JavaFX is on the test classpath, but the tests are not meant to start the JavaFX toolkit --
the three core classes are deliberately JavaFX-free. A test that does need the toolkit will
also need `--add-modules` and the openjfx Gradle plugin, which is exactly the complication
the current split avoids.

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
  Groovy script had.
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
failure rather than narrowing it. The panel's mitigations are the status strip, the guard at
close/shutdown, and the snapshot/restore route.

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

> **[Stub -- Phase 2]** The surface classes changed late in design -- the panel is
> **window-first with optional docking**, not a tab that undocks. This table is the Phase 1
> consolidation and the surface rows will be corrected against the shipped code. See
> `agent-reports/extension-team/class-visibility-panel/02_design.md` for the version this
> was taken from, and treat the shipped source as authoritative where they disagree.

| Class | Package | Role |
|---|---|---|
| `ClassVisibilityExtension` | `qupath.ext.classvisibility` | `QuPathExtension`; the `installed` re-entry guard; the toolbar button and its context menu; the menu item |
| `ClassVisibilityPane` | `.ui` | The entire UI. A `BorderPane`, responsive across a wide and a narrow profile. Owns no window geometry |
| `ClassVisibilityController` | `.core` | Lifecycle: listener install/uninstall, image-follow, update gating, debounce |
| `ClassCensus` | `.core` | Immutable harvest result. Class -> count, component -> (count, class spread). `null` and `PathClass.NULL_CLASS` folded to one `Unclassified` key |
| `ClassHarvester` | `.core` | Off-FX-thread walk via `PathObject.getClassifications()`. Pure, JavaFX-free, unit-testable |
| `VisibilityRuleModel` | `.core` | Exact selections plus the component rule -> minimal delta against `selectedClasses`. Re-entrancy guard. Snapshot/restore. Pure, JavaFX-free, unit-testable |
| `VisibilitySnapshot` | `.core` | An immutable capture of the whole `OverlayOptions` visibility surface (see below) |
| `VisibilityStateStore` | `.core` | Holds the one saved snapshot, and takes the automatic one before the panel's first mutation in a session |
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
4. **Spread is over classes, not objects.** The component list's `Classes` column counts how
   many distinct classes contain the component. Deriving it from object counts is wrong, and
   it is what puts a degenerate component like `positive` back at the top of the list.
5. **The status strip counts set entries, not rows.** A rule with no visible row is still a
   rule.
6. **Scope `All objects` must not use the `synchronized` hierarchy accessors**
   (`getAllObjects(boolean)`, `getFlattenedObjectList(...)`) -- they contend with a running
   classifier for the length of a million-object walk.
7. **Nothing captures `imageData`, `hierarchy` or `server` at construction.** Listeners come
   off in `dispose()`.
8. **`QuPathGUI.getAvailablePathClasses()` is read-only for us.** QuPath syncs it back into
   the project (`QuPathGUI.java:602-611`), so writing it would mutate the user's project class
   list -- which is exactly what "Populate from image" does and what this panel promises not
   to do.
9. **No control calls any setter on a `PathObject`, on the project class list, or
   `resetDetectionClassifications()`.** Worth a grep test in CI.
10. **"Base class" appears in no shipped string.** `PathClass.getBaseClass()` means something
    else entirely (the outermost ancestor), and base-class *matching* semantics were
    explicitly refused upstream. The shipped word is **component**, in labels, tooltips,
    status text and notifications alike.
11. **Any string pointing a user at QuPath's own recovery names the More options button
    beside the show/hide dropdown, positionally.** `PathClassPane` has three such buttons
    (`:165`, `:223`, `:257`); "the More menu" is ambiguous three ways, and this string exists
    to rescue someone whose viewer is already blank.
12. **The near-universal component signal reports; it never advises.** No shipped string
    about spread may contain a verb of advice. Ratios only.

### The snapshot

`Restore visibility state...` restores a snapshot of the *whole* visibility surface, not just
this panel's three properties: `selectedClasses`, `selectedClassVisibilityMode`,
`useExactSelectedClasses`, `showObjectPredicate`, `opacity`, `cellDisplayMode`, and the
per-type booleans `showDetections`, `showAnnotations`, `showTMAGrid`, `showConnections`,
`fillDetections`, `fillAnnotations`, `showTMACoreLabels`, `showGrid`,
`showPixelClassification`. All are on `OverlayOptions`.

One is taken automatically before the panel's first mutation in a session. That automatic
snapshot is what makes this a recovery route rather than a power-user feature, and removing
it would quietly downgrade the feature to the latter.

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

If a scripting entry point is ever added, it belongs in a `*Scripts.java` following the
InstanSeg pattern, and the user guide grows an "Advanced" section -- which is deliberately
absent rather than shipped empty.

</details>

---

<details><summary><strong>Contributing</strong></summary>

## Contributing

- **ASCII only** in code, logs, comments and any internal string. Production runs on Windows
  with cp1252, where a Unicode arrow in a log line is a `UnicodeEncodeError`. Check with
  `grep -rn '[^\x00-\x7F]' src/`. Unicode is fine in this documentation.
- **`qupath.fx.dialogs.Dialogs` only.** `qupath.lib.gui.dialogs.Dialogs` is deprecated.
- **Tests stay JavaFX-free.** The three core classes are pure by design; keep them that way
  rather than adding a JavaFX toolkit initializer to the test suite.
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

- **Not catalog-distributed.** There is deliberately no `.github/workflows/notify-catalog.yml`
  and no catalog entry; distribution will be decided after bench testing. If that decision
  changes, read the catalog section of the monorepo's root `CLAUDE.md` first -- catalog bumps
  must **prepend** the new release and keep every prior entry, or installed users lose their
  update path.
- Tag plus a GitHub release with the shadow jar attached.
- **Do not claim macOS or Windows verification** in the README, the release notes or
  anywhere else until someone has actually run it there.

</details>
