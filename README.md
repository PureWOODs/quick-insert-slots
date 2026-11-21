# Device: Quick Insert Slots

A Shortcut Buddy for Bitwig, built on the BML mod kit: a grid of slots, each holding something you
can insert into a track — a Bitwig device, a preset, a whole chain, or a plug-in. Insert it with a
click in the panel, a right-click in the device menu, a key you bind, or the Commander.

There is no Bitwig device to put on a track and nothing to route: the slots live in the mod, and the
panel is where you see them all.

## Requirements

- Bitwig Studio **6.1.4** (Windows x64; the build is against 6.1.4).
- The BML loader (Patchbay) **0.36.0-dev.13** or newer.
- These frameworks, installed as mods next to this one:
  - `action-menu-composition-framework` **0.7.0-dev.3** or newer
  - `panel-management-framework` **0.1.0-dev.3** or newer
  - `preferences-composition-framework` **1.0.0** or newer
  - `notifications-framework` **0.3.0-dev.1** or newer
  - (Panel management needs the Settings framework too, so both have to be present. The
    notifications framework is what shows "inserted" and "could not insert".)

## Install

1. Unzip `quick-insert-slots` into the loader's `mods` folder, next to the frameworks.
2. Start Bitwig. The panel is offered as **Quick Insert** by whichever panel layout mod you use
   (Framework: Panels, or Panel: Streamlined Views with its view switcher).
3. Settings → **Mods** → **Quick Insert** holds four options: how many slots are on show (4, 8, 16,
   32, 48 or 64), whether a click fills every selected track, whether using a slot says so, and
   whether a right-click clears one.

## Filling a slot

Right-click anything in Bitwig's browser — a device, a preset, a plug-in, a sample — and pick
**Add to Next Empty Slot**, or **Quick Insert ▸ Save to Slot NN** to choose the slot yourself. What gets stored is the file
behind the browser item, so the slot keeps working when the browser is closed or the item is filtered
away.

Two things worth knowing:

- **Built-in Bitwig devices are files too** (`…\Library\devices\*.bwdevice`), so they can sit in a
  slot like anything else.
- **A VST3 that is a bundle folder is stored by its module**, not by the folder: Bitwig cannot load
  a folder, and asking it to gives *could not read metadata … LoadLibrary … Error code: 5*. This mod
  picks `Contents\<arch>\<name>.vst3` on its own.

## Using a slot

| Where | What it does |
|---|---|
| The **Quick Insert** panel | Click a cell. The panel fills the space it is given, so cells grow and shrink with its size and with the number of slots. |
| Right-click a **device** | `Quick Insert ▸ Slot NN` — inserts through Bitwig's own drop: an instrument at the top of the chain, an effect at the end. |
| **Settings → Shortcuts** | Bind any `Slot NN` to a key. A slot is named by its number alone — Bitwig reads an action name once, at launch, so a name in a menu would be the one the slot had at the start. The panel shows what is in each slot, live. Without a device in context the slot goes into the selected tracks (see below). |
| The **Commander** | Search `Slot NN`. |

How many slots a menu shows follows the setting, live: it is asked for each time Bitwig opens a menu,
so changing it needs no restart. Every slot keeps its settings and its key whatever number is on
show, so fewer slots now means the rest come back unchanged.

### Where a slot goes

Everything goes through Bitwig's own drop: an instrument lands at the top of the chain, an effect at
the end of it, and a chain or preset is merged the way dropping it on the device panel merges it.
Placing an effect yourself, right after the device the track has selected, was built and then
dropped again: Bitwig's own choice kept beating it, and an index taken from a selected device could
put an instrument among the effects.

**Which track it goes into.** A click in the panel, a key, or the Commander without a device in
context fills the first selected track with a device chain. Turn on **Settings → Mods → Quick
Insert → Insert into all selected tracks** and it fills every selected track instead — one click for
a whole selection of tracks. Each track is its own insertion, so Bitwig's undo takes them back one
at a time. Right-clicking a device always means that device's own chain, whatever the setting says.

**What the notice says.** Nothing is reported until Bitwig has actually taken the drop: the
insertion reports its own outcome, so a plug-in that fails to load (a licence prompt, a missing
dependency) says *could not insert …* instead of a "inserted" that never happened. The report is
the insertion's own listener — `lBQ`, not the bare `oRV` the method's signature suggests: Bitwig
casts the listener it is given to `lBQ` inside, and a plain `oRV` throws there and nothing is
inserted. Turn the notices off under **Settings → Mods → Quick Insert → Show a notice when a slot
is used**; the mod log keeps every line either way.

### Clearing a slot

A slot is emptied where it is: a **right-click on its cell** in the panel, or **Clear Slot NN** in the
browser's Quick Insert menu (that entry only appears for a slot with something in it). Both take
effect at once, without a restart. Right-click clearing can be turned off under **Settings → Mods →
Quick Insert → Right-click clears a slot** for anyone who would rather not have it a click away. Lowering **Slots on show** hides the extra slots instead of emptying
them: their settings, their keys and their content stay, and they come back unchanged.

### Sharing a key with another action

Bitwig refuses to bind a key that another action already uses. [Shortcuts: Share a
Key](https://github.com/shaboogen-dev/Mods) adds an **Add** button to the shortcut dialog for that:
it puts the key on one more action, and Bitwig then runs all of them, starting at whatever has the
focus. This mod needs nothing for that — its actions are ordinary actions, so Share a Key puts a slot
on a key you already use.

## Where the slots are stored

`%LOCALAPPDATA%\BML\mods\quick-insert-slots\settings.properties`, two keys per slot plus the
settings:

```properties
slot.4.path=C:/Program Files/Common Files/VST3/FabFilter/FabFilter Pro-Q 4.vst3
slot.4.path.alt=…             only when the browser named a bundle around the module
slot.4.name=Pro-Q 4           what the panel calls it
slots.count=8                 how many slots are on show
insert.all.selected=false     fill every selected track
notify.enabled=true           say so when a slot is used
clear.right.click=true        a right-click on a cell empties it
```

Edit it with Bitwig closed, or set slots from the browser and the rest from Settings.

## When something does not work

- **Nothing in the menus, no `Slot NN` in Shortcuts** — one of the frameworks is missing or too old,
  or the mod did not load. The loader's log says why: `sessions\<time>\bml.log` in the BML folder.
- **An error box about metadata or `LoadLibrary … Error code: 5`** — a slot points at a `.vst3`
  folder. This version repairs that on read; if you still see it, the slot points at something else
  that Bitwig cannot open.
- **A slot does nothing** — the file behind it moved or was uninstalled. A toast names the slot
  and says *is missing*; re-save it from the browser. When Bitwig refuses what the slot holds, the
  toast says so too, and the panel keeps showing the file name.

## Limits

- One target track: the first selected track (or the chain of the device the menu was opened on).
- No drag and drop reordering of slots; re-save into a slot to change it.
- The panel fills its space rather than scrolling: with 64 slots in a short panel the cells get
  small. Use fewer slots on show, or give the panel more height.
