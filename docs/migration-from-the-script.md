# Coming from the "Show specific classes of objects" script

If you found this extension from
[image.sc topic 31828](https://forum.image.sc/t/qupath-script-ui-for-class-visibility-selection/31828)
or from [the gist](https://gist.github.com/Svidro/e00021dff92ea1173e535008854be72e), this
page is for you. It covers three things: why the script stopped working, what QuPath now
does on its own, and what changed in the behaviour you were used to.

---

## The script does not run on QuPath 0.7

It is not deprecated. It is **removed**.

The script drives visibility through
`getCurrentViewer().getOverlayOptions().hiddenClassesProperty()`. That property no longer
exists anywhere in QuPath 0.7 -- the visibility model was rebuilt around a *selected* class
set with an explicit mode, rather than a *hidden* class list. Every version of the script
that circulated calls it, so every version fails at the first line that touches the viewer.
There is no small edit that fixes this; the mechanism it was written against is gone.

---

## Check whether you still need an extension at all

QuPath 0.7 absorbed most of what the script did. This extension is aimed squarely at highly
multiplexed data -- twenty or thirty derived classes -- and if that is not your situation you
very likely do not need it.

Before installing anything, work through
[Do you need this?](../README.md#do-you-need-this) in the README. It names where QuPath's own
class list is, everything it now does for you, and the point past which this panel starts
earning its screen space. If per-class show and hide is all you were using the script for,
you are done there -- you do not need this extension.

The one thing to note on the way past, because it is the change most likely to alter a result
you previously got: **the matching rule is QuPath's now**, and it is a good one. Selecting
`CD3` matches `CD3`, `CD3: CD8` and `CD8: CD3`, but never `CD31`.

What this extension adds on top, all of it a consequence of working at twenty to forty
combinatorial classes:

- a **list of components**, so you can act on `CD3` without hunting through every class that
  contains it;
- **combining several components with `All`** -- "only the cells that are CD3 *and* CD4" --
  which no interface in QuPath currently exposes, although the underlying engine supports it;
- the classes **actually present in the current image, with counts**, rather than the
  project's list of available classes. (The only built-in way to reconcile the two is
  "Populate from image", which *changes* your project's class list. This panel never does.)
- a `Find` field that filters the **component** list, which the built-in pane has no
  equivalent of. Over class names its own filter is the better one -- it accepts regular
  expressions and this panel's does not.

---

## What changed in the behaviour you were used to

One thing is simply different, and you will hit it in the first ten seconds. The rest were
bugs rather than features; if you built habits around them, these are the ones you will
notice.

### A ticked box means the opposite of what it meant in the script

**In the script, ticking a box SHOWED that class. Here, by default, ticking a box HIDES it.**

The script's first act on opening was to add every class to the hidden list, so its
checkboxes were un-hide toggles: you started from a blank image and ticked your way back to
what you wanted. This panel opens without changing anything, so a tick has to mean the
opposite -- you start from everything visible and tick your way down.

If you want the script's polarity, it is one radio button: **`Show only checked classes`**.
That is the mode where a tick means show, everything else is hidden, and the panel starts
from blank exactly as the script did. The two radios are labelled as full sentences precisely
so this is a choice you can see rather than a convention you have to remember.

One caution if you adopt that mode as a habit, and it is the failure this extension is most
careful about: the mode is saved across a QuPath restart while your ticks are not, so leaving
QuPath in `Show only checked classes` and quitting means the next launch starts with
everything hidden and nothing on screen to say why. See
[If everything disappears](user-guide.md#if-everything-disappears), which covers both how to
get out of it and how to avoid it.

### The script crashed if any object was unclassified

The script collected classes with `collect { it?.getPathClass() }`, which keeps a `null` for
every unclassified object, and then called `.toString()` on it. On any image with a single
unclassified cell, it threw a `NullPointerException` before the window opened.

This also means the script's **"None"** entry -- added specifically to handle unclassified
objects -- never ran even once, because the crash happened before the code that would have
created it. Here, unclassified objects get a working row of their own, with a real count.

### The "None" checkbox did not do anything

In the versions where it appeared, checking "None" called `getPathClass("None")`, which
creates a brand-new class literally named "None" rather than referring to unclassified
objects. It hid nothing, because no object was classified as "None". Fixed: the
`Unclassified` row acts on genuinely unclassified objects.

### Checking `CD3` also caught `CD31`

The script matched with `it.getId().contains(n.getId())` -- a plain substring test on the
class name. `"CD31: CD8".contains("CD3")` is true, so `CD31` objects were swept up silently.
QuPath 0.7 compares whole names instead, so `CD3` matches `CD3`, `CD3: CD8` and `CD8: CD3`,
and never `CD31`. This is the change most likely to alter a result you previously got from
the script.

### Group checkboxes wiped out your individual selections

The script's own comment said so: *"ANY GROUP CLASS CHECKING OR UNCHECKED OVERWRITE ANY
SINGLE CLASS CHANGES"*. Every change to a group checkbox re-applied *all* the group
checkboxes over the individual ones, so a carefully assembled selection could be undone by
checking one group. The two lists here compose instead: class rows and component rows both
contribute, and neither rewrites the other. Where the two would collide on the same class,
the panel shows you which list owns it rather than letting one silently win.

### In the tabbed version, "All" only ever un-hid unclassified objects

The tabbed variant's "All" handler is:

```groovy
classifications.each {
    if (it == null )
    getCurrentViewer().getOverlayOptions().hiddenClassesProperty().remove(it)
}
```

The `if` has no braces, so the `remove` is the whole body of the `if`. Checking "All" un-hid
only the null class; every real class stayed hidden while its checkbox read as checked. The
interface and the viewer disagreed completely, with the interface looking correct.

### The "Reset classifications" button is gone, on purpose

The tabbed variant had a **"Reset classifications"** button next to the visibility
checkboxes. It called `resetDetectionClassifications()`, which does not reset visibility --
it **erases the classifications on your detections**, destroying classifier output from a
button sitting inches below a row of harmless-looking tick boxes. This extension has no
equivalent, and nothing in it changes object data of any kind.

### Other differences worth a line

- **The class list refreshes.** The script harvested classes once, when the window opened,
  and never again -- so a classifier run, or switching image, left you looking at a stale
  list. This panel follows the active image and re-harvests. On very large images you can
  turn that off with `Auto-refresh counts` and recount on demand.
- **There is only ever one panel.** Running the script twice gave you two windows whose
  listeners fought over one set of viewer state. The toolbar button reveals the one panel;
  it never creates a second.
- **It is still a window, and it can become a tab.** The script opened an 800x500 window
  over your image. This panel also opens as a window -- one you can size and place -- but you
  can dock it into QuPath's analysis pane next to Annotations and Hierarchy when you would
  rather it were always visible and out of the way. Nothing appears in your analysis pane
  unless you put it there.
- **The panel no longer hides everything the moment it opens.** The script's first act was
  to add every class to the hidden list, so opening it blanked your image until you checked
  something. This panel opens without changing anything, and the status strip tells you so:
  `[OK] No class rules active -- nothing is hidden by class.`
- **You can get back.** Between `Reset all` in the panel and `Restore visibility state...`
  on the toolbar button's menu, there is always a way out of a state where you cannot see
  anything -- including states the panel did not cause. See
  [If everything disappears](user-guide.md#if-everything-disappears).

---

## Two things the script had that this does not

Both are honest omissions rather than oversights.

- **Exclusions** ("show all CD8-positive classes, but nothing with PDL1"). This was requested
  on the forum thread and never built into the script either. It needs a different mechanism
  from the one the panel uses, with different lifetime and performance consequences, and it
  is **not currently supported**.
- **Configurable separators**, for names like `CD8_positive` rather than `CD8: positive`.
  Version 2 of the script had this for the pre-0.2 multiplex classifier and version 3 dropped
  it deliberately. Composite classifiers use `:`, and QuPath's matching engine splits on `:`
  only, so other separators are **not currently supported**.

---

## Credit

The script and this extension exist because of a long thread of people fixing each other's
work:

- **Research_Associate (Mike Nelson)** wrote the original script and its revisions, including
  the two-column layout this extension is built on.
- **Mark_Zaidi** contributed the fix that kept the script working when QuPath's class API
  changed.
- **EP.Zindy** contributed the tabbed variant, and asked for the analysis-pane placement that
  shaped this extension's design.
- **petebankhead** diagnosed the underlying problem -- `getName()` returns only the last part
  of a derived class -- and designed the order-independent matching rule that QuPath 0.7
  ships and that this extension relies on rather than reimplementing.

Sources:
[image.sc topic 31828](https://forum.image.sc/t/qupath-script-ui-for-class-visibility-selection/31828),
[the gist](https://gist.github.com/Svidro/e00021dff92ea1173e535008854be72e).
