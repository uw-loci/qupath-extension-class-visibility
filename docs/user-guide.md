# Class Visibility Panel -- User Guide

This guide is written for **highly multiplexed data**: images whose objects carry twenty
or thirty derived classes like `CD3: CD8: CD4: CD45`. If you have five classes, QuPath's
built-in Annotations pane already does what you need -- see
[Do you need this?](../README.md#do-you-need-this) in the README.

Throughout this guide:

| Word | What it means |
|---|---|
| **class** | the whole thing on an object: `CD3: CD8: PD1` |
| **component** | one colon-separated part of a class: `CD3` |
| **rule** | one checked row, as it acts on the viewer |

The panel counts **rules**, not checked rows, and the difference matters. A rule can be in
force with no row to show it -- see [Working across several images](#working-across-several-images).

---

## Look up a message you are seeing

Most of this guide is in collapsible sections, and your browser's find-in-page does not
search inside a collapsed one. This index is deliberately left open, so searching this page
for the message on your screen finds it here.

**Where the panel puts these.** The warning `[!] Every object is hidden`, the note about a
component that is in nearly every class, and the buttons are on the always-visible strip at
the bottom. The routine "what is in force" sentences -- the `[OK]` and `[i]` ones -- are
inside the **`Active rules`** expander just above it, whose title carries the count without
being opened.

| The panel says | What it means |
|---|---|
| `[OK] No class rules active -- nothing is hidden by class.` | No class rule is in force. Note what it does **not** say: if your viewer is still blank, the cause is not a class rule -- see [It might not be a class rule at all](#it-might-not-be-a-class-rule-at-all). |
| `[!] Every object is hidden. "Show only checked classes" is on and nothing is checked.` | **Expected the moment you open the panel** -- that is how it starts, so you can check your way to what you want to see. See [Opening the panel hides everything](#opening-the-panel-hides-everything). If you did not just open the panel, see [If everything disappears](#if-everything-disappears). Either way, the check box at the top of the classes list is haloed in blue and one click on it puts everything back. |
| `[i] 1 rule active -- objects in that class are hidden, in every object type.` | Normal `Hide checked classes` operation. "In every object type" is not decoration -- see [`List` chooses what you see here](#list-chooses-what-you-see-here-not-what-gets-hidden). |
| `[i] 1 rule active -- only objects matching it are shown.` | Normal `Show only checked classes` operation. |
| `[i] Showing only CD3: CD8 and any class containing all of its parts. Everything else is hidden.` | You soloed a class row that has supersets in this image -- see [Show only one class](#show-only-one-class) and [What a checked class row acts on](#what-a-checked-class-row-acts-on). |
| `[i] Showing only classes containing CD8. Everything else is hidden.` | You soloed a component row. |
| `[i] Showing only CD3: CD8. Everything else is hidden.` | You soloed something that reaches nothing beyond itself -- an exact rule, or a class with no supersets in this image. |
| `1 rule matches nothing listed above.` | A rule that reaches none of the objects in the lists as they stand -- usually a rule for a class the current image does not carry. It is still in force -- see [Working across several images](#working-across-several-images). This is inside the `Active rules` expander, with the rest of the routine status. |
| `[i] 2 rules active -- they apply to every image.` | No image is open, and rules are still set. |
| `[!] "Exact matches only" is on. ...` | A QuPath-wide setting is blocking component rules -- see [One QuPath setting can switch the component list off](#one-qupath-setting-can-switch-the-component-list-off). |
| `Counting classes in ... ` | A recount is running. On a very large image, see [Work on a very large image](#work-on-a-very-large-image-without-the-panel-slowing-you-down). |
| `"positive" is in 26 of 28 classes and 401,552 of 452,110 objects.` | You checked a component that is in nearly every class -- see [Components that appear in almost every class](#components-that-appear-in-almost-every-class). |
| `The panel is closed, but 3 class rules are still in force. Objects stay hidden until you clear them.` | Closing the panel could not put your view back, and rules are in force that may be the panel's -- see [Rules can still be in force with no panel open](#rules-can-still-be-in-force-with-no-panel-open-and-the-toolbar-button-says-so). An ordinary close does not show this: it restores your view and says nothing. |
| `Could not put the view back to how it was before the panel opened. ...` | The restore on close failed. Your view is wherever the panel left it -- see [Closing the panel puts your view back](#closing-the-panel-puts-your-view-back). |
| `Switched "Show only checked classes" back to "Hide checked classes" ...` | The guard fired on a setting you made -- see [What the panel does about it](#what-the-panel-does-about-it). |
| `Put the view back to how it was before this panel touched anything.` | `Restore the state from when the panel opened` ran -- see [If everything disappears](#if-everything-disappears). |
| `Saved the preset "T cells" to this project.` / `Deleted the preset "T cells".` | A named view was written into, or removed from, the project -- see [Presets: a view you named](#presets-a-view-you-named). |
| `Matches classes listed above` (in `Active rules`) | The rule has no row of its own in the classes list, but it reaches classes that do -- normal on multiplexed data, and normal for every `All` combination. See [What a checked class row acts on](#what-a-checked-class-row-acts-on) and [One thing to expect in QuPath's Annotations pane](#one-thing-to-expect-in-qupaths-annotations-pane). |
| `Matches nothing listed above` (in `Active rules`) | The rule is in force and reaches nothing here. Most often a rule for a class this image does not carry -- see [Working across several images](#working-across-several-images). |
| `Already set by the component rule below. ...` | A class row is checked and disabled because a component rule already covers it -- see [How the two lists combine](#how-the-two-lists-combine). |
| `No rules are active, and "Show only checked classes" is on, so every object is hidden. Check a class above to see it.` (in `Active rules`) | The empty `Active rules` table, in the state the panel opens in. Expected -- see [Opening the panel hides everything](#opening-the-panel-hides-everything). |
| `No rules are active. Every object is visible.` (in `Active rules`) | The empty `Active rules` table under `Hide checked classes`, where no rules really does mean nothing hidden. If your viewer is still blank, the cause is not a class rule -- see [It might not be a class rule at all](#it-might-not-be-a-class-rule-at-all). |

---

<details open><summary><strong>Getting started</strong> (read this first)</summary>

## Getting started

### Prerequisites

QuPath 0.7.0 or later, with the extension installed and QuPath restarted. Nothing in the
panel needs a project -- it works on a single open image -- but the panel is most useful
inside one.

### What this panel does, in one paragraph

It lists the classes carried by objects in the image you are looking at, and the
components those classes are made of, each with a count. Checking a row adds a rule that
either hides that class or shows only that class, depending on the mode. **Opening the panel
hides everything**, so the ordinary way to use it is to check your way back to the populations
you want -- see [the next section](#opening-the-panel-hides-everything). The panel does
not implement its own matching: it writes into the same QuPath setting that the built-in
Annotations pane writes into, so the panel, that pane and the viewer always agree.

### Opening the panel hides everything

**Press the toolbar button and your objects disappear.** That is the panel working, not a
fault. Opening it does two things in one step: it records the view you had, and then it
switches to `Show only checked classes` with nothing checked -- which means nothing is shown.
You then check the classes or components you want and they come back, one population at a
time.

**And closing the panel puts the recorded view back**, so the blanking lasts exactly as long
as the panel does -- see [the section after this one](#closing-the-panel-puts-your-view-back).

This is the workflow of the Groovy script this extension ports, and it is the right way round
for the data the panel is for: with thirty overlapping classes painted over each other there
is nothing to see until most of them are gone. Starting from everything visible would mean
unticking twenty-eight rows in order to look at two.

Four things make it recoverable rather than alarming:

- the status strip says it in words the moment it happens --
  `[!] Every object is hidden. "Show only checked classes" is on and nothing is checked.`;
- **the check box in the classes list's header carries a blue halo** for exactly as long as
  that is true, and its tooltip opens `Everything is hidden right now. This shows it all
  again`. One click puts every listed class on screen;
- beside the status message are `Switch to "Hide checked classes"` and `Reset all`, either of
  which ends the state;
- **the whole view you had was recorded on the way in**, and
  `Restore the state from when the panel opened` -- on the toolbar button's right-click menu
  and under `Extensions > Class Visibility` -- puts it back. See
  [If everything disappears](#if-everything-disappears).

**Opening the panel also clears any class rules that were already in force**, including rules
you set from QuPath's own class list in the Annotations tab. That is what "hides everything"
requires: an empty checked set. Those rules are in the snapshot, so both the restore above and
closing the panel bring them back.

**If you would rather start from everything.** Switch the mode to `Hide checked classes` as
soon as the panel opens: everything reappears, and a checked row then means "hide this". The
panel does not remember that choice between openings -- it opens hiding everything every time,
and there is no preference to change that.

### Closing the panel puts your view back

**The panel is a session. Closing it undoes everything opening it did.** Whatever the panel
found when it opened is what QuPath is left with when it closes: your class rules, the
visibility rule, `Exact matches only`, overlay opacity, the cell display mode, and every one
of the object-type show and fill toggles. It does not matter what you did in between, or
whether you did anything at all.

| Closed how | Restores? |
|---|---|
| The floating window's own close button | yes |
| Pressing the toolbar button while the panel is in front | yes |
| `Hide panel` on the toolbar button's menu, or on **Extensions > Class Visibility** | yes |
| A docked panel, closed by any of those three -- a docked tab has no close button of its own | yes |
| Quitting QuPath with the panel open | yes |
| **`Dock as tab` and `Undock to window`** | **no, and deliberately** -- see below |

**Moving the panel is not closing it.** Docking and undocking carry the running panel from one
surface to the other: your rules, your `Find` text and your sort order all survive the move,
and nothing is restored. The same is true of QuPath's own *Undock tab* gesture on the docked
tab. If moving the panel reset your work, docking would be a trap; it is not.

Two consequences of the restore worth holding on to:

- **Opening the panel to look costs nothing.** Open it on an image, read the counts, close it,
  and QuPath is where you left it. There is no cleaning up afterwards.
- **A filter you build in the panel does not survive the close.** If you want to keep it, keep
  it deliberately: save it as a [preset](#presets-a-view-you-named), which is named, stored in
  the project and there next week -- or leave the panel open, which is what it is designed for
  while you work.

**An ordinary close is silent**, because there is nothing to report: you are where you were.
Two closes are not ordinary, and both say so:

- **The restore failed** -- something QuPath refused to write. Nothing has been seen to cause
  this, and the panel still tells you rather than leaving you to find out:
  `Could not put the view back to how it was before the panel opened.` It names
  `Restore the state from when the panel opened` as the way to try again.
- **The state you had was itself the blank one** -- `Show only checked classes` with nothing
  checked, set before you ever opened the panel. Restoring that faithfully would hand you back
  an empty viewer and, worse, leave it to greet you at the next launch, so the guard flips the
  mode and says so. See [What the panel does about it](#what-the-panel-does-about-it).

### Where to find it

**Installing the extension changes nothing about your QuPath until you click the button.**
No new tab appears and your layout is untouched.

Two ways in, both reaching the same single panel:

- **The toolbar button**, immediately right of brightness/contrast and drawn as an **eye**.
  It opens the panel as a floating window over QuPath.
- **Extensions > Class Visibility > Show panel**, which does the same thing. Use this if the
  toolbar button did not appear (see [Troubleshooting](#troubleshooting)).

Once the panel is open you can **dock it as a tab** in the analysis pane, alongside Project,
Image, Annotations, Hierarchy and Workflow, if you would rather have it there permanently --
see [Where the panel lives](#where-the-panel-lives-floating-window-and-docked-tab). That is
your choice to make, not something the extension does to you on install.

Pressing the button again closes the panel.

**The button says two things at once, and they are not the same thing.** Its **pressed** state
means the panel is open -- what a toggle button's pressed state normally means. Its **eye** is
about your objects: **open** while nothing is being hidden by class, **slashed**, with an
orange iris, the moment something is.

"Something is" covers both ways it happens: a rule you set, **and** `Show only checked classes`
with nothing checked, which hides everything without any rule existing at all. That second one
is the state this extension was built for, and it is the one where you are least likely to
suspect a setting -- so it slashes the eye too.

Read the two channels separately. A slashed eye on an unpressed button is the combination worth
learning: the panel is closed and objects are being hidden anyway. QuPath's own class list marks
hidden classes with the same open/slashed eye, so it is not a new vocabulary, and the tooltip
states both facts in words -- see
[Rules can still be in force with no panel open](#rules-can-still-be-in-force-with-no-panel-open-and-the-toolbar-button-says-so).

**The panel does not come back after a restart, docked or otherwise.** It starts closed in
every session and always reopens as a floating window, even if you left it docked. There is
no "show at startup" preference: an extension that opens a window at launch is one you have
to close at every launch.

### Reading the panel

Top to bottom, the panel is:

| Zone | What it is |
|---|---|
| `Image:` | The image the rows and counts come from. This is the panel's way of telling you which image it is describing, and it is there in every layout. At the right of the same row: a **`?`** button with a short summary of the panel and what to do if the viewer goes blank, and the `Dock as tab` / `Undock to window` button. |
| `Preset:` | A combo of the views saved in this project, with `Save` and `Delete`. Choosing one applies it immediately. Empty, and disabled apart from the combo, when no project is open. See [Presets: a view you named](#presets-a-view-you-named). **On a wide panel `List:` shares this row**, at its right-hand end. |
| `Visibility rule:` | Two radio buttons, `Hide checked classes` and `Show only checked classes`. The panel opens on the second one -- see [Opening the panel hides everything](#opening-the-panel-hides-everything) and [below](#hide-checked-classes-vs-show-only-checked-classes). **On a wide panel the cell-display note shares this row**, at its right-hand end. |
| `Dense cells? Try View -> Cell Display` | A line of text, and a pointer rather than a control -- it names a menu and stops there. **Cell display is QuPath's own setting and has nothing to do with classes**, but it can make cells look wrong in a way that is easily blamed on this panel, which is the only reason a line about it appears here at all. Hover it for the rest: which of the four options to try, and what changes when you do. On a wide panel it sits at the right-hand end of the `Visibility rule:` row and shortens to `Dense cells? Try...` when there is not room for all of it; on a narrow one it has a line of its own below the radios. See [`Cell display`: how cells are drawn](#cell-display-how-cells-are-drawn). |
| `List:` | Which objects are counted and listed -- `Detections`, `Cells`, `Annotations` or `All objects`. It chooses what you *see in this panel*, never what gets hidden. See [the next section](#list-chooses-what-you-see-here-not-what-gets-hidden). On a wide panel it sits at the end of the `Preset:` row; on a narrow one it has a line of its own, above `Find:`. |
| `Find:` | One filter field over both lists. Case-insensitive, matches anywhere in the name, and the matched text is shown in **bold** in the rows that survive. **On a wide panel `Exact matches only` shares this row**, at its right-hand end. |
| `Exact matches only` | A QuPath-wide setting. While it is on, the whole component half of the panel is greyed out. See [below](#one-qupath-setting-can-switch-the-component-list-off). At the end of the `Find:` row on a wide panel; on its own line below `Find:` on a narrow one. |
| Classes list | Headed `Classes on detections in this image (28)`. One row per class present in the image, with a checkbox, a **colour swatch** and the name, then an **`Affects`** -- how many objects a click on the row would actually hide or show. Sorted by `Affects` descending by default. A **`Count`** column, objects carrying exactly this class, is off by default and one click away in the column menu button; the two are different numbers and the gap is the point -- see [What a checked class row acts on](#what-a-checked-class-row-acts-on). The **checkbox column's header holds a check box of its own**, which checks or unchecks everything currently listed. |
| Components list | Headed `Anything containing these components (17)`, which is what checking a row does. One row per component, with a checkbox, the name and a `Count`. Sorted by name, alphabetically, by default. A **`Spread`** column is off by default, in the same column menu button -- see [The `Spread` column](#the-spread-column-how-many-classes-a-component-covers). |
| `Checked components combine as:` | The `Any` / `All` radios. Only meaningful from two checked components onward. |
| `Active rules` | An expander listing every rule in force, including rules with no row in the lists above. Its title carries the count -- `Active rules (3)` -- without opening it, and the sentence saying what that means in words is inside. See [See every rule in force](#see-every-rule-in-force-including-invisible-ones). |
| Status strip | Always visible, and it holds the things you cannot afford to miss: the `[!] Every object is hidden` warning, the note that fires when you check a component that is in nearly every class, and the buttons -- `Undo`, `Reset all`, `Switch to "Hide checked classes"`. `Undo` is one step deep and always names the step -- `Undo "Check CD8"`, `Undo "Show only CD3: CD8"`, `Undo "Reset all"` -- so it is never a guess. Every action reaches it: single rows, the header check box, a solo, an applied preset, and `Reset all` alike. When there is nothing to say and no button to offer, the strip leaves the layout rather than holding a blank line open. The routine "what is in force" sentence is inside `Active rules`, above it. |

Every control applies immediately. There is no `Apply` button, and nothing here needs
saving.

### `List` chooses what you see here, not what gets hidden

This is the one control in the panel people read confidently and wrongly, so it is worth
sixty seconds before you touch anything else.

`List:` is a **list scope**. It decides which object types the two lists are built from and
what the `Count` column counts. It does **not** narrow the effect of a rule. Hiding is driven
by a single QuPath-wide, type-blind setting: hide `Tumor` and you hide Tumor detections,
Tumor cells and Tumor annotations alike, whatever `List` is set to.

So `List: Annotations`, then check a class that is on both your annotations and your
detections, and **your detections go too**. That is not a bug and there is no setting that
changes it -- a type-aware hiding filter is not currently supported. The panel lists what you
asked for; it hides what QuPath hides.

It is also why `List` defaults to `Detections`. If your classes are on annotations you have
drawn, the classes list will be **empty** when you first open the panel, and it will say so:
`No detections in this image. Change "List" above to see other object types, or run a
classifier first. "List" affects this list only, never what is hidden.` Change `List` and your
rows appear.

### Your first filter -- a 60-second walkthrough

1. Open a multiplexed image and open the panel with the toolbar button. **Your objects
   disappear.** The mode radio sits on **`Show only checked classes`**, nothing is checked,
   and the status strip reads
   `[!] Every object is hidden. "Show only checked classes" is on and nothing is checked.`
   The check box at the top of the classes list is haloed in blue. This is the panel's
   starting state -- see
   [Opening the panel hides everything](#opening-the-panel-hides-everything).
2. In the **Components** list, check one row -- say `CD8`. Every class containing `CD8` is now
   the only thing on screen, and the halo and the warning both go. `Active rules` now reads
   `Active rules (1)`; open it and the sentence inside says
   `[i] 1 rule active -- only objects matching it are shown.`
   (One checked component is **one rule**, however many class names it covers. It says "class"
   because `CD8` is handed to QuPath as a class in its own right, and QuPath matches every
   class containing it -- see
   [What "contains" means, precisely](#what-contains-means-precisely).)
3. Check a second component -- say `PD1`. With the combination on `Any`, which is the default,
   you now see everything CD8-positive **plus** everything PD1-positive. Switch
   `Checked components combine as:` to **`All`** and you see only the double-positives. That
   combination is the thing no other QuPath interface can express.
4. Type `cd` into **`Find`**. Both lists shrink to the names containing it, and the matched
   text renders in **bold** in every row that survived.
5. **Double-click** a class row. The viewer shows only that class, everything else goes, and
   the strip names what you soloed. Double-click the same row again to undo it, or press the
   `Undo` button in the status strip, which says what it will undo:
   `Undo "Show only CD3: CD8"`.
6. **The check box in the classes list's header** brings every listed class back on screen.
   **Closing the panel**
   does something different and usually better: it puts back the view you had *before* step 1,
   whatever that was -- see
   [Closing the panel puts your view back](#closing-the-panel-puts-your-view-back).
7. **`Reset all`** always gets you back to QuPath's own defaults: no rules,
   `Hide checked classes`, `Exact matches only` off. From there a checked row means "hide
   this" instead.

</details>

---

<details><summary><strong>Where the panel lives: floating window and docked tab</strong></summary>

## Where the panel lives: floating window and docked tab

### It opens as a window, and stays one unless you say otherwise

Installing this extension does not add a tab to QuPath's analysis pane. That pane already
carries five tabs, and taking a sixth slot from everyone who installs an extension is rude.
Until you press the toolbar button, nothing about your QuPath is different.

Press it and the panel opens as a **floating window** over QuPath. You can move it, size it,
and put it on a second monitor. With that much width the two lists sit side by side, which
is the layout the panel is happiest in.

Pressing the button again closes the panel -- it is a toggle, and its tooltip always names
what the next click will do:

| Tooltip | The next click will |
|---|---|
| `Open the Class visibility panel. It hides every object to start with, so you can check the classes you want to see; closing it puts your view back.` | open it |
| `Bring the Class visibility panel to the front.` | raise it, because it is open but buried |
| `Close the Class visibility panel and put your view back to how it was before it opened.` | close it |

### Docking, when you want it always there

If you would rather have the panel in the analysis pane than floating over your image,
choose **`Dock as tab`**. The panel moves into the analysis pane and becomes a tab like any
other, next to Annotations and Hierarchy. The same control in reverse moves it back out into
a floating window.

**A move is not a close.** The panel carries on running on the other surface: your rules, your
`Find` text, your sort order and your `Any`/`All` choice all survive both moves, and none of
them triggers the restore that closing the panel does. The same is true of QuPath's own undock
gesture below. You can move the panel as often as you like without losing a thing.

Once it is docked, QuPath's own undock gesture works on it too -- **right-click the tab and
choose "Undock tab"** -- the same gesture that works on QuPath's built-in tabs and on the
measurement table's histogram and scatter tabs. There is no new gesture to learn.

Which to reach for:

- **Floating** is the default, and the roomier one. Reach for it when you are working *on*
  the panel: comparing counts, hunting through 30 classes, setting up an `All` combination.
- **Docked** is the always-visible one. Reach for it when the panel is background furniture
  you glance at while working in the viewer, and you would rather it did not sit over the
  image.

**Where the dock control is.** One button, at the panel's **top right**, on the same row as
the `Image:` label. It reads **`Dock as tab`** while the panel is a window and
**`Undock to window`** while it is a tab -- the same button doing the move in whichever
direction is available. Both moves are also on the toolbar button's right-click menu.

One case where that button is not there: if you used **QuPath's** own undock gesture on the
docked tab, the tab is floating in a window QuPath owns rather than one this extension owns,
so the button hides itself rather than offering a move it cannot make. Drag the tab back into
the analysis pane and it reappears.

### The panel changes shape with its width

There is one panel, and it lays itself out two ways depending on how much width it has:

- **Wide** (roughly 640 px and up, which is the usual floating size): the two lists sit side
  by side, classes on the left and components on the right, with a divider you can drag. The
  header is **three rows**, no control holding a line to itself -- `Preset:` with its combo,
  `Save`, `Delete` and then `List:`; `Visibility rule:` with its two radios and then the
  cell-display note; `Find:` with its field, the clear button and then `Exact matches only`.
  When a row runs short of width the labels and the controls keep their full text and the
  cell-display note is the one thing that shortens, because it is the only occupant you can
  read half of and still act on.
- **Narrow** (roughly 580 px and below, which is the usual docked width): the two lists
  stack vertically, classes above components, again with a draggable divider. **The header
  stacks rather than compressing**: the mode radios go one above the other with the
  cell-display note on a line below them, and `List:`, `Find:` and `Exact matches only` each
  take a line of their own. Nothing is dropped -- the
  panel would sooner be taller than push a control off its right-hand edge. The classes
  header shortens -- `Classes on detections (3 of 28)` rather than
  `Classes on detections in this image (3 of 28)`. Nothing is lost there either: the panel's
  whole subject is the current image, and the `Image:` label one line above says which. The
  components header, `Anything containing these components (2 of 17)`, is the same in both
  layouts.

The two thresholds differ on purpose, so the layout does not flicker back and forth while
you drag a window edge or the analysis pane's divider through the switch point.

No column is dropped as the panel narrows. `Affects` used to be, back when the classes list
carried four columns and long class names had nothing left; with `Count` off by default the
names have that room anyway, and `Affects` is the one number on the row that says what a click
will do. If a class name is still being cut off, widen the panel or drag the divider between
the two lists.

### If you docked it and it seems to have gone

QuPath's entire analysis pane can be collapsed (**View > Show analysis pane**), and a docked
tab inside a collapsed pane is invisible. If the panel disappeared shortly after you docked
it, check that first.

**You do not have to expand it yourself.** Pressing the toolbar button, or choosing
**Extensions > Class Visibility > Show panel**, expands a collapsed analysis pane before
selecting the tab. The same happens when you dock the panel while the pane is collapsed. The
one thing that will not bring it back is expecting the tab to be visible in a pane you
collapsed by hand and did not reopen.

### Which image the panel is showing

The rows and counts always come from the image you are currently looking at, and the
**`Image:`** label at the top of the panel says which. That matters most for a floating
window sitting on a second monitor, where the panel can otherwise quietly be describing a
different image from the one on screen.

Long names are shortened in the middle rather than at the end
(`Tonsil_multiplex_...01.ome.tif`), because names in a project usually share a long prefix
and differ near the end. Hover the label for the full name.

</details>

---

<details><summary><strong>Exact classes vs components</strong></summary>

## Exact classes vs components

### What a class is made of

A QuPath class is either a single name (`CD3`) or several names joined with colons
(`CD3: CD8: PD1`). Composite classifiers produce the second kind, and a multiplex panel
produces a lot of them -- 20 or 30 distinct classes from six or seven markers is normal,
and that is the situation this panel is built for.

Throughout this guide, the whole thing (`CD3: CD8: PD1`) is a **class**, and each
colon-separated part (`CD3`, `CD8`, `PD1`) is a **component**.

### The two lists

- **Classes** -- one row for each whole class actually present in the current image.
  Checking `CD3: CD8` acts on that class **and on every class carrying both of those parts**
  -- `CD3: CD8: PD1`, `CD3: CD8: CD4: CD45`, and so on. See
  [What a checked class row acts on](#what-a-checked-class-row-acts-on), which is the one
  thing in this panel most likely to surprise you.
- **Components** -- one row for each individual name that appears anywhere in those
  classes. Checking `CD3` acts on every class that *contains* `CD3`.

### What a checked class row acts on

**A checked class row is not an exact match by default.** QuPath matches a selected class
against everything that carries all of its parts, so a class row reaches its own class *and
every class that adds markers to it*. On a small project the difference is usually invisible,
because there is nothing above `CD3: CD8` to catch. On a 30-class combinatorial panel there
usually is -- five or six things, which is what "combinatorial" means.

Say your image carries these six classes, and you check the row `CD3: CD8`:

| Class in the image | `Count` (off by default) | Acted on, by default | With `Exact matches only` on | Why |
|---|---|---|---|---|
| `CD3: CD8` | 412 | yes | yes | it is that class |
| `CD8: CD3` | 51 | yes | yes | order is never significant |
| `CD3: CD8: PD1` | 1,204 | **yes** | no | it carries both `CD3` and `CD8`; the extra part does not stop it |
| `CD3: CD8: CD4: CD45` | 880 | **yes** | no | same again |
| `CD3` | 9,310 | no | no | it does not carry `CD8` |
| `CD31: CD8` | 77 | no | no | `CD31` is a different name from `CD3` |

The row you clicked shows `Count 412`. What actually disappears is 2,547.

### `Count` and `Affects`

That gap is why the classes list has **two** number columns, only one of which is on screen
by default:

| Column | What it counts | On screen |
|---|---|---|
| **`Affects`** | objects a click on this row would hide or show **right now** -- 2,547 | yes |
| **`Count`** | objects carrying **exactly** this class -- 412 | off by default |

**`Affects` is the one you get**, because it is the one that answers the question you are
about to act on. `Count` is one click away in the **column menu button** -- the small button
at the right-hand end of the table's header row -- and turning it on is worth doing exactly
when you want to see the gap above for yourself, on your own data. Nothing about either number
changed when `Count` was moved off the default view.

`Affects` follows the current settings rather than describing an idea: turn on
`Exact matches only` and it drops to match `Count`, because that is genuinely what the click
would then do. **It renders in bold whenever it is larger than `Count`** -- the panel saying
*this row reaches further than it looks* -- and that emphasis works whether or not you have the
`Count` column on, which is what makes hiding that column safe. Turning `Count` on is how you
see by how much. When the two are equal `Affects` is plain, and on a small project they will
usually be equal all the way down the list.

`Affects` is the number to trust for a decision and the number to read after a surprise.
`Count` is still the honest answer to "how many cells are `CD3: CD8`", which is the question a
figure legend usually asks -- but take a figure's numbers from a measurement table, which
visibility never touches, rather than from either column here.

(Neither column is dropped when the panel gets narrow. `Affects` used to be, and is not any
more -- it is the number the row is about.)

**`Exact matches only` narrows a class row to the class itself.** That checkbox is QuPath's
own setting rather than the panel's, and it has a second effect you probably do not want as a
side-effect: while it is on, component rules cannot match anything at all. See
[One QuPath setting can switch the component list off](#one-qupath-setting-can-switch-the-component-list-off).
If you need exact class rows *and* component rules in the same session, you cannot have both
at once.

**Soloing a row inherits all of this**, and says so. Showing only `CD3: CD8` shows that class
and its supersets, and the sentence inside `Active rules` reads
`[i] Showing only CD3: CD8 and any class containing all of its parts. Everything else is
hidden.` -- the longer wording appears exactly when the shorter one would be false. If the
class has no supersets in this image, you get the short version instead.

This is QuPath's matching rule, not something the panel invented, and it is a good rule --
"hide CD3" meaning "hide everything CD3-positive" is what people almost always want. It is
just not what the word *exact* would suggest, and on multiplexed data it is not a corner
case.

### What "contains" means, precisely

A component matches if it is one of the parts, in any order. It is **not** a text search.
This is QuPath's own matching rule -- the panel does not implement its own -- so the panel,
QuPath's built-in Annotations pane and the viewer always agree.

Checking the component `CD3`:

| Class | Matched by component `CD3`? | Why |
|---|---|---|
| `CD3` | yes | it is that name |
| `CD3: CD8` | yes | `CD3` is one of the parts |
| `CD8: CD3` | yes | order does not matter |
| `CD3: CD8: CD4: CD45` | yes | extra parts do not matter |
| `CK: CD3` | yes | position in the name does not matter either |
| `CD31` | **no** | `CD31` is a different name; this is not a substring match |
| `CD31: CD8` | **no** | neither part is `CD3` |
| *(unclassified)* | **no** | see below |

The `CD31` row is the one worth remembering, and it is not a corner case in a multiplex
panel -- `CD3` and `CD31`, `PD1` and `PDL1`, `CD4` and `CD45` are all real marker pairs
where one name is a prefix of the other. Checking `PD1` never matches `PDL1`, and checking
`CD4` never matches `CD45`.

The old Groovy script *did* do a substring match, so checking `CD3` there silently swept up
every `CD31` object as well. That is fixed, and it is fixed because QuPath compares whole
names rather than characters.

### The `Spread` column: how many classes a component covers

Each component row can carry a **`Spread`** figure such as `6/28`: how many of this image's
classes contain that component. It is not an object count -- the `Count` column is the object
count.

**`Spread` is off by default**, in the **column menu button** at the right-hand end of the
component list's header row. It is a diagnostic about how discriminating a component is, not
an answer to "what will this click do", and the warning it carries also fires at the moment
you click such a row, in the status strip and in the row's own tooltip -- so you are not
relying on having turned the column on. Turn it on when you are sizing up an unfamiliar
naming scheme and want the whole list ranked at a glance.

Two things about the denominator, because both can shift a ratio:

- It is the number of classes **carried by objects in this image** -- by default, the same
  number the classes list header shows. It does **not** grow when you turn on
  `Include classes not in this image`: a class no object carries is reach a component
  cannot have, and counting it would make every component look less widespread than it is.
  (So with that option on, the header count is the larger of the two, and the ratios stay put.)
- **`Unclassified` is one of the classes it counts**, whenever the image has unclassified
  objects, and no component can ever appear in it. So on such an image every ratio is one
  class short of its ceiling -- `26/28` where `26/27` is the honest maximum. The alternative
  is a denominator that disagrees with the header count one line above, which would be worse;
  it is worth knowing about near the 80% emphasis threshold below, where one class can decide
  whether a row is bold.

**The list is sorted alphabetically by name**, so a marker is where you would look for it.
Spread-ascending was the first choice -- most specific first -- but at twenty or forty
combinatorial classes every real marker sits in the middle of the spread distribution, so
ascending order ranks them arbitrarily and reliably promotes only one-offs and typos. The
bold ratio already warns you about a degenerate component at the moment you are looking at
it, which is where that warning belongs. Every column is sortable if you want a different
order, and on a long list the `Find` field beats any sort.

### Components that appear in almost every class

Multiplex naming schemes usually carry a suffix on nearly every class -- `positive`, `pos`,
`neg`, `negative`, `Cell`. If your classes look like `CD8: positive`, `PD1: positive`,
`CD20: negative`, then `positive` is a component like any other, and it gets a row like any
other.

Checking it is not a filter. It matches almost everything you have. The author of the
original script described it exactly:

> "because almost all of my class names have 'positive' in them, there is one super class
> named 'positive' which has almost no real meaning, but will override almost all classes
> if checked!"

This is not a bug -- not in the script and not here. `positive` genuinely *is* part of
those classes, and the matching rule has no way to know you meant it as a suffix rather
than as a marker.

The panel warns you about it, in three places, none of which stops you. **Hovering the row**
states the scope plainly, whatever columns you have on:

> `In 26 of the 28 classes in this image, and 401,552 of 452,110 objects.`

**Turn the `Spread` column on** and a component present in at least 80% of the image's classes
shows its ratio in **bold**, so the whole list is scannable at once without hovering anything.
(That emphasis is suppressed entirely on images with fewer than five classes, where `3/3`
means nothing.) And after the click, the status strip says the same thing in the same numbers
-- see below.

That is the whole treatment: a ratio, at the point where you are deciding, and again at the
point where you acted. The panel does not tell you not to click. Checking `positive` to get
everything positive is a legitimate thing to want, and it is a fast way to get it.

Two things follow:

- A component present in nearly every class behaves like a "select all" for that list.
- It behaves very differently in the two combination modes. In **`All`** mode a
  near-universal component costs you almost nothing -- `CD8` plus `positive` matches roughly
  what `CD8` matched alone. In **`Any`** mode it swamps everything else: adding `positive`
  to any other component gives you nearly the whole image. This is the single most common
  reason for "I checked one more thing and got *more* cells than before".

When checking such a component does empty most of the viewer, the status strip says so after
the fact as well: `"positive" is in 26 of 28 classes and 401,552 of 452,110 objects.` That note
is on the always-visible strip rather than inside the `Active rules` expander, precisely
because it is a reply to the click you just made -- it clears again on the next one.

### Unclassified objects

Objects with no class get one row of their own, **`Unclassified`**, in the classes list. It
sorts to the bottom whatever else you sort by, because it is a category rather than a class.
It is never matched by a component row -- an object with no class has no components to
match. If you want to hide unclassified objects, check that row.

(Internally QuPath represents "no class" in two different ways depending on how the object
was created. The panel folds both into this one row, so the count you see is the real count.)

### Zero-count classes

By default the classes list shows only classes carried by objects in the current image.
Check **`Include classes not in this image`** to add the rest of the classes QuPath currently
knows about as well. They show a `Count` of zero, render muted, and sort to the bottom --
present and absent are never interleaved.

**Where those extra classes come from is worth knowing, because it is not only your project.**
They are QuPath's list of available classes -- the one in the **Annotations** tab -- and
QuPath keeps that list in two places at once. With a project open it belongs to the project:
opening a project that has its own saved classes replaces the list with them, and every edit
you make is written straight back into the project file. But QuPath *also* saves the list into
your user preferences when it quits and loads it from there at startup, so with no project
open you get whatever the list held last, and **a brand-new project starts out inheriting it**
rather than empty. Classes can therefore follow you from one project into the next, which is
usually convenient and occasionally surprising.

**A zero `Count` does not mean the row does nothing.** Its `Affects` can be large: no object
carries `CD3: CD8` in this image, but a rule for it still reaches `CD3: CD8: PD1` and every
other class here that carries both parts. That is the pair of columns earning their keep --
`Count 0` with `Affects 2,135` is a row worth reading twice before you click it.

Two reasons to turn it on: a rule for a class that is absent from this image is perfectly
legitimate (rules are global -- see
[Working across several images](#working-across-several-images)), and with the option on the
rows stop jumping around as you flip between images. It is off by default because at 28
classes the question is usually "what is in front of me", and padding that with 40 absent
classes answers a different one.

The panel **reads** that class list and never writes to it -- neither into the project nor
into your preferences. QuPath's own "Populate from image" action does write to it; this panel
deliberately has no equivalent.

### One QuPath setting can switch the component list off

**`Exact matches only`**, the checkbox at the end of the `Find:` row -- or on its own line
below it, on a narrow panel -- is bound to QuPath's own setting
**"Show/hide exact class matches only"**, the one under the **More options** button at the
top of the class list in the Annotations tab. It is a saved preference, so it can be
on from a session weeks ago.

While it is on, only whole-class matches count, and **component rules cannot match anything**.
The panel does not let you find that out by trial and error:

- the whole component half of the panel is greyed out and cannot be clicked;
- a warning strip appears directly under the checkbox itself -- `[!] "Exact matches only" is
  on. Component rules match only a class with exactly that name, so they will not find
  derived classes.` -- with a **`Turn off`** button;
- any component rule already in force is listed in `Active rules` with the status
  `Limited by "Exact matches only"`.

Turning it off re-enables the component list, and any component rules already in force start
matching again immediately -- nothing was cleared. The panel never turns the setting off
behind your back: you set it, so you get to unset it.

</details>

---

<details><summary><strong>Combining components: <code>Any</code> and <code>All</code></strong></summary>

## Combining components: `Any` and `All`

This applies to the **component** list only. Class rows are always independent of each
other.

With **no** components checked, or with exactly **one**, the two modes behave identically, so
the radios are greyed out and the label says so:
`Checked components combine as: (check two or more)`. It says it in the label rather than in a
tooltip because JavaFX shows no tooltip on a disabled control -- a greyed pair of radios you
cannot hover for a reason is how a working panel reads as a broken one.

They diverge from two components onward, which is precisely when people start wondering why
they got more (or fewer) cells than they expected.

The control is labelled **`Checked components combine as:`**, and its label spells out the
current choice as you go, e.g. `All -- CD3 and CD8 together`.

**The first time it applies in a session, it glows.** Because the control is inert below two
components, the moment you check a second one is the only moment at which it can be connected
to something you just did -- so at that moment the label and its two radios pulse gently for
about five seconds. It is a slow swell rather than a blink, three cycles over the five
seconds, and it carries no information of its own: the label beside it states the rule in
words, as it always does.

It happens **once per QuPath session** -- a fresh launch teaches it again, closing and
reopening the panel does not -- and it stops early if you click either radio or the checked
count drops back below two. If you would rather it did not happen at all, untick
**`Highlight the Any / All choice when it first applies`** in
**Extensions > Class Visibility**. That setting is on by default and is remembered across
restarts.

### `Any` -- "any of these"

A class matches if it contains **any** of the checked components. This is what the original
Groovy script did, and it is the first-run default here for that reason.

### `All` -- "all of these"

A class matches only if it contains **every** checked component. Extra components are fine.

### Worked example

Take an image whose objects carry these classes:

```
CD3
CD3: CD8
CD3: CD4: CD45
CD3: CD8: CD4: CD45
CD3: CD8: PD1
CK: CD3
CK: PDL1
CD20
CD31
CD68: PDL1
(unclassified)
```

Check the components `CD3` and `CD8`:

| Mode | Classes matched |
|---|---|
| **`Any`** | `CD3`, `CD3: CD8`, `CD3: CD4: CD45`, `CD3: CD8: CD4: CD45`, `CD3: CD8: PD1`, `CK: CD3` -- everything with CD3 in it, plus everything with CD8 in it |
| **`All`** | `CD3: CD8`, `CD3: CD8: CD4: CD45`, `CD3: CD8: PD1` -- only the ones carrying both |

Six classes against three, from the same two checks. Now check `CD8` and `PD1` instead:

| Mode | Classes matched |
|---|---|
| **`Any`** | `CD3: CD8`, `CD3: CD8: CD4: CD45`, `CD3: CD8: PD1` |
| **`All`** | `CD3: CD8: PD1` |

Note that `CK: PDL1` appears in neither. `PDL1` is a different component from `PD1` -- see
[what "contains" means, precisely](#what-contains-means-precisely) -- the same rule that
keeps `CD3` away from `CD31`.

And note what a single component does, in either mode: checking `CD3` on its own matches
`CD3`, `CD3: CD8`, `CD3: CD4: CD45`, `CD3: CD8: CD4: CD45`, `CD3: CD8: PD1` and `CK: CD3`,
but not `CD31`, not `CD20`, and not the unclassified objects.

### The mode matters most for suffix components

If your classes carry `positive` / `negative` suffixes -- `CD8: positive`, `PD1: positive`,
`CD20: negative` -- the two modes diverge dramatically rather than mildly. Checking `CD8`
and `positive`:

| Mode | Result |
|---|---|
| **`Any`** | `CD8: positive` **and** `PD1: positive` -- everything positive, which is nearly the whole image |
| **`All`** | `CD8: positive` only |

This is the case described under
[components that appear in almost every class](#components-that-appear-in-almost-every-class),
and it is the most common source of surprise in either mode.

### Which one you want

- **`Any`** is the "show me this marker" question. *Everything CD8-positive, plus everything
  PD1-positive.*
- **`All`** is the "show me this population" question. *Only the CD8 PD1 double-positives.*
  This is the one no other QuPath interface can express, and on a highly multiplexed image
  it is usually what you actually mean -- the more markers a class carries, the less useful
  "contains any of these" becomes.

### How the two lists combine

Class rows and the component rule combine with OR: an object is matched if its exact class
is checked, **or** if the component rule matches it. Checking class rows never overwrites
your component choices, and checking components never overwrites your class rows. (The old
script could not do this -- see [Coming from the script](migration-from-the-script.md).)

Where the two collide -- your `All` combination is `CD3: CD8` and `CD3: CD8` is also a real
class in the image, or you have checked the component `CD8` and a bare `CD8` class exists --
the class row shows as checked with its checkbox **disabled**, and its tooltip says
`Already set by the component rule below. Change it in the Components list.` That stops an
uncheck in one list quietly destroying a rule you built in the other.

### One thing to expect in QuPath's Annotations pane

In `All` mode the panel expresses your choice to QuPath as a single combined class --
`CD3: CD8` for the example above. That combination usually does not exist in QuPath's list
of available classes, so QuPath's own class list has no row to highlight and will look
as though nothing is selected, even while objects are being hidden.

This is expected. The panel's `Active rules` expander lists the rule and, in its `Status`
column, says whether it is reaching anything: `Matches classes listed above` while it is doing
its job. The `Source` column reads `Components (All)`, so what kind of rule it is has already
been said twice by the time you reach the status. `Reset all` clears it.

(Through 0.1.2 the status column read `Composite -- not in QuPath's class list` instead, and
the status strip simultaneously counted the same rule as having no class in the image. Both
were true statements about set membership and neither was an answer to "is this rule doing
anything" -- see the [0.2.0 entry in the changelog](../CHANGELOG.md#020).)

**`Active rules` does not dress that composite up as a class.** Check `CD8` and `CD45` and the
rule reads **`CD45 + CD8 (all components)`**, not `CD45: CD8` -- the `+` and the suffix are
there so you cannot mistake it for a class name and go hunting your class list for something
that is not in it. (It is written alphabetically because that is what makes the rule stable:
the panel has to find and remove its own composite when you change your mind, and it can only
do that if the same two components always produce the same rule. **The order never affects
what matches**, because matching compares sets of names, not text.)

</details>

---

<details><summary><strong><code>Hide checked classes</code> vs <code>Show only checked classes</code></strong></summary>

## `Hide checked classes` vs `Show only checked classes`

These two modes are QuPath's, not the panel's. The same control is a dropdown at the top of
the class list in the **Annotations** tab, under different wording, and changing it in either
place changes it in both.

| Panel radio | QuPath's dropdown | What checking a row does |
|---|---|---|
| **`Hide checked classes`** | "Show by default" | Everything is visible. Checked rows are **hidden**. |
| **`Show only checked classes`** | "Hide by default" | Everything is hidden. Only checked rows are **shown**. |

The panel spells both readings out as radio buttons rather than hiding one behind a
dropdown, because the difference between "I hid three classes" and "I hid everything except
three classes" should not require a click to see.

`Hide checked classes` is **QuPath's** default: start from everything and take things away.
`Show only checked classes` is **this panel's** default, because it is the mode you want when
you have 28 classes and care about two of them -- which, on a highly multiplexed image, is
most of the time. Soloing a row puts you in it too, from wherever you were.

**The panel sets the mode when it opens, and puts it back when it closes.** It switches to
`Show only checked classes` and clears the checked set, which is what makes the viewer go
blank on the first press -- see
[Opening the panel hides everything](#opening-the-panel-hides-everything). Switch to
`Hide checked classes` at any time and the panel follows you there for as long as the panel is
open; it does not remember the choice for next time, because closing restores the mode you had
before you opened it -- see
[Closing the panel puts your view back](#closing-the-panel-puts-your-view-back).

The mode is **global**. It applies to every viewer, every image, and every project -- there
is one setting for the whole application, not one per image. So the mode the panel sets on
opening is also the mode QuPath's own class list is in, and the dropdown there will read
"Hide by default" while the panel is open.

### The part that catches people out

**The mode is saved and comes back when you restart QuPath. The list of checked classes is
not.**

That asymmetry is worth reading twice, because the two halves of the setting have different
lifetimes:

| | Survives a QuPath restart? |
|---|---|
| `Hide checked classes` / `Show only checked classes` | **yes** |
| `Exact matches only` | **yes** |
| Which classes are checked | **no** -- cleared when QuPath closes |

So if you leave QuPath in `Show only checked classes` and quit, the next launch starts in
that mode with nothing checked -- which means nothing is shown. The next section is about
exactly that.

**With this extension installed, that particular landing is caught at startup.** When QuPath
launches in `Show only checked classes` with nothing checked, the extension puts the mode back
to `Hide checked classes` before any window appears, and writes a line into the log saying it
did. You will not see a blank viewer from a previous session's mode. Closing the panel and
quitting QuPath cover the ordinary routes into that state -- both restore the view the panel
opened onto, and a guard runs behind the restore in case the state being restored is itself
that pair. The startup check covers the one route neither can -- a crash or a force-quit, where
nothing of ours got to run.

</details>

---

<details><summary><strong><code>Cell display</code>: how cells are drawn</strong></summary>

## `Cell display`: how cells are drawn

**This is QuPath's setting, not the panel's.** It lives in QuPath's **`View > Cell display`**
menu and has four options: `Cell boundaries only`, `Nuclei only`, `Nuclei & cell boundaries`,
`Cell centroids only`.

The panel carries one line of text pointing at it -- `Dense cells? Try View -> Cell Display`
-- with everything else on hover. **That line is there because of one specific confusion**,
and it is far too short to say so, which is why it is worth stating here: if your cells have
gone or look wrong, the cause is at least as often the cell display mode as it is a class
rule, and this is the panel you were looking at when you noticed. `Cell centroids only` in
particular turns every cell into a dot, which reads as "my cells are missing" to anyone who
did not set it. A user who does not know the setting exists has no way to go looking for it,
so the panel names the menu and leaves the rest to QuPath.

**The visible line keeps the menu path rather than the option to pick**, and that is the
deliberate half of the trade: a menu path cannot be guessed, whereas an option cannot be
missed once the menu is open -- there are four of them and the one worth trying is on the
list. The hover leads with it. Dense cells are usually easier to read drawn as outlines, so
the option to try is `Cell boundaries only`.

**Transparency is not the same in every option**, which is the part that surprises people.
The standard `Nuclei & cell boundaries` is semi-transparent; `Cell boundaries only` is
completely opaque. So switching does not only change the shape drawn for each cell, it
changes how much of the image underneath comes through -- worth knowing before you decide
the panel did something to your image.

The panel used to carry a second copy of that combo, and it was dropped in 0.2.0: it was the
one control in the panel with nothing to do with classes, and the panel it sat in was too
busy. The pointer that replaced it stated the connection in a full sentence; 0.2.1 cut that
back to the menu name alone, and 0.2.2 made it a question and a path, with the explanation
moved onto the hover where there is room for it. What no version of that line has ever had
room for is the confusion this section opens with -- why a note about cell display sits in a
panel about classes at all.

Two things to know before you reach for it:

- **It applies everywhere in QuPath**, not only to the classes checked in this panel, and not
  only to the current image. It is one application-wide setting, and it is remembered across a
  restart like the other `View` settings.
- **It changes nothing about what is hidden.** It is how cells are *drawn*, not which objects
  are drawn at all. And detections that are not cells -- tiles, spots, anything without a
  nucleus and a boundary -- look the same whichever option is chosen.

**A preset still remembers it.** Saving a preset records the cell display mode along with the
rules and the visibility rule, and applying that preset puts it back -- see
[What a preset carries](#what-a-preset-carries). So does the snapshot taken automatically when
the panel opens, which is why closing the panel restores it. Neither of those changed when the
combo was removed.

</details>

---

<details><summary><strong>If everything disappears</strong></summary>

## If everything disappears

This is the failure this panel is most careful about, and it is worth understanding whether
or not you use the panel, because you can reach it with stock QuPath alone.

### First: did you just open the panel?

**If your objects vanished the moment you pressed the toolbar button, that is the panel's
starting state and not a fault.** It opens on `Show only checked classes` with nothing
checked, so that you can check your way to the populations you want. Click the check box at
the top of the classes list -- the one haloed in blue -- to bring everything back, or read
[Opening the panel hides everything](#opening-the-panel-hides-everything).

The rest of this section is about the other case: a blank viewer you did not ask for.

### The symptom

You open QuPath, open an image, and **no objects are drawn**. No cells, no detections, no
annotations. The image itself is fine. The Annotations and Hierarchy tabs show the objects
are there, with correct counts. The overlay is switched on. Nothing renders.

### The most likely cause

QuPath is in **"Hide by default"** mode (what this panel calls
**`Show only checked classes`**) with **no classes checked**.

That means *hide everything except the checked classes*. With nothing checked, there is no
exception, so everything is hidden. And because the mode survives a restart while the checked
list does not, an ordinary sequence gets you there:

1. Switch to `Show only checked classes` and check a couple of classes to concentrate on
   them.
2. Quit QuPath.
3. Next launch: the mode is still `Show only checked classes`. The checks are gone.
4. Everything in every image is invisible, with nothing on screen explaining why.

There is no error, no warning, and no obvious control to blame -- which is what makes it feel
like a broken install or a corrupted project rather than a setting.

**This extension closes that sequence three times over**: at panel close, at quit, and again
at the next startup if something skipped both -- a crash, or a force-quit. So on a machine
with the extension installed you should not meet it at all. It is described here because you
can still meet it on a machine without the extension, and because the same blank viewer has
several other causes, below.

It is also worth saying what this section is *not* about any more. Since 0.1.1 the panel puts
your whole view back when it closes, so **the panel itself is no longer a way to arrive at a
blank viewer with no panel open**. What is left are the causes that were never the panel's:
a rule set from QuPath's own class list, a rule set by a script, an object type switched off
in the **View** menu, opacity at zero, or a machine without this extension on it at all.

While the panel is open you will not have to guess: the status strip reads

> `[!] Every object is hidden. "Show only checked classes" is on and nothing is checked.`

with a **`Switch to "Hide checked classes"`** button next to it. One click and your objects
are back.

### It might not be a class rule at all

"Everything vanished" has several causes that look identical, and only some are about
classes. Before hunting through class rules, check the obvious ones in **View**: object
types can be hidden wholesale (detections, annotations, the TMA grid, connections), fills can
be off, and overlay opacity can be at zero. A class rule is the *most likely* cause of a
completely blank viewer, but it is not the only one.

If you would rather not work through them one at a time,
**`Restore the state from when the panel opened`** covers all of them at once -- see below. It
is on the toolbar button's right-click menu and under **Extensions > Class Visibility**, and
neither route needs the panel open.

### Two ways back, and they are not the same

**1. `Reset all` -- go to QuPath's defaults.**

The **`Reset all`** button in the panel's status strip does exactly three things, in this
order, which is exactly what QuPath's own reset does:

- switch the mode to `Hide checked classes`;
- turn `Exact matches only` off;
- clear every rule.

This is the sledgehammer. It always reaches a known good state, and it fills the undo slot
first, so `Undo` will bring your rules back if you did not mean it.

**2. `Restore the state from when the panel opened` -- go back to the state you had.**

Right-click the toolbar button (or click its arrow), or open **Extensions > Class
Visibility**, and choose **`Restore the state from when the panel opened`**. This restores a
recorded snapshot rather than resetting to defaults -- so it puts back the state you *had*,
not the state QuPath ships with.

**Its main job is now an undo while the panel is open.** Since 0.1.1 the panel replays that
same snapshot for you when it closes, so you no longer have to remember this item in order to
get your view back after a session. Reach for it when you are still working in the panel and
want to abandon what you have done, or when a close told you it could not put your view back.

**The recording happens by itself, and you never had to ask for it.** The panel takes a
snapshot every time it opens, before it hides anything, and replaces the previous one; with
the panel closed, the menu's own actions take one first if none exists. That is the whole
point of this route: the person who needs a way back is the person who did not plan for one.

The snapshot covers the whole "why can't I see anything" surface, not just this panel's
class rules: the class rules and the mode and `Exact matches only`, but also the show/hide
toggles for detections, annotations, the TMA grid and connections, the fill settings, TMA
core labels, the grid, pixel classification, plus overlay opacity and the cell display mode.
That is why it is worth reaching for when something vanished and you are not sure what you
changed.

**It puts back all of that, which is wider than the problem usually is.** The snapshot is one
moment -- when the panel last opened -- and one slot, not a history and not one per image. So
if you turned detections off and dialled opacity down after opening the panel, restoring to
fix a class rule takes those back too. Nothing is destroyed and everything is re-settable, but
if you know the trouble is a class rule, `Reset all` or unchecking the row is the narrower
tool.

**Which to use:** `Reset all` if you want a clean slate and do not mind losing your setup, and
it is the one to reach for when whatever went wrong started before this panel did.
`Restore the state from when the panel opened` if you want back whatever you had before this
panel touched anything -- including things that were never about classes. Or simply close the
panel, which does the second of those for you.

If nothing has been recorded yet, the menu item says so and greys itself out:
`Restore the state from when the panel opened (nothing recorded yet)`. The panel tells you
when it restores, so it never happens silently.

**A preset is not this.** `Preset` in the panel header saves views *you* name, into the
project, and brings them back weeks later -- see
[Presets: a view you named](#presets-a-view-you-named). The restore above is recorded whether
or not you thought to, which is what makes it a recovery route rather than a feature you have
to have adopted in advance.

### The cure without this extension

The recovery is in stock QuPath, and it works whether or not this extension is installed:

1. Go to the **Annotations** tab of the analysis pane. The class list is on the right-hand
   side.
2. At the top of that list, next to the **"Show by default" / "Hide by default"** dropdown,
   click the **More options** button -- the ellipsis button immediately to the right of the
   dropdown.
3. Choose **"Restore class visibility to default settings"**.

That resets all three things at once: the mode back to "Show by default", exact-match-only
back to off, and the checked list cleared. Your objects come back immediately.

Two warnings about that menu:

- **Do not use "Reset selected classes" for this.** It is the item just above, and it clears
  the checked list *without* changing the mode -- which, in "Hide by default", is precisely
  the state that hides everything. It will make the symptom worse, not better.
- **The Annotations pane has three of those ellipsis buttons** -- one for the class list, one
  for actions on selected objects, and this one. The one you want is the one **beside the
  show/hide dropdown**.

The fastest manual fix, if you would rather not hunt for the menu at all, is simply to set
that dropdown back to **"Show by default"**.

### What the panel does about it

The extension guards the specific transition that creates this state: **if you close the
panel, or quit QuPath**, while the mode is `Show only checked classes` and nothing is checked,
the mode goes back to `Hide checked classes` before anything is saved. An empty "show only"
has no useful meaning, so nothing is lost by refusing to leave you in it. It tells you when it
does this rather than fixing it silently:

> **Class visibility**
> Switched "Show only checked classes" back to "Hide checked classes" because no classes
> were checked. Otherwise every object would have been hidden the next time QuPath starts.

**The guard runs behind the restore, and after 0.1.1 it usually finds nothing to do.** Closing
the panel puts back the view you had before you opened it, and that view was almost never the
empty "show only" pair. The one case where it still fires on an ordinary close is the case it
was written for: you were *already* in `Show only checked classes` with nothing checked before
you opened the panel -- set from QuPath's own class list. Restoring that faithfully would hand
you an empty viewer and, because QuPath saves the mode but not the checked set, leave it
waiting at the next launch. So the guard flips the mode, and because that state was yours and
not the panel's, it says so.

**You are told only when it is undoing something you did.** Before the restore existed, the
panel's own opening state tripped the guard on every close, and a notification each time would
have been the panel announcing its own tidying-up until you stopped reading the one that
matters. The guard always runs; the message appears only when the state being undone is one
you set yourself.

The guard deliberately does **not** fire while the panel is open. Auto-flipping the mode
because you unchecked your last class on the way to checking a different one would move a
control under your hand. While the panel is open, the status strip's warning and its one-click
`Switch to "Hide checked classes"` do the job instead.

**The quit half does not need the panel to be open.** The guard is installed when the
extension loads, not when the panel opens, so it runs at every quit -- including a session in
which you never opened the panel and reached the empty "show only" state from QuPath's own
class list.

**One case where quitting changes things and you do not quit.** QuPath can cancel a quit --
unsaved viewers, a running script, an open script editor -- and by then the panel has already
closed itself and put your view back, guard included. So a cancelled quit leaves you in a
session that is still going, with the panel shut and the view you had before you opened it. It
is visible rather than mysterious, and one press of the toolbar button opens the panel again.
The alternative -- restoring the view but leaving the panel standing -- would leave a panel on
screen whose rules had all been undone underneath it, which is worse.

**And there is a third check, at startup.** A crash or a force-quit runs neither guard, so
QuPath could still come back in `Show only checked classes` with nothing checked -- a blank
viewer, no panel open, and nothing on screen to blame. When the extension loads it looks for
exactly that pair and puts the mode back to `Hide checked classes` before any window exists,
logging the reason:

> `Class visibility: 'show only checked classes' was left set with no checked classes, which
> hides every object. Reset at startup.`

It leaves the mode alone when any class rule is present: a view hiding three of your forty
classes is a filter, and the startup check must never be the thing that discards one.

**What the guards do not cover:** they only ever act on the *empty* "show only" state. A view
hiding three of your forty classes is a filter you built, not a state you fell into, and no
guard will touch it -- at quit, at startup, or behind the restore on close. What *does* touch
it is the restore itself: a filter you built inside the panel goes away when the panel closes,
because the view you had before you opened it comes back in its place. If a filter is worth
keeping, save it as a [preset](#presets-a-view-you-named). See
[Closing the panel puts your view back](#closing-the-panel-puts-your-view-back) and
[Rules can still be in force with no panel open](#rules-can-still-be-in-force-with-no-panel-open-and-the-toolbar-button-says-so).

</details>

---

<details><summary><strong>The two menus</strong></summary>

## Two menus, and every recovery action is in both

The recovery actions live in **two** places, deliberately: the toolbar button's right-click
menu, and **Extensions > Class Visibility**. Toolbar insertion is best-effort -- the panel
does not control QuPath's layout and the button can fail to appear -- and a recovery route
whose only door is the component most likely to be missing is the wrong design. Whichever you
reach for, the items are the same:

| Item | What it does |
|---|---|
| **`Restore the state from when the panel opened`** | Put back the recorded snapshot of your whole visibility surface, without closing the panel. Closing the panel replays the same snapshot, so this is mostly an undo for the session you are in. See [If everything disappears](#if-everything-disappears). |
| **`Reset all visibility`** | The same three-step reset as the panel's `Reset all`: mode to `Hide checked classes`, `Exact matches only` off, every rule cleared. Available without opening the panel. |
| **`Show panel`** / **`Hide panel`** | Open the panel, or close it if it is already in front -- the same three outcomes as the button itself. The label says which it will do. `Hide panel` restores your view, exactly as every other way of closing does. |
| **`Help`** | The same short summary the panel's **`?`** button shows: what the two lists do, what `List` does *not* do, and the ways to get your objects back. This guide is the longer version. |

The first two are reachable **without the panel being open**, which is the whole point: if
your viewer is blank, you should not have to open a panel to fix it -- and opening this one
would hide everything before it helped. `Restore the state from when the panel opened` greys
itself out and renames itself
`Restore the state from when the panel opened (nothing recorded yet)` when there is nothing to
go back to, so it never looks like a route that failed.

There is no `Save visibility state` on these menus. The single manual save slot it used to
offer has been replaced by named presets in the panel header, which are saved in the project
rather than for the session -- see [Presets: a view you named](#presets-a-view-you-named).

**One item is on the Extensions menu only**, and deliberately:
`Highlight the Any / All choice when it first applies`, a tickbox that switches off the
five-second glow described in
[Combining components](#combining-components-any-and-all). It is a preference, not a recovery
action, and the toolbar button's menu is the recovery route -- mixing a setting into it would
dilute what that menu is for. It is not in the panel either, where a permanent tickbox would
be lasting clutter bought for a hint that fires once a session.

**Either gesture opens the toolbar menu.** Right-click anywhere on the button, or left-click
the small triangle at its bottom-right corner. The triangle is a marker rather than a target
-- it is a few pixels across and drawn faintly -- so right-clicking the button is the gesture
to learn, and the Extensions menu is the one to fall back on.

**Hovering the button is worth a mention of its own.** Its tooltip names both what the next
click will do *and* how many class rules are currently in force -- see
[Rules can still be in force with no panel open](#rules-can-still-be-in-force-with-no-panel-open-and-the-toolbar-button-says-so).

</details>

---

<details><summary><strong>Presets: a view you named</strong></summary>

## Presets: a view you named

A **preset** is a visibility setup you saved under a name, into the open project. `Preset:`
starts the second row of the panel's header, under `Image:`: a combo listing what is saved,
with `Save` and `Delete` beside it. On a wide panel `List:` shares the rest of that row.

### Saving one

Set the panel up the way you want it -- the classes and components checked, `Any` or `All`,
the mode, `Exact matches only`, the cell display -- then click **`Save`** and give it a name
you will recognise next month (`T cells`, `Fig 3 panel`, `everything but stroma`). The name
becomes a filename, so one containing characters a filename cannot hold is refused with the
reason before anything is written, and saving over an existing name asks first.

### Using one

**Choosing a preset from the combo applies it immediately.** There is no separate Load button
-- the combo is the load, which is what QuPath's own Brightness & Contrast settings combo
does. If that was not what you meant, the status strip's `Undo` reads
`Undo "Apply preset "T cells""` and one click reverses it.

**`Delete`** removes the chosen preset from the project. It asks first, and it changes nothing
on screen: deleting the preset you are looking at does not put the view back.

### What a preset carries

| Carried | Not carried |
|---|---|
| the class rules in force -- the authoritative half | the `Find` text |
| the show/hide mode, and `Exact matches only` | the sort order, column widths and divider position |
| the `Cell display` mode and overlay opacity -- still carried, though the panel no longer has a control for the first | which image or project you were in |
| the show and fill toggles for detections, annotations, the TMA grid, connections, TMA core labels, the grid and pixel classification | an object predicate set by a script |
| the panel's own checked rows and the `Any` / `All` choice | |

Classes are stored **by name**, so a preset saved in one project applies in another that names
its classes the same way -- which is most of the value in having them. A class the new image
does not carry is still a rule: it appears in `Active rules` as `Matches nothing listed above`,
exactly as it would if you had checked it by hand -- unless it still reaches classes the new
image does carry, which on multiplexed data it usually does.

### Where they live, and who else sees them

In the project: one small JSON file per preset, under `resources/class-visibility` in the
project directory. So a preset survives a restart, travels when the project is copied or
shared, and is there for anyone else who opens that project. It is also the one thing in this
panel that writes anything into your project -- see
[What the panel does not touch](#what-the-panel-does-not-touch).

**No project, no presets.** With no project open the combo reads `(no project open)` and both
buttons are disabled; the combo itself stays clickable so that its tooltip can say why and
name the way out -- `File > Project > Create project`. With a project open and nothing saved
yet, it reads `(no presets yet)`.

### A preset is not the recovery route

A preset needs forethought: you have to have saved one before the moment you need it, and the
person staring at a blank viewer has by definition saved none. That is what
**`Restore the state from when the panel opened`** is for -- recorded automatically, every
time the panel opens, whether or not anyone planned for it. See
[If everything disappears](#if-everything-disappears).

**The forethought runs the other way too.** Because closing the panel restores the view you had
before it opened, a filter you built *inside* the panel does not survive the close. A preset is
how you keep one: name it, and it is in the project next week and on the next image. See
[Closing the panel puts your view back](#closing-the-panel-puts-your-view-back).

There is one thing a preset cannot do that the automatic snapshot can: if a script has set an
object predicate, a preset leaves it exactly where it found it rather than pretending to
restore something it never captured. The snapshot puts that back too.

</details>

---

<details><summary><strong>Common tasks</strong></summary>

## Common tasks

### Show only one class

**Double-click its row.** That leaves exactly that one rule in force and switches the mode to
`Show only checked classes` if you are not already in it, so that row's checkbox becomes the
only checked one in the list and **its name renders in bold**. If you have scrolled away from
it, the sentence inside `Active rules` names what is soloed.

Three gestures do the same thing, and they work on class rows and component rows alike:

| Gesture | Notes |
|---|---|
| **Double-click the row** | The primary one. A double-click on the row's checkbox is left alone -- that is two toggles, and you meant the toggle. |
| **Right-click the row -> `Show only <name>`** | The menu item names the row it will act on, so there is nothing to work out. |
| **The `O` key**, with the row selected | The fastest of the three, and the easiest to press by accident -- see [Keyboard](#keyboard). |

(There is no `Only` button column any more. It cost a column of width in a panel whose class
names -- `FoxP3 (Opal 570): 1+: PDL1 negative` -- were already being cut off to make room for
it, and it lost most of its point once checking a row came to mean "show this".)

On a class row this shows the class **and everything containing all of its parts** -- see
[What a checked class row acts on](#what-a-checked-class-row-acts-on) -- and `Active rules`
says which case you are in rather than making you work it out:

| `Active rules` says | You soloed |
|---|---|
| `[i] Showing only CD3: CD8 and any class containing all of its parts. Everything else is hidden.` | a class row that has supersets in this image, so `CD3: CD8: PD1` is on screen too |
| `[i] Showing only classes containing CD8. Everything else is hidden.` | a component row |
| `[i] Showing only CD3: CD8. Everything else is hidden.` | something that reaches nothing else -- `Exact matches only` is on, or the class has no supersets here |

Three ways to undo it, in increasing scope: repeat the gesture on the same row; press the
status strip's `Undo` button, which is labelled with what it will undo; or `Reset all`.

On a component row it means *show only objects whose class contains this component*.

### Save a view you will want again

Set the panel up the way you want it, then click **`Save`** beside the `Preset:` combo, give
it a name, and it is stored in the project. See
[Presets: a view you named](#presets-a-view-you-named) for what a preset carries and where it
lives.

### Hide one noisy class and keep working

Leave the mode on `Hide checked classes` and check that class's row. Everything else stays
visible. Uncheck it to bring it back.

### Find every class containing a marker

Use the **Components** list rather than the classes list -- that is what it is for. Check
`CD8` and every class containing `CD8` is covered by one rule, in any position and any
order.

### Find a class or a component by name

Type into **`Find`** (`Ctrl+F` from anywhere in the panel, `Cmd+F` on macOS). One field
filters both lists at once, matching anywhere in the name, case-insensitively. **The matched
text is shown in bold** in every row that survived the filter -- typing `cd8` bolds the `CD8`
in `CD3: CD8: PD1`, and every occurrence of it, so you can see why a row is listed. On a
soloed row, which is already bold throughout, the match is underlined instead. The list
headers tell you when a list is filtered -- `Classes on detections in this image (3 of 28)`.

`Escape` clears the filter without leaving the field. The `Find` text is not remembered
between sessions: reopening onto a filtered list you had forgotten about is its own version
of "where did everything go".

### Act on a filtered set of classes

Filter with `Find`, then click **the check box in the header of the classes list's checkbox
column**. It acts on the rows currently listed -- classes the filter is hiding are not touched.

It is three-state: ticked when everything listed is checked, clear when nothing is, and dashed
when some are. Clicking it cycles between checked and unchecked only; the dashed state is
something it reports, never something you have to click through. With nothing listed it is
disabled, so a click on an empty list cannot look ignored. `Undo` names the step either way --
`Undo "Check all listed"`.

If you find yourself checking all 28 classes, flip the mode instead. It says the same thing
in one click.

### See every rule in force, including invisible ones

Expand **`Active rules`**. It has one row per rule, with columns `Rule`, `Source` and
`Status`:

| `Source` | Means |
|---|---|
| `Class` | you checked a class row |
| `Components (Any)` / `Components (All)` | it came from the component list |
| `Set elsewhere` | it was set outside this panel -- QuPath's own Annotations pane writes to the same setting |

| `Status` | Means |
|---|---|
| `Listed above` | it has a row of its own in one of the lists |
| `Matches classes listed above` | it has no row of its own, but it reaches objects that do. Normal on multiplexed data, where a rule for `CD8` reaches every `CD8: ...` class without being one; and normal for every `All` combination, which is never a class name |
| `Matches nothing listed above` | it is in force and reaches nothing here -- most often a rule for a class this image does not carry |
| `Limited by "Exact matches only"` | the QuPath setting is on and this rule cannot match |

The middle two are what a rule **reaches**, not whether its name appears in the classes list.
Through 0.1.2 they were the latter, which on combinatorial class names called a great many
working rules inert -- see the [0.2.0 entry in the changelog](../CHANGELOG.md#020).

Each row has a **`Remove`** button that takes out that one rule, and there is a
**`Clear all rules`** button that removes every rule while leaving the show/hide mode alone.
**Under `Show only checked classes` that empties the viewer**, which is the panel's opening
state and not a fault -- no rules plus "show only" means nothing is shown. The warning and the
haloed check box come back to say so. If what you wanted was everything visible, `Reset all`
is the one to press: it clears the rules *and* puts the mode back.

**You can take the list with you.** Right-click the rules table and choose
**`Copy rules to the clipboard`**, or press `Ctrl+C` with the table focused. You get one
tab-separated line per rule -- rule, source, status -- which pastes into a spreadsheet, a lab
notebook or a figure caption. This is the panel's record of what was in force, and it exists
because rules are deliberately not saved anywhere: if you want to be able to say later which
classes a screenshot was showing, copy them at the time.

### Clear everything and start again

**`Reset all`**, in the status strip. Mode back to `Hide checked classes`, `Exact matches
only` off, every rule cleared -- and the undo slot filled first, so `Undo` brings your rules
back if you did not mean it.

### Work on a very large image without the panel slowing you down

There is nothing to turn off, and that is deliberate. Counting classes is a full walk of the
object hierarchy, and on a slide with millions of objects it is the one thing in this panel
you might feel -- so it is done off the main thread, after a short pause once you stop
changing things, and a recount that has been overtaken by a newer one is dropped rather than
drawn. A spinner appears if a count runs long enough to be worth mentioning, and while one is
in flight the count column header reads **`Count (stale)`** -- if you have that column on, which
by default you do not.

**Hiding and showing are never waiting on a count.** Check a row and the viewer redraws
immediately; only the numbers beside the rows are catching up.

0.1.2 had an `Auto-refresh counts` checkbox for this, and it is gone. Turning it off bought a
little responsiveness at the price of numbers that were silently the previous run's, which is
the failure the `Count (stale)` marker exists to catch in the first place.

**With it left on, a recount after a classifier run announces itself.** The lists dim, a
spinner appears and the status strip reads `Counting classes in <image>...` -- after a short
grace period, so the ordinary fast recount does not flicker at you. That matters most on
exactly the case this panel was built for: a classifier finishing over a multiplexed slide is
the longest recount there is, and it used to run in total silence behind stale numbers.

### Keyboard

| Key | Where | Action |
|---|---|---|
| `Ctrl+F` / `Cmd+F` | anywhere in the panel | focus `Find` |
| `Escape` | in `Find` | clear the filter, keep focus |
| `Up` / `Down` | in a list | move between rows |
| `Space` | in a list | check or uncheck the focused row |
| `O` | in a list | show only the focused row, hiding everything else |
| Double-click | on a row | the same as `O`, on the row you clicked |
| `Ctrl+C` / `Cmd+C` | in the `Active rules` table | copy every rule to the clipboard |

`Escape` does not close the panel. It is a pane, not a dialog.

**`Ctrl+Z` is deliberately not on this list.** Undo lives on the status strip's **`Undo`**
button and nowhere else. The panel used to take the accelerator, which meant that deleting an
annotation, clicking into the panel and pressing `Ctrl+Z` undid something in the panel while
your deletion stood -- with nothing on screen saying so. `Ctrl+Z` now reaches QuPath, as it
should; the button is one click and, unlike a key, it tells you what it is about to undo.

**`O` is not a small key.** Unmodified, with a row focused, it leaves that row as the only
rule and switches the global mode to `Show only checked classes`, so everything else
disappears. Press it by accident and most of your image goes; `Undo`, or a second `O` on the
same row, puts it back. It also means the table's built-in type-to-search cannot reach a row
whose name begins with `O`. If that ever costs you more than it saves, the row's right-click
menu and a double-click reach the same action.

</details>

---

<details><summary><strong>Working across several images</strong></summary>

## Working across several images

The panel is meant to stay open while you move between images. Switch image and the rows
re-populate from the new image's objects; you do not close and reopen it. While the new
image is being counted, the lists dim and the status strip reads
`Counting classes in <image>...`.

**What carries over:** your rules, and the mode. Both are properties of QuPath, not of the
image, so they follow you from image to image. That is usually what you want in a project
where every slide carries the same panel of markers. Your `Find` text, sort order, `List`
scope and `Any`/`All` choice carry over too -- you are following one thread of investigation
across images.

**What changes:** the rows themselves and their counts, which are harvested from the image
you are looking at, and the `Image:` label that says which image that is.

**A class checked in one image but absent from the next** stays checked and stays in force --
it simply has no row to show it in, because nothing in this image carries that class.

Whether the panel calls that out depends on what the rule still *reaches*. If nothing in the
new image matches it, `Active rules` shows `Matches nothing listed above` and the status
sentence inside that expander appends `1 rule matches nothing listed above.` But on a
multiplexed panel a rule for `CD8` usually still reaches plenty -- every `CD8: ...` class the
new image does carry -- and then it reads `Matches classes listed above` and nothing is
counted, because nothing is wrong.

This is why the panel counts **rules** rather than checked rows: the two numbers can
legitimately differ, and a rule you cannot see is still a rule. A row-counting indicator
would cheerfully report `0 rules active` while objects were being hidden -- which is the
exact failure this panel exists to prevent.

If you would rather the rows stayed put as you flip between images, turn on
**`Include classes not in this image`**.

**Presets follow the project, not the image.** The same list is offered on every image in the
project, and applying one on a different image applies the same rules -- which is the point,
on a project where every slide carries the same panel of markers. Rules the new image has
nothing for appear in `Active rules` as `Matches nothing listed above`.

**With no image open** the lists show `No image open. Open an image to list the classes it
uses.`, and any rules you have set stay in force -- `Active rules` says
`[i] 2 rules active -- they apply to every image.`

</details>

---

<details><summary><strong>What the panel does not touch</strong></summary>

## What the panel does not touch

The panel changes **what you see**. It never changes **what you have**.

Specifically, nothing in this panel will:

- change any object's class;
- add to, remove from, or reorder QuPath's list of available classes;
- delete, merge, or move objects;
- modify measurements or any other object data.

**One thing it does write into your project, and only when you ask it to:** a saved preset,
as a small JSON file under `resources/class-visibility` in the project directory. Nothing
else in the panel writes anything anywhere, and a preset touches no object and no class list
-- see [Presets: a view you named](#presets-a-view-you-named).

Two consequences worth knowing:

- **Nothing here needs saving, and nothing here can be lost.** Close the panel, close the
  image without saving -- your classifications are untouched, because they were never
  touched. (Presets are the exception in the other direction: they are saved the moment you
  click `Save`, and they stay saved.)
- **The class list is read-only.** QuPath's own Annotations pane has a "Populate from image"
  action that *adds* every class found in the image to QuPath's available classes -- which,
  with a project open, means writing them into the project file. This panel deliberately does
  not do that. It shows you what is in the image without changing what QuPath, or your
  project, knows about.

If you are coming from the tabbed variant of the old Groovy script: that version had a
**"Reset classifications"** button sitting a few pixels below the visibility checkboxes,
which called `resetDetectionClassifications()` and **destroyed your classifier output**.
There is deliberately no such button here, and no control in this panel uses "classify" as a
verb.

### Rules can still be in force with no panel open, and the toolbar button says so

Class rules belong to QuPath, not to this panel. Closing the panel puts back the rules you had
when you opened it -- and **if what you had was a filter, a filter is what you get back**.
Rules set from QuPath's own class list, or from a script, are in force whether this panel has
ever been opened or not, and a machine without this extension has nothing at all to tell you
about them.

It matters because a *blank* viewer announces itself -- nobody mistakes it for data -- while a
viewer showing thirty-seven of your forty classes looks completely normal, and that is the one
somebody screenshots. So the panel tells you without being asked:

- **The toolbar button's eye goes slashed**, and its iris turns orange, whenever anything is
  being hidden by class -- with the panel open, closed, or never opened. This is the signal you
  do not have to do anything to see, and it is deliberately on the icon rather than on the
  button's pressed state: the pressed state disappears when you close the panel, which is
  precisely when you most need telling. The slash carries the meaning; the colour is a second
  channel, not the only one.
  **The eye is open exactly when the panel's status strip would read
  `[OK] No class rules active -- nothing is hidden by class.`** -- the two are the same
  question, one asked with the panel open and one with it closed. (During a recount the strip
  shows `Counting classes in ...` instead of either message; the eye still tells you the
  truth.)
- **Its tooltip says the same thing in words.** Hover it at any time and, after the sentence
  about what a click will do, it tells you what is in force: `No class rules are in force.` / `3 class rules are
  in force, so some objects are hidden.` / `Every object is hidden: "Show only checked
  classes" is on and nothing is checked.` It follows rules written from QuPath's own class
  list too, not only ones you set here. **That hover is the quickest way to answer "am I
  looking at everything?"**

One indicator it cannot give you:

- **QuPath's own class list marks hidden classes** with an eye-slash icon and italic text in
  the Annotations tab, and that is the out-of-panel indicator to use -- **but it can only
  mark a class that is in QuPath's list of available classes.** A class that exists on
  your objects and not in that list has no row to carry the mark. On a classifier's output in
  a project where nobody ran "Populate from image", that is the common case rather than the
  edge case. This panel deliberately does not add classes to that list to fix it;
  writing into your class list to improve a status icon is a worse trade.

There is also a notification, `The panel is closed, but 3 class rules are still in force.
Objects stay hidden until you clear them.` -- but you should not normally see it. It fires only
when closing the panel could not put your view back, so the rules left in force may be the
panel's own. An ordinary close restores your view and says nothing.

If you are about to hand the screen, a screenshot or an image export to anyone else, **glance
at the toolbar button: a slashed eye means a filter is on.** Hover it for the count, and either
note what is in force or clear it with **Extensions > Class Visibility > Reset all
visibility**, which does not need the panel open.

### Screenshots and rendered exports carry the filter; measurements do not

A hidden class is hidden in anything painted from the viewer. That includes a viewer snapshot
and **File > Export images... > Rendered RGB (with overlays)...**, which copies the viewer's
overlay layers and therefore paints through the same filter. Neither output records anywhere that a filter
was in force -- a `.png` of a filtered multiplex field looks exactly like a `.png` of an
unfiltered one.

None of this is new with this extension; QuPath's class visibility has always worked this
way and the same image comes out of the built-in class list. What changes is how easy it now
is to have twenty classes hidden and not be holding that in your head. Clear or note the
filter before capturing an image for a figure, a slide or a collaborator.

**Measurements are not filtered**, with one exception worth knowing:

- `Measure > Export measurements`, the batch export, has no connection to visibility at all.
  It exports every object.
- QuPath's **measurement table** has an **`Apply class visibility`** toggle in its own menu
  (accelerator `Shortcut+Shift+V`, which is one key away from paste-as-plain-text). It is
  **off by default**, but it is a saved preference, so once someone turns it on it stays on
  for that machine account. While it is on, the table shows only visible objects -- and its
  Copy and Save buttons write only those rows, while the workflow step QuPath records for
  that save is a plain `saveDetectionMeasurements(...)` with no note of the filter. Re-run
  the logged script and you get a larger file than the one you saved. If a measurement export
  has to match a filtered view, say so alongside the file; if it has to be complete, check
  that toggle is off.

### What the panel does not do

- **`List` does not limit hiding.** The `List` control chooses which objects are counted and
  listed. Hiding is driven by one global, type-blind setting: hiding `Tumor` hides Tumor
  detections, Tumor cells and Tumor annotations alike, whatever `List` is set to. No control
  in this panel can change that, and a type-aware hiding filter is **not currently
  supported**.
- **Exclusions.** "Show every CD8-positive class, but nothing with PDL1" is **not currently
  supported**.
- **Per-component opacity.** Fading a group of classes rather than hiding it outright is
  **not currently supported**.
- **Configurable separators.** Classes named `CD8_positive` rather than `CD8: positive` have
  one component, not two. QuPath's matching engine splits on `:` only, so other separators
  are **not currently supported**.

</details>

---

<details><summary><strong>Settings and what persists</strong></summary>

## Settings and what persists

Some of what you see in this panel belongs to QuPath and some belongs to the panel. The
difference decides what comes back after a restart.

| Setting | Owned by | Survives restart | Scope |
|---|---|---|---|
| `Hide checked classes` / `Show only checked classes` | QuPath | **yes** | global, but the panel sets it to `Show only checked classes` every time it opens |
| `Exact matches only` | QuPath | **yes** | global |
| `Cell display` | QuPath | **yes** | global -- set it in QuPath's `View` menu; the panel only points at it |
| Which classes are checked (your rules) | QuPath | **no** -- cleared when the panel opens, and put back when it closes | global for the session |
| Saved presets | the panel | **yes**, in the project | one set per project, shared with anyone who opens it |
| `Any` / `All` | the panel | yes | the panel |
| `Highlight the Any / All choice when it first applies` | the panel | yes | one machine, every project |
| `List` scope | the panel | yes | the panel |
| Divider position between the two lists | the panel | yes | separately for the wide and narrow layouts |
| `Active rules` expanded or collapsed | the panel | yes | the panel |
| `Include classes not in this image` | the panel | yes | the panel |
| Sort column and direction, per list | the panel | **no** | survives docking and undocking within a session, but not a restart |
| `Find` text | -- | **no**, deliberately | -- |
| The panel window's position and size | the panel | **yes** | one window, whichever image or project |
| Whether the panel was docked or floating | the panel | **no** -- it always reopens as a window | -- |
| Whether the panel was open at all | -- | **no** -- it always starts closed | -- |

One row is missing from that table on purpose, because it is not a setting: **the snapshot the
panel takes when it opens**. It lives for as long as QuPath does, is replaced on every opening,
and is replayed when the panel closes -- see
[Closing the panel puts your view back](#closing-the-panel-puts-your-view-back). Nothing about
it survives a restart, and nothing about it is written to disk.

**The `Any` / `All` glow keeps its setting but not its memory.** Whether the hint is switched
on is a saved preference, in that table. Whether it has already fired is not: that is held for
as long as QuPath runs and no longer, so the next launch shows it once more.

There is one more, with no control anywhere in the panel: the **80% threshold** at which a
component's spread ratio is shown in bold, when the `Spread` column is on, is a saved
preference (`classvisibility.coverageThreshold`). Nothing in the interface changes it. If two
machines ever show you different rows in bold, that setting having been retuned on one of them
is the reason.

Two of these are deliberate omissions rather than oversights:

- **The `Find` text is not remembered.** Reopening onto a filtered list that hides most of
  your classes, with no memory of having typed the filter, is a small self-inflicted version
  of the bug this panel exists to prevent.
- **Your rules are not remembered across a restart, and the panel does not add that.**
  QuPath deliberately does not persist them. Persisting them here would make this panel the
  only thing in QuPath capable of hiding objects across a restart -- which widens the "why is
  everything invisible" problem rather than narrowing it.

</details>

---

<details><summary><strong>Troubleshooting</strong></summary>

## Troubleshooting

**I checked a component row and nothing happened.**
`Exact matches only` is on. The component half of the panel will be greyed out, with a
warning strip and a `Turn off` button. See
[One QuPath setting can switch the component list off](#one-qupath-setting-can-switch-the-component-list-off).

**Everything vanished when I opened the panel.**
That is how it opens: `Show only checked classes` with nothing checked, so that you check your
way to what you want to see. Click the check box at the top of the classes list -- the one
haloed in blue -- to put
every listed class on screen, or close the panel, which puts back the view you had before you
opened it. See [Opening the panel hides everything](#opening-the-panel-hides-everything).

**I had classes hidden, and opening the panel cleared them.**
Opening clears every class rule, including rules set from QuPath's own class list, because
"hide everything" means an empty checked set. **Close the panel and they come back.** If you
would rather have them back without closing it, right-click the toolbar button and choose
`Restore the state from when the panel opened`.

**I set up a filter in the panel and closing it threw my filter away.**
That is the panel working as designed: closing restores the view you had before you opened it,
so anything you built inside the session goes with it. Save the ones worth keeping as a
[preset](#presets-a-view-you-named), or leave the panel open -- docked, if it is in the way --
while you work. See
[Closing the panel puts your view back](#closing-the-panel-puts-your-view-back).

**"Could not put the view back to how it was before the panel opened."**
The restore on close failed, so your view is wherever the panel left it rather than where you
started. Right-click the toolbar button and choose `Restore the state from when the panel
opened` to try again; `Reset all visibility` on the same menu is the fallback. This has not
been seen to happen -- if it does, it is worth an issue.

**Everything is invisible and I did not do anything.**
The usual cause is `Show only checked classes` with nothing checked. With this extension
installed that pair is reset at startup, so it should not survive from a previous session --
which makes a class rule set during this one, or something that is not about classes at all,
the likelier culprit. See [If everything disappears](#if-everything-disappears), and note that
`Restore the state from when the panel opened` -- on the toolbar button's right-click menu and
in **Extensions > Class Visibility** -- covers the non-class causes too, and does not need the
panel to be open.

**I get more cells than I expected.**
`Any` where you wanted `All`, or a near-universal component like `positive` in the mix under
`Any`. See [Combining components](#combining-components-any-and-all).

**I get no cells at all when I check two components.**
`All` mode, with no class in this image carrying both. Switch to `Any` to see what each one
matches on its own.

**Checking `CD3` used to also catch `CD31`.**
It did, in the old script -- that was a substring match, and it was a bug. QuPath compares
whole names now. See [Coming from the script](migration-from-the-script.md).

**QuPath's Annotations pane shows nothing selected but objects are hidden.**
You are in `All` mode, and the combined class is not in QuPath's list of available classes,
so there is no row for QuPath to highlight. Expected -- see
[One thing to expect in QuPath's Annotations pane](#one-thing-to-expect-in-qupaths-annotations-pane).
The panel's `Active rules` expander shows it, and `Reset all` clears it.

**A class I checked has vanished from the list.**
It is absent from the current image. The rule is still in force. See
[Working across several images](#working-across-several-images), or turn on
`Include classes not in this image`.

**I changed `List` to `Annotations` but my detections are still hidden.**
`List` chooses what is counted and listed. It never limits what is hidden -- one global,
type-blind setting drives that, so a hidden class is hidden for cells, detections and
annotations alike. This is the panel's most common misreading, and there is no setting that
changes it.

**The counts do not match my measurement table.**
Check the `List` scope. `Detections` is the default, and a measurement table showing cells,
annotations or every object will not agree with it. `Count` is also off by default in the
classes list -- the column on screen is `Affects`, which is a different number, and the two
are explained in [`Count` and `Affects`](#count-and-affects). If the column header reads
`Count (stale)`, a recount is on its way and the numbers you are reading are the previous
run's.

**The toolbar button is missing.**
Toolbar insertion is best-effort, and nothing depends on it. **Extensions > Class Visibility**
carries every item the button's menu does -- `Show panel`, `Restore the state from when the
panel opened`, `Reset all visibility` and `Help` -- so the recovery routes are still
there. What you lose is the at-a-glance signal: the button's slashed eye and its tooltip are
the quickest way to see whether any class rule is in force, and with no button you have to open
the panel and read the `Active rules` count instead. If the button is missing on your platform, that is worth
an issue -- it is not expected.

**I cannot find the panel at all.**
The extension deliberately adds nothing until you ask for it -- there is no tab and no window
until you press the toolbar button or choose **Extensions > Class Visibility > Show panel**.
If you had already docked it, it is a tab in the analysis pane, and that whole pane can be
collapsed (**View > Show analysis pane**).

**I docked it and now I want it floating again.**
Use the same control in reverse, or right-click the tab and choose "Undock tab" -- QuPath's
own gesture, which works on a docked panel just as it does on its built-in tabs. Neither move
is a close, so nothing you have set is lost and nothing is restored.

**The two lists are squashed together.**
You have docked the panel, and the analysis pane is narrow. Widen it by dragging its divider,
or move the panel back out into a floating window you can size yourself. See
[Where the panel lives](#where-the-panel-lives-floating-window-and-docked-tab).

**The panel is off-screen after changing monitors.**
It is designed not to be. The panel remembers its window position and size across restarts,
and on reopening it clamps that saved rectangle to the screen containing its centre, falling
back to the primary screen if the monitor it was on is gone. If it lands somewhere unreachable
anyway, `Dock as tab` puts it back inside the QuPath window; undock it again with the monitor
you want attached. This has not been exercised on a real multi-monitor machine yet -- if you
hit it, that is worth reporting.

**"The QuPath analysis pane is not available, so the Class visibility panel cannot be
shown."**
You asked to dock the panel and QuPath's analysis pane could not be reached, so the move did
not happen. Nothing is lost and nothing is broken: the panel stays in its own window and is
fully usable there. Every feature works in a window; docking is only ever a placement choice.

**`Save` and `Delete` beside `Preset:` are greyed out.**
No project is open -- presets are saved in the project. The combo says so, and its tooltip
names the way out: `File > Project > Create project`. See
[Presets: a view you named](#presets-a-view-you-named).

**My cells are drawn as dots.**
`Cell display` is on `Cell centroids only`. That is QuPath's own setting, shared with
`View > Cell display`, and it is remembered across a restart -- so it can be left over from a
session weeks ago. Change it in QuPath's `View` menu -- this panel points at that setting but
does not carry a copy of it. See
[`Cell display`: how cells are drawn](#cell-display-how-cells-are-drawn).

**Something else.**
File an issue on the repository's issue tracker, saying which platform you are on and which
QuPath version you are running. This extension has only ever been run on Linux, so a report
from macOS or Windows is useful rather than redundant.

</details>

---

<details><summary><strong>Doing this from a script</strong></summary>

## Doing this from a script

**There is no extension API, and you do not need one.** Everything this panel does is a
couple of lines against QuPath's own `OverlayOptions` from the script editor:

```groovy
import qupath.lib.gui.viewer.OverlayOptions
import qupath.lib.objects.classes.PathClass

def options = getCurrentViewer().getOverlayOptions()

// Hide every class containing both CD3 and CD8 -- the "All" combination, from a script.
options.selectedClassesProperty().add(PathClass.fromCollection(["CD3", "CD8"]))
options.setSelectedClassVisibilityMode(OverlayOptions.ClassVisibilityMode.HIDE_SELECTED)

// Show only those classes instead:
// options.setSelectedClassVisibilityMode(OverlayOptions.ClassVisibilityMode.SHOW_SELECTED)

// Back to a clean view:
// options.selectedClassesProperty().clear()
// options.setSelectedClassVisibilityMode(OverlayOptions.ClassVisibilityMode.HIDE_SELECTED)
```

That is the same object the panel writes to -- every viewer shares one `OverlayOptions` -- so
a script and the panel are editing the same set, and each sees what the other did. A rule
written by a script, or from QuPath's own class list, shows in the panel's `Active rules` with
the source `Set elsewhere`.

**With one exception, and it is the one to know: opening the panel clears the set, and closing
it puts the set back.** The panel's opening state is `Show only checked classes` with nothing
checked, so rules a script set beforehand are gone the moment you press the toolbar button --
and they return the moment you close it, along with the mode, the exact-match flag, opacity,
the cell display and the object-type toggles. Two consequences for a script:

- **Open the panel first and run the script second** if you want to see the script's rules in
  the panel. Everything a script sets while the panel is already open is picked up live, as it
  always was. `Restore the state from when the panel opened` gets a script's earlier rules
  back without closing the panel.
- **A script that runs while the panel is open will have its writes undone when the panel
  closes.** The restore replays the state from before the panel opened; it does not merge. If
  a script is meant to leave a filter behind, close the panel before running it.

Three things to know before you script it:

- The matching rules are the ones described in
  [What a checked class row acts on](#what-a-checked-class-row-acts-on): adding `CD3: CD8`
  to that set also affects every class carrying both parts, unless
  `setUseExactSelectedClasses(true)` is on.
- **Adding several classes separately means "any of these", not "all of them together".** For
  an `All` combination, build one composite with `PathClass.fromCollection` as above, rather
  than adding the parts one at a time.
- **`SHOW_SELECTED` with an empty set hides every object**, and the mode is a saved preference
  while the set is not. A script that leaves that pair behind is the state this panel exists to
  catch; end with `HIDE_SELECTED`, as the snippet's last two lines do.

A wrapper API around two public calls would be a second thing to keep in step with QuPath
for no capability you do not already have, which is why there is not one.

</details>

---

<details><summary><strong>Coming from the Groovy script</strong></summary>

## Coming from the Groovy script

If you arrived from
[image.sc topic 31828](https://forum.image.sc/t/qupath-script-ui-for-class-visibility-selection/31828)
or from [the gist](https://gist.github.com/Svidro/e00021dff92ea1173e535008854be72e), the
account of why the script stopped working, what QuPath now does on its own, and which
behaviours changed is on its own page:
[Coming from the "Show specific classes of objects" script](migration-from-the-script.md).

</details>
