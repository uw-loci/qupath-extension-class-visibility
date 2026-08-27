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

<details open><summary><strong>Getting started</strong> (read this first)</summary>

## Getting started

### Prerequisites

QuPath 0.7.0 or later, with the extension installed and QuPath restarted. Nothing in the
panel needs a project -- it works on a single open image -- but the panel is most useful
inside one.

### What this panel does, in one paragraph

It lists the classes carried by objects in the image you are looking at, and the
components those classes are made of, each with a count. Checking a row adds a rule that
either hides that class or shows only that class, depending on the mode. The panel does
not implement its own matching: it writes into the same QuPath setting that the built-in
Annotations pane writes into, so the panel, that pane and the viewer always agree.

### Where to find it

**Installing the extension changes nothing about your QuPath until you click the button.**
No new tab appears and your layout is untouched.

Two ways in, both reaching the same single panel:

- **The toolbar button.** It opens the panel as a floating window over QuPath.
- **Extensions > Class visibility > Show panel**, which does the same thing. Use this if the
  toolbar button did not appear (see [Troubleshooting](#troubleshooting)).

Once the panel is open you can **dock it as a tab** in the analysis pane, alongside Project,
Image, Annotations, Hierarchy and Workflow, if you would rather have it there permanently --
see [Where the panel lives](#where-the-panel-lives-floating-window-and-docked-tab). That is
your choice to make, not something the extension does to you on install.

The toolbar button is a toggle -- press it again to hide the panel -- and its tooltip changes
to say which it will do.

> **[Stub -- Phase 2]** Whether the panel comes back docked after a restart if you left it
> docked.

### Reading the panel

Top to bottom, the panel is:

| Zone | What it is |
|---|---|
| `Image:` | The image the rows and counts come from. This is the panel's way of telling you which image it is describing, and it is there in every layout. |
| `Visibility rule:` | Two radio buttons, `Hide checked classes` and `Show only checked classes`. See [below](#hide-checked-classes-vs-show-only-checked-classes). |
| `Exact matches only` | A QuPath-wide setting. While it is on, the whole component half of the panel is greyed out. See [below](#one-qupath-setting-can-switch-the-component-list-off). |
| `List:` | Which objects are counted and listed -- `Detections`, `Cells`, `Annotations` or `All objects`. **This never limits what gets hidden.** |
| `Find:` | One filter field over both lists. Case-insensitive, matches anywhere in the name. |
| `Auto-refresh counts` | On by default. Turn it off on very large images to keep the panel responsive; the count header then reads `Count (stale)` and a `Refresh` button appears. |
| Classes list | One row per class present in the image, with a checkbox, an `Only` button, the name and a `Count`. Sorted by `Count` descending by default. Below it: `Check all listed` and `Uncheck all listed`. |
| Components list | One row per component, with a checkbox, an `Only` button, the name, a `Classes` spread and a `Count`. Sorted by `Classes` ascending by default -- most specific first. |
| `Checked components combine as:` | The `Any` / `All` radios. Only meaningful from two checked components onward. |
| `Active rules` | An expander listing every rule in force, including rules with no row in the lists above. |
| Status strip | Always visible, always true. Says how many rules are active and what that means, and carries `Undo` and `Reset all`. |

Every control applies immediately. There is no `Apply` button, and nothing here needs
saving.

### Your first filter -- a 60-second walkthrough

1. Open a multiplexed image and open the panel with the toolbar button. The status
   strip reads
   `[OK] No rules active -- every object is visible.`
2. Leave the mode on **`Hide checked classes`**.
3. In the **Components** list, check one row -- say `CD8`. Every class containing `CD8` is
   now hidden, and the strip reads `[i] 1 rule active -- objects in that class are hidden.`
4. Uncheck it. Everything comes back.
5. Now click **`Only`** on the same row. The viewer shows only objects whose class contains
   `CD8`, and the mode radio visibly moves to **`Show only checked classes`** -- the action
   teaches the control. The strip reads
   `[i] Showing only CD8. Everything else is hidden.`
6. Click `Only` on that row again to undo it, or use the `Undo` button in the status strip,
   which names what it will undo: `Undo "Show only CD8"`.
7. **`Reset all`** always gets you back to a known state: no rules, `Hide checked classes`,
   `Exact matches only` off.

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

Pressing the button again hides the panel -- it is a toggle, and its tooltip changes to
`Hide the Class visibility panel.` while the panel is open.

### Docking, when you want it always there

If you would rather have the panel in the analysis pane than floating over your image,
choose **`Dock as tab`**. The panel moves into the analysis pane and becomes a tab like any
other, next to Annotations and Hierarchy. The same control in reverse moves it back out into
a floating window.

Once it is docked, QuPath's own undock gesture works on it too -- **right-click the tab and
choose "Undock tab"** -- the same gesture that works on QuPath's built-in tabs and on the
measurement table's histogram and scatter tabs. There is no new gesture to learn.

Which to reach for:

- **Floating** is the default, and the roomier one. Reach for it when you are working *on*
  the panel: comparing counts, hunting through 30 classes, setting up an `All` combination.
- **Docked** is the always-visible one. Reach for it when the panel is background furniture
  you glance at while working in the viewer, and you would rather it did not sit over the
  image.

> **[Stub -- Phase 2]** The exact label and position of the dock control, and the control
> that moves a docked panel back out.

### The panel changes shape with its width

There is one panel, and it lays itself out two ways depending on how much width it has:

- **Wide** (roughly 640 px and up, which is the usual floating size): the two lists sit side
  by side, classes on the left and components on the right, with a divider you can drag.
- **Narrow** (roughly 580 px and below, which is the usual docked width): the two lists
  stack vertically, classes above components, again with a draggable divider. The mode
  radios stack, and the list headers shorten -- `Components (2 of 17)` rather than
  `Components in this image (2 of 17)`. Nothing is lost: the panel's whole subject is the
  current image, and the `Image:` label one line above says which.

The two thresholds differ on purpose, so the layout does not flicker back and forth while
you drag a window edge or the analysis pane's divider through the switch point.

Squeezed narrower still, the component list drops its `Classes` spread column and the class
list narrows its `Count` column before the name. Both are recoverable: every table has a
column menu button in its header for putting columns back.

### If you docked it and it seems to have gone

QuPath's entire analysis pane can be collapsed (**View > Show analysis pane**), and a docked
tab inside a collapsed pane is invisible. If the panel disappeared shortly after you docked
it, check that first.

> **[Stub -- Phase 2]** Whether the toolbar button shows a collapsed analysis pane for you,
> or whether you have to show it yourself.

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
  Checking `CD3: CD8` acts on objects classified as exactly `CD3: CD8`, and nothing else.
- **Components** -- one row for each individual name that appears anywhere in those
  classes. Checking `CD3` acts on every class that *contains* `CD3`.

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

### The `Classes` column: how many classes a component covers

Each component row carries a **`Classes`** figure such as `6/28`. That is its **spread**:
how many of this image's classes contain that component. It is not an object count -- the
`Count` column is the object count.

The list is sorted by spread **ascending** by default, so the most specific components are
at the top. Every column is sortable if you want a different order, and the `Find` field is
usually a better way to look a marker up than any sort.

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

The panel tells you before you click rather than after. A component present in at least 80%
of the image's classes has its `Classes` figure shown in **bold**, and its row tooltip
states the scope plainly:

> `In 26 of the 28 classes in this image, and 401,552 of 452,110 objects.`

That is the whole treatment: a ratio, in bold, plus a sort order that keeps the least
specific components furthest from the top of the list. The panel does not tell you not to
click. Checking `positive` to get everything positive is a legitimate thing to want, and it
is a fast way to get it. (The emphasis is suppressed entirely on images with fewer than
five classes, where `3/3` means nothing.)

Two things follow:

- A component present in nearly every class behaves like a "select all" for that list.
- It behaves very differently in the two combination modes. In **`All`** mode a
  near-universal component costs you almost nothing -- `CD8` plus `positive` matches roughly
  what `CD8` matched alone. In **`Any`** mode it swamps everything else: adding `positive`
  to any other component gives you nearly the whole image. This is the single most common
  reason for "I checked one more thing and got *more* cells than before".

When checking such a component does empty most of the viewer, the status strip says so
after the fact as well:
`"positive" is in 26 of 28 classes and 401,552 of 452,110 objects.`

### Unclassified objects

Objects with no class get one row of their own, **`Unclassified`**, in the classes list. It
sorts to the bottom whatever else you sort by, because it is a category rather than a class.
It is never matched by a component row -- an object with no class has no components to
match. If you want to hide unclassified objects, check that row.

(Internally QuPath represents "no class" in two different ways depending on how the object
was created. The panel folds both into this one row, so the count you see is the real count.)

### Zero-count classes

By default the classes list shows only classes carried by objects in the current image.
Check **`Include classes with no objects here`** to add the rest of your project's available
classes as well. They show a `Count` of zero, render muted, and sort to the bottom -- present
and absent are never interleaved.

Two reasons to turn it on: a rule for a class that is absent from this image is perfectly
legitimate (rules are global -- see
[Working across several images](#working-across-several-images)), and with the option on the
rows stop jumping around as you flip between images. It is off by default because at 28
classes the question is usually "what is in front of me", and padding that with 40 absent
classes answers a different one.

The panel **reads** your project's class list and never writes to it. QuPath's own
"Populate from image" action does write to it; this panel deliberately has no equivalent.

### One QuPath setting can switch the component list off

**`Exact matches only`**, the checkbox under the mode radios, is bound to QuPath's own
setting **"Show/hide exact class matches only"** -- the one under the **More options** button
at the top of the class list in the Annotations tab. It is a saved preference, so it can be
on from a session weeks ago.

While it is on, only whole-class matches count, and **component rules cannot match anything**.
The panel does not let you find that out by trial and error:

- the whole component half of the panel is greyed out and cannot be clicked;
- a warning strip appears -- `[!] "Exact matches only" is on. Component rules match only a
  class with exactly that name, so they will not find derived classes.` -- with a
  **`Turn off`** button;
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

With **no** components checked, or with exactly **one**, the two modes behave identically --
and the radios are disabled, with a tooltip saying so. They diverge from two components
onward, which is precisely when people start wondering why they got more (or fewer) cells
than they expected.

The control is labelled **`Checked components combine as:`**, and its label spells out the
current choice as you go, e.g. `All -- CD3 and CD8 together`.

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
`CD3: CD8` for the example above. That combination usually does not exist in your project's
list of available classes, so QuPath's own class list has no row to highlight and will look
as though nothing is selected, even while objects are being hidden.

This is expected. The panel's `Active rules` expander lists that rule with the status
`Composite -- not in QuPath's class list`, the status strip counts it, and `Reset all` clears
it.

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

`Hide checked classes` is QuPath's default and is the mode you want most of the time: start
from everything and take things away. `Show only checked classes` is the mode you want when
you have 28 classes and care about two of them -- which, on a highly multiplexed image, is
most of the time. Clicking any row's **`Only`** button switches you into it.

The mode is **global**. It applies to every viewer, every image, and every project -- there
is one setting for the whole application, not one per image. The panel reads the current
value when it opens and reflects it; it never sets it just by being opened.

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

</details>

---

<details><summary><strong>If everything disappears</strong></summary>

## If everything disappears

This is the failure this panel is most careful about, and it is worth understanding whether
or not you use the panel, because you can reach it with stock QuPath alone.

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

If you would rather not work through them one at a time, the toolbar button's
**`Restore visibility state...`** covers all of them at once -- see below.

### Two ways back, and they are not the same

**1. `Reset all` -- go to QuPath's defaults.**

The **`Reset all`** button in the panel's status strip does exactly three things, in this
order, which is exactly what QuPath's own reset does:

- switch the mode to `Hide checked classes`;
- turn `Exact matches only` off;
- clear every rule.

This is the sledgehammer. It always reaches a known good state, and it fills the undo slot
first, so `Undo` will bring your rules back if you did not mean it.

**2. `Restore visibility state...` -- go back to the state you had.**

Right-click the toolbar button (or click its arrow) and choose
**`Restore visibility state...`**. This restores a stored snapshot rather than resetting to
defaults -- so it puts back the state you *had*, not the state QuPath ships with.

**The panel takes a snapshot automatically, before the first change it makes in a session.**
You do not have to have planned ahead for this to be available. You can also take one
yourself at any point with **`Save visibility state`** in the same menu.

The snapshot covers the whole "why can't I see anything" surface, not just this panel's
class rules: the class rules and the mode and `Exact matches only`, but also the show/hide
toggles for detections, annotations, the TMA grid and connections, the fill settings, TMA
core labels, the grid, pixel classification, plus overlay opacity and the cell display mode.
That is why it is worth reaching for when something vanished and you are not sure what you
changed.

**Which to use:** `Reset all` if you want a clean slate and do not mind losing your setup.
`Restore visibility state...` if you had things how you wanted them a minute ago and would
like that back.

There is **one** saved state, not a history: saving again replaces it. If nothing has been
saved and the panel has not yet changed anything, the menu item says so --
`No visibility state has been saved yet. One is saved automatically the first time this panel
changes anything.` The panel tells you when it saves and when it restores, so neither happens
silently.

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

The panel guards the specific transition that creates this state: if you close the panel, or
quit QuPath, while the mode is `Show only checked classes` and nothing is checked,
the panel puts the mode back to `Hide checked classes` before it goes. An empty "show only"
has no useful meaning, so nothing is lost by refusing to leave you in it. It tells you when
it does this rather than fixing it silently:

> **Class visibility**
> Switched "Show only checked classes" back to "Hide checked classes" because no classes
> were checked. Otherwise every object would have been hidden the next time QuPath starts.

The guard deliberately does **not** fire while the panel is open. Auto-flipping the mode
because you unchecked your last class on the way to checking a different one would move a
control under your hand. While the panel is open, the status strip's warning and its one-click
`Switch to "Hide checked classes"` do the job instead.

**What the guard cannot do:** it only runs when the panel is involved. If you set "Hide by
default" from QuPath's own Annotations pane, with this extension not installed or the panel
not open, nothing intercepts it -- the recovery above is your route back. The panel
reduces the chance of ending up there; it does not make the state unreachable.

</details>

---

<details><summary><strong>The toolbar button's menu</strong></summary>

## The toolbar button's menu

The toolbar button carries a small arrow marking an extra menu. Right-click the button (or
click the arrow) for:

| Item | What it does |
|---|---|
| **`Restore visibility state...`** | Put back a stored snapshot of your visibility settings. See [If everything disappears](#if-everything-disappears). |
| **`Save visibility state`** | Take a snapshot now, so you can come back to this exact state later. |
| **`Reset all visibility`** | The same three-step reset as the panel's `Reset all`: mode to `Hide checked classes`, `Exact matches only` off, every rule cleared. Available without opening the panel. |
| **`Show panel`** | Open the panel, the same as pressing the button. |
| **`Help`** | A short summary inside QuPath: what the two lists do, what `List` does *not* do, and the two ways to get your objects back. This guide is the longer version. |

`Restore visibility state...` and `Reset all visibility` are reachable from the toolbar
without the panel being open, which is the point: if your viewer is blank, you should not
have to open a panel to fix it.

> **[Stub -- Phase 2]** The exact gesture for the arrow -- hover, click, or both.

</details>

---

<details><summary><strong>Common tasks</strong></summary>

## Common tasks

### Show only one class

Click **`Only`** on its row. This switches the mode to `Show only checked classes` and
leaves exactly that one rule in force. The row renders in bold with a marker on its leading
edge so you can see which one is soloed.

Three ways to undo it, in increasing scope: click `Only` on the same row again; press the
status strip's `Undo` button (or `Ctrl+Z` with focus anywhere in the panel), which is
labelled with what it will undo; or `Reset all`.

`Only` works on component rows too -- `Show only objects whose class contains this
component`.

### Hide one noisy class and keep working

Leave the mode on `Hide checked classes` and check that class's row. Everything else stays
visible. Uncheck it to bring it back.

### Find every class containing a marker

Use the **Components** list rather than the classes list -- that is what it is for. Check
`CD8` and every class containing `CD8` is covered by one rule, in any position and any
order.

### Find a class or a component by name

Type into **`Find`** (`Ctrl+F` from anywhere in the panel, `Cmd+F` on macOS). One field
filters both lists at once, matching anywhere in the name, case-insensitively. The list
headers tell you when a list is filtered -- `Classes in this image (3 of 28)`.

`Escape` clears the filter without leaving the field. The `Find` text is not remembered
between sessions: reopening onto a filtered list you had forgotten about is its own version
of "where did everything go".

### Act on a filtered set of classes

Filter with `Find`, then use **`Check all listed`** or **`Uncheck all listed`**. Both act on
the rows currently listed -- classes hidden by the filter are not touched, which is why
"listed" is in the label.

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
| `Listed above` | it has a row in one of the lists |
| `Not in this image` | the class is not carried by anything in the current image |
| `Composite -- not in QuPath's class list` | an `All` combination, which is expected |
| `Limited by "Exact matches only"` | the QuPath setting is on and this rule cannot match |

Each row has a **`Remove`** button that takes out that one rule, and there is a
**`Clear all rules`** button that removes every rule while leaving the show/hide mode alone.

### Clear everything and start again

**`Reset all`**, in the status strip. Mode back to `Hide checked classes`, `Exact matches
only` off, every rule cleared -- and the undo slot filled first, so `Undo` brings your rules
back if you did not mean it.

### Work on a very large image without the panel slowing you down

Turn off **`Auto-refresh counts`**. Counting classes is a full walk of the object hierarchy,
and on a slide with millions of objects, during a classifier run, that is the one thing in
this panel you might feel. With it off, the counts freeze, the column header reads
`Count (stale)` so you know they are, and a **`Refresh`** button recounts on demand. Hiding
and showing are unaffected -- only the counting is.

### Keyboard

| Key | Where | Action |
|---|---|---|
| `Ctrl+F` / `Cmd+F` | anywhere in the panel | focus `Find` |
| `Escape` | in `Find` | clear the filter, keep focus |
| `Up` / `Down` | in a list | move between rows |
| `Space` | in a list | check or uncheck the focused row |
| `O` | in a list | `Only` on the focused row |
| `Ctrl+Z` | anywhere in the panel | undo the last bulk action |

`Escape` does not close the panel. It is a pane, not a dialog.

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
it simply has no row to show it in, because nothing in this image carries that class. It
appears in `Active rules` with the status `Not in this image`, and the status strip appends
`1 rule has no class in this image.`

This is why the panel counts **rules** rather than checked rows: the two numbers can
legitimately differ, and a rule you cannot see is still a rule. A row-counting indicator
would cheerfully report `0 rules active` while objects were being hidden -- which is the
exact failure this panel exists to prevent.

If you would rather the rows stayed put as you flip between images, turn on
**`Include classes with no objects here`**.

**With no image open** the lists show `No image open. Open an image to list the classes it
uses.`, and any rules you have set stay in force -- the status strip says
`[i] 2 rules active -- they apply to every image.`

</details>

---

<details><summary><strong>What the panel does not touch</strong></summary>

## What the panel does not touch

The panel changes **what you see**. It never changes **what you have**.

Specifically, nothing in this panel will:

- change any object's class;
- add to, remove from, or reorder your project's list of available classes;
- delete, merge, or move objects;
- modify measurements or any other object data;
- write anything into your project.

Two consequences worth knowing:

- **Nothing here needs saving, and nothing here can be lost.** Close the panel, close the
  image without saving -- your classifications are untouched, because they were never
  touched.
- **The class list is read-only with respect to your project.** QuPath's own Annotations pane
  has a "Populate from image" action that *adds* every class found in the image to your
  project's available classes. This panel deliberately does not do that. It shows you what is
  in the image without changing what your project knows about.

If you are coming from the tabbed variant of the old Groovy script: that version had a
**"Reset classifications"** button sitting a few pixels below the visibility checkboxes,
which called `resetDetectionClassifications()` and **destroyed your classifier output**.
There is deliberately no such button here, and no control in this panel uses "classify" as a
verb.

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
| `Hide checked classes` / `Show only checked classes` | QuPath | **yes** | global |
| `Exact matches only` | QuPath | **yes** | global |
| Which classes are checked (your rules) | QuPath | **no** | global for the session |
| `Any` / `All` | the panel | yes | the panel |
| `List` scope | the panel | yes | the panel |
| `Auto-refresh counts` | the panel | yes | the panel |
| Divider position between the two lists | the panel | yes | separately for the wide and narrow layouts |
| `Active rules` expanded or collapsed | the panel | yes | the panel |
| Sort column and direction, per list | the panel | yes | the panel |
| `Find` text | -- | **no**, deliberately | -- |
| Docked or floating, and the window's position and size | the panel | **[Stub -- Phase 2]** | -- |

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

**Everything is invisible and I did not do anything.**
Almost certainly `Show only checked classes` with nothing checked, carried over from a
previous session. See [If everything disappears](#if-everything-disappears) -- and note that
`Restore visibility state...` on the toolbar button's menu also covers the causes that are
not about classes at all, and does not need the panel to be open.

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
You are in `All` mode, and the combined class is not in your project's class list, so there
is no row for QuPath to highlight. Expected -- see
[One thing to expect in QuPath's Annotations pane](#one-thing-to-expect-in-qupaths-annotations-pane).
The panel's `Active rules` expander shows it, and `Reset all` clears it.

**A class I checked has vanished from the list.**
It is absent from the current image. The rule is still in force. See
[Working across several images](#working-across-several-images), or turn on
`Include classes with no objects here`.

**I changed `List` to `Annotations` but my detections are still hidden.**
`List` chooses what is counted and listed. It never limits what is hidden -- one global,
type-blind setting drives that, so a hidden class is hidden for cells, detections and
annotations alike. This is the panel's most common misreading, and there is no setting that
changes it.

**The counts do not match my measurement table.**
Check the `List` scope. `Detections` is the default, and a measurement table showing cells,
annotations or every object will not agree with it. Also check whether
`Auto-refresh counts` is off -- the column header reads `Count (stale)` when it is.

**The toolbar button is missing.**
Toolbar insertion is best-effort, and the panel does not depend on it. Use
**Extensions > Class visibility > Show panel** instead. If the button is missing on your
platform, that is worth an issue -- it is not expected.

**I cannot find the panel at all.**
The extension deliberately adds nothing until you ask for it -- there is no tab and no window
until you press the toolbar button or choose **Extensions > Class visibility > Show panel**.
If you had already docked it, it is a tab in the analysis pane, and that whole pane can be
collapsed (**View > Show analysis pane**).

**I docked it and now I want it floating again.**
Use the same control in reverse, or right-click the tab and choose "Undock tab" -- QuPath's
own gesture, which works on a docked panel just as it does on its built-in tabs.

**The two lists are squashed together.**
You have docked the panel, and the analysis pane is narrow. Widen it by dragging its divider,
or move the panel back out into a floating window you can size yourself. See
[Where the panel lives](#where-the-panel-lives-floating-window-and-docked-tab).

**The panel is off-screen after changing monitors.**
Dock it as a tab, which puts it back inside the QuPath window, then float it again with the
monitor you want attached.

> **[Stub -- Phase 2]** Whether the panel remembers a window position that no longer exists
> on the current monitor layout, and what recovers it if so.

**Something else.**
File an issue on the repository's issue tracker, saying which platform you are on. No
platform has been verified yet, so a platform-specific report is useful rather than
redundant.

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
