# Building Quick Insert Slots

This mod has no hooks: it only registers actions, a panel and a settings section, so it needs
nothing but a compiler and the framework jars it already depends on.

## What you need

| | |
|---|---|
| JDK | 25 (the release target is 21: `javac --release 21`) |
| `bitwig.jar`, `libs.jar` | from the Bitwig Studio install, `bin\` |
| `bml-modkit.jar`, `bml-api.jar` | from the BML (Patchbay) install, `modkit\` |
| Framework jars | `helper.jar` of each framework in `requires` |

The mod is built against Bitwig **6.1.4** on Windows x64 and uses that version's obfuscated names.
On another version the names have to be mapped again (see the BML docs, `docs\maps.md`): nothing in
`src\` will compile against a different `bitwig.jar` until then.

## Set two variables first

Every command below uses `$BITWIG` (the Bitwig Studio install) and `$BML` (the BML/Patchbay
install). Replace these example paths with your own, in the shell you build from:

```powershell
$BITWIG = 'C:\Program Files\Bitwig Studio'
$BML    = 'C:\Bitwig mod\BML-0.36.0-dev.13-windows'
```

`javac` and `java` must be the JDK 25 ones. If they are not on `PATH`, call them by full path, for
example `& 'C:\Program Files\Java\jdk-25.0.2\bin\javac.exe'`.

## Three steps

```powershell
# 1. Once: a jar javac can compile against (strips method bodies, makes everything public).
java -jar "$BML\modkit\bml-modkit.jar" compile-jar "$BITWIG\bin\bitwig.jar" compile.jar

# 2. Compile.
javac --release 21 -proc:none -encoding UTF-8 -implicit:none `
      -sourcepath src `
      -cp "compile.jar;$BITWIG\bin\libs.jar;$BML\modkit\bml-api.jar;$DEPS" `
      -d build\classes (Get-ChildItem -Recurse -Filter *.java src).FullName

# 3. Finish (rewrites the obfuscated names back and writes the package) and pack.
java -jar "$BML\modkit\bml-modkit.jar" finish build\classes build\hooks.json `
     "$BITWIG\bin\bitwig.jar" "$BITWIG\bin\libs.jar" $DEP_JARS
# expect: MODKIT_FINISH_OK hooks=0 non_public=3 classes_rewritten=6

jar --create --file build\helper.jar -C build\classes .
```

Then copy `mod.json`, `helper.jar`, the three `*.properties` files, `README.md`, `resources\` and
`slots\` into `<BML>\mods\quick-insert-slots\`, with Bitwig closed.

`$DEPS` is the `-cp` list of the four frameworks' `helper.jar`, `$DEP_JARS` the same four jars as
plain arguments:

- `action-menu-composition-framework`
- `panel-management-framework`
- `preferences-composition-framework`
- `notifications-framework`

## Checking a build without starting Bitwig

```powershell
& "$BML\loader\prepare-mod.cmd" "$BITWIG\bin" <a folder holding this mod and the four frameworks>
# expect: PREPARE_OK ... mods=5
```

While Bitwig is running this fails with a `helper.jar` file-in-use error: close Bitwig first.

## Packaging for a release

A package that Patchbay can import is a zip whose single top-level folder is named after the `mod.id`
(`quick-insert-slots`), with `mod.json`, `helper.jar`, the three properties files, `README.md`,
`resources/` and `slots/` inside it. Entry names must use forward slashes: a zip written by Windows
PowerShell 5.1's `Compress-Archive` uses backslashes and is rejected with
`it has an unsafe path`.

## Notes

- `hooks: []` in `mod.json` is deliberate: the loader still translates the helper jar's Bitwig
  names, but no method of Bitwig's is patched.
- The panel view id (`quick_insert_slots_view`) and the action ids (`quick_insert_slots_NN`) are
  part of a user's saved layout and shortcuts. Changing them loses both.
- The insertion listener must be an `lBQ`, not the `oRV` the method signature suggests: Bitwig
  casts it to `lBQ` internally and a plain `oRV` throws there.
