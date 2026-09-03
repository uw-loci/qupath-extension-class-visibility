# QuPath Class Visibility

Show or hide QuPath objects by class, or by one component of a class name, from a floating
panel you can dock into QuPath's analysis pane.

A QuPath 0.7 extension, version 0.2.2. It is a port of the community Groovy script
*"Show specific classes of objects v3"* ([image.sc topic 31828](https://forum.image.sc/t/31828)),
which stopped working when `OverlayOptions.hiddenClassesProperty()` was removed.

Requires **QuPath 0.7.0 or later**.

## Do you need this?

**Probably not, if you have a handful of classes.** QuPath's built-in class list already
hides and shows classes, and for five or ten classes it is the better tool -- it is already
open, it lets you *edit* each class's colour on the spot, and its own filter field accepts
regular expressions, which this panel's does not.

### Where the built-in one is

Open the **Annotations** tab of the analysis pane. The class list is on the right-hand side,
under the heading **Class list**. It gives you:

- **per-class show and hide** -- click the eye icon at the right of a row to toggle that
  class;
- a **colour picker on every row**, for changing what a class looks like in the viewer. This
  panel shows each class's colour but never changes one;
- a **"Show by default" / "Hide by default"** dropdown at the top of the list -- the same
  mode this extension writes to;
- a **filter field** at the bottom, case-insensitive and regex-capable;
- **"Show/hide exact class matches only"**, **"Reset selected classes"** and **"Restore
  class visibility to default settings"**, under the **More options** button -- the ellipsis
  immediately to the right of that dropdown. (The Annotations tab has three such ellipsis
  buttons; the one you want is the one beside the dropdown.)

If that is what you came for, you are done. Do not install this.

### What this panel adds, and all of it is about scale

This panel is for **highly multiplexed data**: twenty to forty or more combinatorial class
names like `CD3: CD8: PD1`, where your class list is not a list of categories but a lattice
of overlapping supersets. Everything the panel adds exists because of that shape:

- a **component list**. `CD8` is one row here. In the built-in class list it is twenty-six
  rows, because twenty-six of your thirty classes contain it -- and you have to find all
  twenty-six, tick all twenty-six, and be sure you missed none. One row instead, and the
  rule follows the marker rather than the spelling of each class name.
- an **`Any` / `All` switch** over those components. `CD3` **and** `CD8` **and** `PD1`
  together is one rule here; in the built-in pane it is a manual hunt for whichever seven of
  your thirty class names happen to carry all three, repeated by hand on the next image.
  Nothing else in QuPath expresses it, although the matching engine underneath supports it.
- a **`Spread` column** -- `26/28` beside `positive` -- because multiplex naming schemes put
  `positive`, `pos`, `neg` or `Cell` in nearly every class name, and on screen those look
  exactly like markers. The number tells you a component is a near-synonym for "everything"
  *before* you click it, and its row tooltip carries the object figures too:
  `In 26 of the 28 classes in this image, and 401,552 of 452,110 objects.` It is off by
  default and one click away, in the column menu button at the right of the component list's
  header; the warning it carries also fires at the moment you click such a row, without it.
- **`Find` over the component list**. The built-in pane's filter is better than ours over
  classes; it has nothing at all to filter components with, because it has no components.
  Whatever you type is shown in **bold** in the rows it matched.
- **Named views, saved in the project.** A filter worth building on a 30-class panel is worth
  building once. `Preset` in the panel header saves the view you have now under a name, into
  the project, so it comes back next week and for whoever else opens that project.

It writes to the same QuPath setting the built-in class list writes to. The two stay in
agreement; nothing here is a private copy of QuPath's state.

### If you are not sure

The class count is not really the test -- the shape of your list is. **Start with the
built-in class list.** Come back when you find yourself ticking the same marker down a dozen
class rows, counting on your fingers whether you got them all, and then doing it again on
the next image. Below that point a second window is not worth the screen space.

## What it never does

Nothing in this panel changes a classification, QuPath's list of available classes, or any
object data. It is a viewer for visibility state, and there is no "reset classifications"
button.

A unit test fails the build if a call such as `setPathClass(` or
`resetDetectionClassifications(` appears anywhere in the source. That is a guard against the
accident, not a proof about behaviour: it matches call text, so a method reference or a
reflective call would slip past it. What it does guarantee is that nobody adds one of those
calls without noticing.

## Installation

**From the extension catalog.** In QuPath, open **Extensions > Manage extensions**, add the
catalog `https://github.com/uw-loci/qupath-catalog-mikenelson` if it is not already listed,
and install *Class Visibility* from it. Installing this way is what lets QuPath tell you when
a new version is out; a hand-installed jar gets no update notifications.

**Or by hand.** Drag the `qupath-extension-class-visibility-<version>-all.jar` onto QuPath, or
copy it into your QuPath extensions directory and restart. QuPath does not load new extensions
on the fly -- restart it either way.

## Using it

**Opening the panel hides every object, and that is deliberate.** Pressing the toolbar button
records the view you had, then switches to `Show only checked classes` with nothing checked --
so nothing is drawn. You check the classes or components you want and they come back, one
population at a time. That is the workflow of the script this extension ports, and it is the
right way round for the data it is for: with thirty overlapping classes painted over each other
there is nothing to see until most of them are gone.

**The panel is a session, so closing it puts your view back.** Opening borrows your view and
closing returns it: the class rules you had when you opened it, the visibility rule,
`Exact matches only`, overlay opacity, the cell display, and which object types QuPath was
showing all come back exactly as they were -- whether you checked anything while you were in
there or not, and however you close it, including by quitting QuPath. Opening the panel to see
what is in an image costs you nothing.

That is why opening it can afford to clear things. **Opening also clears any class rules
already in force**, including rules you set from QuPath's own class list -- an empty checked
set is what "hide everything" means. Those rules come back when you close. While the panel is
still open, *Restore the state from when the panel opened* brings them back without closing it.

You are not left to work out the blank viewer either. The status strip says
`[!] Every object is hidden. "Show only checked classes" is on and nothing is checked.` the
moment it happens; **the check box in the classes list's header carries a blue halo** for as
long as it is true, and one click on it puts every listed class back; and
`Switch to "Hide checked classes"` and `Reset all` sit beside the message.

**The toolbar button opens the panel as a window.** It sits immediately right of the
brightness/contrast button, and it is drawn as an **eye**, slashed whenever classes are being
hidden -- the same vocabulary QuPath's own class list uses for showing and hiding. Installing this extension adds nothing to your
analysis pane -- that pane already has five tabs, and you should not get a sixth just for
installing something. **Extensions > Class Visibility > Show panel** does the same.

If you would rather have it docked, click **Dock as tab** at the top right of the panel, and
**Undock to window** to send it back. Both moves keep everything you have set -- your rules,
the filter you typed, the sort you chose. Moving the panel is not closing it, so nothing is
restored and no work is undone. Once docked, QuPath's own drag-the-tab-out gesture works too.

Right-click the toolbar button for those two moves plus *Restore the state from when the panel
opened*, a full reset, and help; **Extensions > Class Visibility** carries the same items, so
none of it is lost if the toolbar button fails to appear. The button itself closes the panel
when it is already in front; its tooltip says which of open, raise or close the next click
will do, and then how many class rules are in force.

**The button carries two separate facts, one per channel.** Its **pressed** state means the
panel is open, as a toggle button's normally does. The **eye** means something else entirely:
open while nothing is being hidden by class, **slashed** -- with an orange iris -- whenever
something is, including with the panel closed, which is the point of it. It follows rules set
from QuPath's own class list too, not only ones you set here, and it slashes for the
"everything is hidden and nothing is checked" state as well, where there is no rule to see. The
tooltip states both facts in words.

The panel starts closed in every session. It does not reopen itself at startup, in either
shape.

**A filter can still be in force with no panel open.** Class rules are QuPath's state, not the
panel's: closing the panel puts back the rules you had when you opened it, and if that is a
filter, it is still a filter. Rules set from QuPath's own class list, or from a script, are in
force whether this panel has ever been opened or not. So the toolbar button reports them
without being asked: **the eye goes slashed** whenever anything is being hidden by class, and
its tooltip always ends with how many class rules are in force. Hovering that button is the
quickest way to answer "am I looking at everything?", and it works whether the panel is open,
closed, or has never been opened.

## If everything has disappeared

The panel's own blanking is not one of the ways to get here any more: however you close it,
your view comes back. What remains is everything else -- a class rule set from QuPath's own
class list or from a script, an object type switched off in the **View** menu, opacity at
zero, or a session in which this panel was never opened at all.

While the panel is open it tells you: the status strip states in words whether anything is
being hidden, and in the one state where every object is invisible it offers a button that
puts it right in a single click.

There are two ways back, and both are in **two** places -- the toolbar button's right-click
menu and **Extensions > Class Visibility** -- because toolbar insertion is best-effort and a
recovery route with one door is the wrong design. Neither needs the panel open:

1. **`Reset all`** in the panel, or **Reset all visibility** in either menu. Both clear every
   rule, switch back to "Hide checked classes", and turn off "Exact matches only" -- the
   same three things QuPath's own *Restore class visibility to default settings* does. This
   is the one to reach for when the trouble started before this panel did.
2. **Restore the state from when the panel opened**, which puts back the snapshot taken
   automatically every time the panel opens -- including overlay opacity and which object
   types QuPath is showing, not only the class rules. It is now mostly an undo *while the
   panel is open*: closing does the same restore for you. It still earns its place on the
   menus, because it needs the panel open no more than the reset does, and because a close
   that could not put your view back says so and points here.

A **preset** is the third route and a different kind of thing: a view *you* named and saved
into the project, chosen from the `Preset` combo in the panel header. Presets need forethought
and a project; the snapshot above needs neither, which is why both exist.

Without this extension, QuPath's own route is *Restore class visibility to default settings*,
in the menu under the **More options** button beside the show/hide dropdown at the top of the
class list in the **Annotations** tab. (There are three such ellipsis buttons in that tab; it
is the one next to the dropdown.)

## Documentation

- [User guide](docs/user-guide.md) -- the panel control by control, worked examples, and
  what to do when objects disappear.
- [Coming from the Groovy script](docs/migration-from-the-script.md) -- for anyone arriving
  from image.sc topic 31828.
- [Developer guide](docs/developer-guide.md) -- architecture, the `OverlayOptions` contract,
  and house rules for changes.

## Reporting a problem

File an issue on this repository's issue tracker, saying which platform you are on and which
version of QuPath you are running.

**This extension has only ever been run on Linux.** On a real multiplexed slide there, three
behaviours have been checked by hand: a component rule redrawing the viewer, closing the panel
restoring the view it opened onto, and quitting with the panel open leaving everything visible
on relaunch. Everything else rests on unit tests and static checks.

Nothing at all has been run on **macOS or Windows**. The parts most likely to differ are the
toolbar button, which inserts itself into a layout the extension does not control, and window
placement. A report from either platform is more useful than you might think -- it is more
likely to be new than known.

The extension and the script it ports exist because of a long forum thread of people fixing
each other's work; the contributors are credited at the end of
[Coming from the Groovy script](docs/migration-from-the-script.md#credit).

## Building

```bash
./gradlew build shadowJar
```

Requires JDK 21 or later. The jar is written to `build/libs/`.

## Licence

Apache-2.0. See `LICENSE`.
