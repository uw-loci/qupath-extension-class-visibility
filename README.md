# QuPath Class Visibility

Show or hide QuPath objects by class, or by one component of a class name, from an undockable
panel in QuPath's analysis pane.

A QuPath 0.7 extension. It is a port of the community Groovy script *"Show specific classes of
objects v3"* ([image.sc topic 31828](https://forum.image.sc/t/31828)), which stopped working when
`OverlayOptions.hiddenClassesProperty()` was removed.

## Do you need this?

**Probably not, if you have a handful of classes.** QuPath's built-in **Classes** pane already
hides and shows classes, and for five or ten classes it is the better tool -- it is already open,
and it colours each row.

This panel is for **highly multiplexed data**: 20-40 or more combinatorial class names such as
`CD3: CD8: PD1`. What it adds over the built-in pane is:

- a **component list**, so you can act on `CD8` once instead of ticking the eleven classes that
  contain it;
- an **`Any` / `All`** switch, so several components can mean "any of these" or "all of these at
  once" -- the second is not expressible in the built-in pane at all;
- **per-class and per-component object counts** for the current image;
- a **`Classes` spread column** (`26/28`), so a component that appears in nearly every class is
  visible as such *before* you click it;
- a **filter** over both lists, and a permanent status line that states, in words, whether
  anything is being hidden and offers one click back.

It writes to the same setting as the built-in Classes pane. The two stay in agreement; nothing
here is a private copy of QuPath's state.

## What it never does

Nothing in this panel changes a classification, the project's class list, or any object data. It
is a viewer for visibility state. There is no "reset classifications" button, and a unit test
fails the build if one of those calls appears in the source.

## Installation

Drag the `qupath-extension-class-visibility-<version>-all.jar` onto QuPath, or copy it into your
QuPath extensions directory and restart.

Not distributed through an extension catalog.

## Using it

**The toolbar button (right of brightness/contrast) opens the panel as a window.** Installing this
extension adds nothing to your analysis pane -- that pane already has five tabs, and you should
not get a sixth just for installing something. **Extensions > Class Visibility > Show panel** does
the same.

If you would rather have it docked, click **Dock as tab** at the top right of the panel, and
**Undock to window** to send it back. Both moves keep everything you have set -- your rules, the
filter you typed, the sort you chose. Once docked, QuPath's own drag-the-tab-out gesture works too.

Right-click the toolbar button for those two moves plus save/restore of the whole visibility
state, a full reset, and help.

The panel starts closed in every session. It does not reopen itself at startup, in either shape.

## If everything has disappeared

Two independent ways back:

1. **`Reset all`** in the panel, or **Reset all visibility** in the toolbar button's right-click
   menu. Both clear every rule, switch back to "Hide checked classes", and turn off "Exact
   matches only" -- the same three things QuPath's own *Restore class visibility to default
   settings* does.
2. **Restore visibility state...** in the toolbar button's right-click menu, which puts back the
   snapshot taken before this panel first changed anything -- including opacity and which object
   types QuPath is showing, not only the class rules.

Without this extension, QuPath's own route is *Restore class visibility to default settings*, in
the menu under the **More button beside the show/hide dropdown** at the top of the Classes pane.
(The Classes pane has three such More buttons; it is the one next to the dropdown.)

## Building

```bash
./gradlew build shadowJar
```

Requires JDK 21 or later. The jar is written to `build/libs/`.

## Licence

Apache-2.0. See `LICENSE`.
