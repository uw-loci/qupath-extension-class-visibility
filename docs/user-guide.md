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

| The panel says | What it means |
|---|---|
| `[OK] No class rules active -- nothing is hidden by class.` | No class rule is in force. Note what it does **not** say: if your viewer is still blank, the cause is not a class rule -- see [It might not be a class rule at all](#it-might-not-be-a-class-rule-at-all). |
| `[!] Every object is hidden. "Show only checked classes" is on and nothing is checked.` | The one state this panel exists to catch. One click on the button beside it fixes it -- see [If everything disappears](#if-everything-disappears). |
| `[i] 1 rule active -- objects in that class are hidden, in every object type.` | Normal `Hide checked classes` operation. "In every object type" is not decoration -- see [`List` chooses what you see here](#list-chooses-what-you-see-here-not-what-gets-hidden). |
| `[i] 1 rule active -- only objects matching it are shown.` | Normal `Show only checked classes` operation. |
| `[i] Showing only CD3: CD8 and any class containing all of its parts. Everything else is hidden.` | You clicked `Only` on a class row that has supersets in this image -- see [What a checked class row acts on](#what-a-checked-class-row-acts-on). |
| `[i] Showing only classes containing CD8. Everything else is hidden.` | You clicked `Only` on a component row. |
| `[i] Showing only CD3: CD8. Everything else is hidden.` | You soloed something that reaches nothing beyond itself -- an exact rule, or a class with no supersets in this image. |
| `1 rule has no class in this image.` | A rule for a class the current image does not carry. It is still in force -- see [Working across several images](#working-across-several-images). |
| `[i] 2 rules active -- they apply to every image.` | No image is open, and rules are still set. |
| `[!] "Exact matches only" is on. ...` | A QuPath-wide setting is blocking component rules -- see [One QuPath setting can switch the component list off](#one-qupath-setting-can-switch-the-component-list-off). |
| `Counting classes in ... ` | A recount is running. On a very large image, see [Work on a very large image](#work-on-a-very-large-image-without-the-panel-slowing-you-down). |
| `"positive" is in 26 of 28 classes and 401,552 of 452,110 objects.` | You checked a component that is in nearly every class -- see [Components that appear in almost every class](#components-that-appear-in-almost-every-class). |
| `The panel is closed, but 3 class rules are still in force. Objects stay hidden until you clear them.` | A notification when you close the panel with rules set -- see [Your rules outlive the panel](#your-rules-outlive-the-panel-and-the-toolbar-button-says-so). |
| `Switched "Show only checked classes" back to "Hide checked classes" ...` | The guard fired -- see [What the panel does about it](#what-the-panel-does-about-it). |
| `Composite -- not in QuPath's class list` (in `Active rules`) | An `All` combination. Expected -- see [One thing to expect in QuPath's Annotations pane](#one-thing-to-expect-in-qupaths-annotations-pane). |
| `Already set by the component rule below. ...` | A class row is checked and disabled because a component rule already covers it -- see [How the two lists combine](#how-the-two-lists-combine). |

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

- **The toolbar button**, immediately right of brightness/contrast and drawn as an **eye**.
  It opens the panel as a floating window over QuPath.
- **Extensions > Class visibility > Show panel**, which does the same thing. Use this if the
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
[Your rules outlive the panel](#your-rules-outlive-the-panel-and-the-toolbar-button-says-so).

**The panel does not come back after a restart, docked or otherwise.** It starts closed in
every session and always reopens as a floating window, even if you left it docked. There is
no "show at startup" preference: an extension that opens a window at launch is one you have
to close at every launch.

### Reading the panel

Top to bottom, the panel is:

| Zone | What it is |
|---|---|
| `Image:` | The image the rows and counts come from. This is the panel's way of telling you which image it is describing, and it is there in every layout. At the right of the same row: a **`?`** button with a short summary of the panel and what to do if the viewer goes blank, and the `Dock as tab` / `Undock to window` button. |
| `Visibility rule:` | Two radio buttons, `Hide checked classes` and `Show only checked classes`. See [below](#hide-checked-classes-vs-show-only-checked-classes). |
| `Exact matches only` | A QuPath-wide setting. While it is on, the whole component half of the panel is greyed out. See [below](#one-qupath-setting-can-switch-the-component-list-off). |
| `List:` | Which objects are counted and listed -- `Detections`, `Cells`, `Annotations` or `All objects`. It chooses what you *see in this panel*, never what gets hidden. See [the next section](#list-chooses-what-you-see-here-not-what-gets-hidden). |
| `Find:` | One filter field over both lists. Case-insensitive, matches anywhere in the name. |
| `Auto-refresh counts` | On by default. Turn it off on very large images to keep the panel responsive; the count header then reads `Count (stale)` and a `Refresh` button appears. |
| Classes list | One row per class present in the image, with a checkbox, an `Only` button, a **colour swatch** and the name, then a `Count` and an **`Affects`**. `Count` is how many objects carry this class; `Affects` is how many a click on the row would actually hide or show, which can be larger -- see [What a checked class row acts on](#what-a-checked-class-row-acts-on). Sorted by `Count` descending by default. Below it: `Check all listed` and `Uncheck all listed`. |
| Components list | One row per component, with a checkbox, an `Only` button, the name, a **`Spread`** and a `Count`. Sorted by name, alphabetically, by default. |
| `Checked components combine as:` | The `Any` / `All` radios. Only meaningful from two checked components onward. |
| `Active rules` | An expander listing every rule in force, including rules with no row in the lists above. |
| Status strip | Always visible, always true. Says how many rules are active and what that means, and carries `Undo` and `Reset all`. `Undo` is one step deep and always names the step -- `Undo "Check CD8"`, `Undo "Show only CD3: CD8"`, `Undo "Reset all"` -- so it is never a guess. Every action reaches it: single rows, the bulk buttons, `Only`, and `Reset all` alike. |

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

1. Open a multiplexed image and open the panel with the toolbar button. The status
   strip reads
   `[OK] No class rules active -- nothing is hidden by class.`
2. Leave the mode on **`Hide checked classes`**.
3. In the **Components** list, check one row -- say `CD8`. Every class containing `CD8` is
   now hidden, and the strip reads `[i] 1 rule active -- objects in that class are hidden,
   in every object type.`
   (One checked component is **one rule**, however many class names it covers. The strip
   says "class" because `CD8` is handed to QuPath as a class in its own right, and QuPath
   matches every class containing it -- see
   [What "contains" means, precisely](#what-contains-means-precisely).)
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

Pressing the button again closes the panel -- it is a toggle, and its tooltip always names
what the next click will do:

| Tooltip | The next click will |
|---|---|
| `Show the Class visibility panel. Hide or show objects by class.` | open it |
| `Bring the Class visibility panel to the front.` | raise it, because it is open but buried |
| `Close the Class visibility panel.` | close it |

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
  by side, classes on the left and components on the right, with a divider you can drag.
- **Narrow** (roughly 580 px and below, which is the usual docked width): the two lists
  stack vertically, classes above components, again with a draggable divider. The mode
  radios stack, and the list headers shorten -- `Components on detections (2 of 17)` rather
  than `Components on detections in this image (2 of 17)`. Nothing is lost: the panel's whole
  subject is the current image, and the `Image:` label one line above says which.

The two thresholds differ on purpose, so the layout does not flicker back and forth while
you drag a window edge or the analysis pane's divider through the switch point.

Squeezed narrower still, the class list drops its `Affects` column and the component list
drops its `Spread` column -- the two that are orienting information rather than the thing you
came to click. Both are recoverable: every table has a column menu button in its header for
putting columns back, and you can also just make the panel wider.

### If you docked it and it seems to have gone

QuPath's entire analysis pane can be collapsed (**View > Show analysis pane**), and a docked
tab inside a collapsed pane is invisible. If the panel disappeared shortly after you docked
it, check that first.

**You do not have to expand it yourself.** Pressing the toolbar button, or choosing
**Extensions > Class visibility > Show panel**, expands a collapsed analysis pane before
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

| Class in the image | `Count` | Acted on, by default | With `Exact matches only` on | Why |
|---|---|---|---|---|
| `CD3: CD8` | 412 | yes | yes | it is that class |
| `CD8: CD3` | 51 | yes | yes | order is never significant |
| `CD3: CD8: PD1` | 1,204 | **yes** | no | it carries both `CD3` and `CD8`; the extra part does not stop it |
| `CD3: CD8: CD4: CD45` | 880 | **yes** | no | same again |
| `CD3` | 9,310 | no | no | it does not carry `CD8` |
| `CD31: CD8` | 77 | no | no | `CD31` is a different name from `CD3` |

The row you clicked shows `Count 412`. What actually disappears is 2,547.

### `Count` and `Affects`

That gap is why the class list has **two** number columns:

| Column | What it counts |
|---|---|
| **`Count`** | objects carrying **exactly** this class -- 412 |
| **`Affects`** | objects a click on this row would hide or show **right now** -- 2,547 |

`Affects` follows the current settings rather than describing an idea: turn on
`Exact matches only` and it drops to match `Count`, because that is genuinely what the click
would then do. It renders in **bold** whenever it is larger than `Count`, which is the panel
saying *this row reaches further than it looks*. When the two are equal it is plain, and on a
small project they will usually be equal all the way down the list.

`Affects` is the number to trust for a decision and the number to read after a surprise.
`Count` is still the honest answer to "how many cells are `CD3: CD8`", which is the question a
figure legend usually asks -- but take a figure's numbers from a measurement table, which
visibility never touches, rather than from either column here.

(Both columns are dropped when the panel gets very narrow, `Affects` first. The column menu
button in the table header puts them back.)

**`Exact matches only` narrows a class row to the class itself.** That checkbox is QuPath's
own setting rather than the panel's, and it has a second effect you probably do not want as a
side-effect: while it is on, component rules cannot match anything at all. See
[One QuPath setting can switch the component list off](#one-qupath-setting-can-switch-the-component-list-off).
If you need exact class rows *and* component rules in the same session, you cannot have both
at once.

**`Only` inherits all of this**, and says so. Soloing `CD3: CD8` shows that class and its
supersets, and the status strip reads
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

Each component row carries a **`Spread`** figure such as `6/28`: how many of this image's
classes contain that component. It is not an object count -- the `Count` column is the object
count.

Two things about the denominator, because both can shift a ratio:

- It is the number of classes **carried by objects in this image** -- by default, the same
  number the classes list header shows. It does **not** grow when you turn on
  `Include classes with no objects here`: a class no object carries is reach a component
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

The panel tells you before you click rather than after. A component present in at least 80%
of the image's classes has its `Spread` figure shown in **bold**, and its row tooltip
states the scope plainly:

> `In 26 of the 28 classes in this image, and 401,552 of 452,110 objects.`

That is the whole treatment: a ratio, in bold, at the point where you are deciding. The panel
does not tell you not to click. Checking `positive` to get everything positive is a legitimate thing to want, and it
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

With **no** components checked, or with exactly **one**, the two modes behave identically, so
the radios are greyed out and the label says so:
`Checked components combine as: (check two or more)`. It says it in the label rather than in a
tooltip because JavaFX shows no tooltip on a disabled control -- a greyed pair of radios you
cannot hover for a reason is how a working panel reads as a broken one.

They diverge from two components onward, which is precisely when people start wondering why
they got more (or fewer) cells than they expected.

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

If you would rather not work through them one at a time, **`Restore visibility state...`**
covers all of them at once -- see below. It is on the toolbar button's right-click menu and
under **Extensions > Class visibility**, and neither route needs the panel open.

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

Right-click the toolbar button (or click its arrow), or open **Extensions > Class
visibility**, and choose **`Restore visibility state...`**. This restores a stored snapshot
rather than resetting to defaults -- so it puts back the state you *had*, not the state QuPath
ships with.

**The panel takes a snapshot automatically, before the first change it makes in a session.**
You do not have to have planned ahead for this to be available. You can also take one
yourself at any point with **`Save visibility state`**, in either menu.

The snapshot covers the whole "why can't I see anything" surface, not just this panel's
class rules: the class rules and the mode and `Exact matches only`, but also the show/hide
toggles for detections, annotations, the TMA grid and connections, the fill settings, TMA
core labels, the grid, pixel classification, plus overlay opacity and the cell display mode.
That is why it is worth reaching for when something vanished and you are not sure what you
changed.

**It puts back all of that, and the snapshot may not be recent.** The automatic one is taken
at the panel's first change *of the session*, which could be hours and several images ago,
and there is one slot rather than one per image. So if you turned detections off and dialled
opacity down for a screenshot at three o'clock, and then restored at five past three to fix a
class rule, your detections and your opacity go back to where they were this morning as well.
Nothing is destroyed and everything is re-settable, but it is a wider undo than the problem
usually needs -- if you know the trouble is a class rule, `Reset all` or unchecking the row is
the narrower tool. Take a fresh snapshot with **`Save visibility state`** whenever you have
the view set up the way you want it, and the restore stops being a trip backwards in time.

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

The extension guards the specific transition that creates this state: **if you close the
panel, or quit QuPath**, while the mode is `Show only checked classes` and nothing is checked,
the mode goes back to `Hide checked classes` before anything is saved. An empty "show only"
has no useful meaning, so nothing is lost by refusing to leave you in it. It tells you when it
does this rather than fixing it silently:

> **Class visibility**
> Switched "Show only checked classes" back to "Hide checked classes" because no classes
> were checked. Otherwise every object would have been hidden the next time QuPath starts.

The guard deliberately does **not** fire while the panel is open. Auto-flipping the mode
because you unchecked your last class on the way to checking a different one would move a
control under your hand. While the panel is open, the status strip's warning and its one-click
`Switch to "Hide checked classes"` do the job instead.

**The quit half does not need the panel to be open.** The guard is installed when the
extension loads, not when the panel opens, so it runs at every quit -- including a session in
which you never opened the panel and reached the empty "show only" state from QuPath's own
class list.

**One case where it fires and you do not quit.** QuPath can cancel a quit -- unsaved viewers,
a running script, an open script editor -- and the guard has already run by then, so you are
left in `Hide checked classes` in a session that is still going. That is deliberate: the only
state it ever moves you out of is the one where you are looking at a completely empty viewer,
so it is a rescue rather than a control moving under your hand, and the notification says what
happened either way.

**What the guard does not cover:** it only ever acts on the *empty* "show only" state. A view
hiding three of your forty classes is left exactly as you set it, at panel close and at quit
alike -- that is a filter you built, not a state you fell into. See
[Your rules outlive the panel](#your-rules-outlive-the-panel-and-the-toolbar-button-says-so).

</details>

---

<details><summary><strong>The two menus</strong></summary>

## Two menus, and everything is in both

The recovery actions live in **two** places, deliberately: the toolbar button's right-click
menu, and **Extensions > Class visibility**. Toolbar insertion is best-effort -- the panel
does not control QuPath's layout and the button can fail to appear -- and a recovery route
whose only door is the component most likely to be missing is the wrong design. Whichever you
reach for, the items are the same:

| Item | What it does |
|---|---|
| **`Restore visibility state...`** | Put back a stored snapshot of your visibility settings. See [If everything disappears](#if-everything-disappears). |
| **`Save visibility state`** | Take a snapshot now, so you can come back to this exact state later. |
| **`Reset all visibility`** | The same three-step reset as the panel's `Reset all`: mode to `Hide checked classes`, `Exact matches only` off, every rule cleared. Available without opening the panel. |
| **`Show panel`** / **`Hide panel`** | Open the panel, or close it if it is already in front -- the same three outcomes as the button itself. The label says which it will do. |
| **`Help`** | The same short summary the panel's **`?`** button shows: what the two lists do, what `List` does *not* do, and the ways to get your objects back. This guide is the longer version. |

The first three are reachable **without the panel being open**, which is the whole point: if
your viewer is blank, you should not have to open a panel to fix it. `Restore visibility
state...` greys itself out and renames itself `Restore visibility state (none saved)` when
there is nothing to go back to, so it never looks like a route that failed.

**Either gesture opens the toolbar menu.** Right-click anywhere on the button, or left-click
the small triangle at its bottom-right corner. The triangle is a marker rather than a target
-- it is a few pixels across and drawn faintly -- so right-clicking the button is the gesture
to learn, and the Extensions menu is the one to fall back on.

**Hovering the button is worth a mention of its own.** Its tooltip names both what the next
click will do *and* how many class rules are currently in force -- see
[Your rules outlive the panel](#your-rules-outlive-the-panel-and-the-toolbar-button-says-so).

</details>

---

<details><summary><strong>Common tasks</strong></summary>

## Common tasks

### Show only one class

Click **`Only`** on its row. This switches the mode to `Show only checked classes` and
leaves exactly that one rule in force, so that row's checkbox becomes the only checked one in
the list and **its name renders in bold**. If you have scrolled away from it, the status strip
names what is soloed.

On a class row this shows the class **and everything containing all of its parts** -- see
[What a checked class row acts on](#what-a-checked-class-row-acts-on) -- and the status strip
says which case you are in rather than making you work it out:

| The strip says | You soloed |
|---|---|
| `[i] Showing only CD3: CD8 and any class containing all of its parts. Everything else is hidden.` | a class row that has supersets in this image, so `CD3: CD8: PD1` is on screen too |
| `[i] Showing only classes containing CD8. Everything else is hidden.` | a component row |
| `[i] Showing only CD3: CD8. Everything else is hidden.` | something that reaches nothing else -- `Exact matches only` is on, or the class has no supersets here |

Three ways to undo it, in increasing scope: click `Only` on the same row again; press the
status strip's `Undo` button, which is labelled with what it will undo; or `Reset all`.

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
headers tell you when a list is filtered -- `Classes on detections in this image (3 of 28)`.

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

Turn off **`Auto-refresh counts`**. Counting classes is a full walk of the object hierarchy,
and on a slide with millions of objects, during a classifier run, that is the one thing in
this panel you might feel. With it off, the counts freeze, the column header reads
`Count (stale)` so you know they are, and a **`Refresh`** button recounts on demand. Hiding
and showing are unaffected -- only the counting is.

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
| `O` | in a list | `Only` on the focused row |
| `Ctrl+C` / `Cmd+C` | in the `Active rules` table | copy every rule to the clipboard |

`Escape` does not close the panel. It is a pane, not a dialog.

**`Ctrl+Z` is deliberately not on this list.** Undo lives on the status strip's **`Undo`**
button and nowhere else. The panel used to take the accelerator, which meant that deleting an
annotation, clicking into the panel and pressing `Ctrl+Z` undid something in the panel while
your deletion stood -- with nothing on screen saying so. `Ctrl+Z` now reaches QuPath, as it
should; the button is one click and, unlike a key, it tells you what it is about to undo.

**`O` is not a small key.** Unmodified, with a row focused, it does the same thing the `Only`
button does -- switches the global mode to `Show only checked classes` and hides everything
but that row. Press it by accident and most of your image disappears; `Undo`, or a second `O`
on the same row, puts it back. It also means the table's built-in type-to-search cannot reach
a row whose name begins with `O`.

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

### Your rules outlive the panel, and the toolbar button says so

Closing the panel does not clear your rules. They belong to QuPath, not to the panel, so
`Hide checked classes` with three classes checked stays exactly that with the panel gone --
which is usually what you want, because closing the panel to reclaim screen space is a
reasonable thing to do while working filtered. Clearing them for you would make the panel
destructive of your working state in order to protect you from forgetting it.

It matters because a *blank* viewer announces itself -- nobody mistakes it for data -- while a
viewer showing thirty-seven of your forty classes looks completely normal, and that is the one
somebody screenshots. So the panel says it twice:

- **When you close the panel with rules set**, a notification: `The panel is closed, but 3
  class rules are still in force. Objects stay hidden until you clear them.`
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
  mark a class that is in your project's list of available classes.** A class that exists on
  your objects and not in that list has no row to carry the mark. On a classifier's output in
  a project where nobody ran "Populate from image", that is the common case rather than the
  edge case. This panel deliberately does not add classes to the project's list to fix it;
  writing into your class list to improve a status icon is a worse trade.

If you are about to hand the screen, a screenshot or an image export to anyone else, **glance
at the toolbar button: a slashed eye means a filter is on.** Hover it for the count, and either
note what is in force or clear it with **Extensions > Class visibility > Reset all
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
| `Hide checked classes` / `Show only checked classes` | QuPath | **yes** | global |
| `Exact matches only` | QuPath | **yes** | global |
| Which classes are checked (your rules) | QuPath | **no** | global for the session |
| `Any` / `All` | the panel | yes | the panel |
| `List` scope | the panel | yes | the panel |
| `Auto-refresh counts` | the panel | yes | the panel |
| Divider position between the two lists | the panel | yes | separately for the wide and narrow layouts |
| `Active rules` expanded or collapsed | the panel | yes | the panel |
| `Include classes with no objects here` | the panel | yes | the panel |
| Sort column and direction, per list | the panel | **no** | survives docking and undocking within a session, but not a restart |
| `Find` text | -- | **no**, deliberately | -- |
| The panel window's position and size | the panel | **yes** | one window, whichever image or project |
| Whether the panel was docked or floating | the panel | **no** -- it always reopens as a window | -- |
| Whether the panel was open at all | -- | **no** -- it always starts closed | -- |

There is one more, with no control anywhere in the panel: the **80% threshold** at which a
component's spread ratio is shown in bold is a saved preference
(`classvisibility.coverageThreshold`). Nothing in the interface changes it. If two machines
ever show you different rows in bold, that setting having been retuned on one of them is the
reason.

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
`Restore visibility state...` -- on the toolbar button's right-click menu and in
**Extensions > Class visibility** -- also covers the causes that are
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
Toolbar insertion is best-effort, and nothing depends on it. **Extensions > Class visibility**
carries every item the button's menu does -- `Show panel`, `Restore visibility state...`,
`Save visibility state`, `Reset all visibility` and `Help` -- so the recovery routes are still
there. What you lose is the at-a-glance signal: the button's slashed eye and its tooltip are
the quickest way to see whether any class rule is in force, and with no button you have to open
the panel and read the status strip instead. If the button is missing on your platform, that is worth
an issue -- it is not expected.

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

**Something else.**
File an issue on the repository's issue tracker, saying which platform you are on and which
QuPath version you are running. No platform has been verified yet, so a platform-specific
report is useful rather than redundant.

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
a script and the panel are editing the same set, and each sees what the other did. Open the
panel after running a script and your rules are listed in `Active rules`, with the source
`Set elsewhere`.

Two things to know before you script it:

- The matching rules are the ones described in
  [What a checked class row acts on](#what-a-checked-class-row-acts-on): adding `CD3: CD8`
  to that set also affects every class carrying both parts, unless
  `setUseExactSelectedClasses(true)` is on.
- **Adding several classes separately means "any of these", not "all of them together".** For
  an `All` combination, build one composite with `PathClass.fromCollection` as above, rather
  than adding the parts one at a time.

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
