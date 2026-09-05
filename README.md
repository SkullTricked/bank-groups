# Bank Groups (RuneLite plugin)

Highlights bank items with a coloured border + translucent fill, organized
into up to 10 named/coloured groups (e.g. "Melee gear", "Mage gear",
"Potions"). Matching is by canonical item ID, so a group's border follows an
item even when only its **placeholder** remains in the bank.

## How it works

- **Edit mode** (toggled from the side panel) selects one of the 10 groups
  as "active".
- While edit mode is on, **left-clicking an item in the bank toggles it
  in/out of the active group** instead of withdrawing it. All normal bank
  click actions are suppressed on that widget while edit mode is active, so
  you can't accidentally withdraw something.
- Groups are **mutually exclusive** — adding an item to a group silently
  removes it from any other group it was in, so borders never overlap.
- Group name/colour/membership persist **per RuneLite profile** via
  `ConfigManager#setRSProfileConfiguration`.
- The overlay uses `WidgetItemOverlay`, which re-resolves each item's
  on-screen bounds every frame, so borders track items automatically if the
  bank layout shifts.

## Importing into IntelliJ (Gradle)

This now uses Gradle, matching RuneLite's own official plugin template —
more reliably resolves `latest.release` than plain Maven does against
their repo.

1. Install **JDK 11** and make sure IntelliJ has it configured as an SDK.
2. Open IntelliJ → **File → Open** → select this `bank-groups-plugin`
   folder (the one containing `build.gradle`).
3. IntelliJ will detect `build.gradle` and prompt to import as a Gradle
   project — accept it.
   - There's no `gradlew` wrapper checked into this project. If IntelliJ's
     import dialog asks which Gradle to use, choose **"Use Gradle from:
     'idea' internal build/gradle distribution"** (bundled with IntelliJ,
     no extra download) rather than a wrapper — that's the simplest path
     and needs no separate Gradle install.
   - If you'd rather have a `gradlew` wrapper checked in (useful for CI or
     building outside IntelliJ), open a terminal in this folder once
     Gradle is working and run `gradle wrapper`.
4. Let the import finish — it downloads `net.runelite:client` and friends
   from `https://repo.runelite.net` and Maven Central. Watch the "Build"
   panel at the bottom for errors the way you did with Maven.
5. **File → Project Structure → Project** → set the Project SDK to JDK 11
   if it isn't already.

## Running it against the real client

`src/test/java/com/bankgroups/BankGroupsPluginTest.java` is the dev-loop
entry point RuneLite plugins conventionally use — it's not a real test,
just a `main()` that boots the actual client with this plugin side-loaded
via `ExternalPluginManager.loadBuiltin(...)`.

To run it:
1. Right-click `BankGroupsPluginTest.java` → **Run**.
2. Open its Run/Debug configuration and add `-ea` to VM options (Modify
   options → Add VM options).
3. Run it — the real RuneLite client should boot with "Bank Groups"
   already present in the plugin panel. Open a bank in-game and toggle
   edit mode on from the side panel to test.

## Things to double check when you build

RuneLite's internal API shifts over time; these are the pieces most likely
to need a small tweak depending on the exact client version Gradle
resolves — none should require restructuring the plugin, just possibly a
different constant/method name:

- **`WidgetInfo.BANK_ITEM_CONTAINER`** — some newer client versions have
  migrated parts of the widget-ID system to `ComponentID`/`InterfaceID`
  constants. If `WidgetInfo.BANK_ITEM_CONTAINER` doesn't resolve, search the
  `net.runelite.api.widgets` / `net.runelite.api.gameval` packages in the
  version you pulled for the bank container's replacement constant.
- **`ConfigManager#getRSProfileConfiguration` / `setRSProfileConfiguration`**
  — these are the per-profile config accessors; if your resolved client
  version predates them, fall back to `getConfiguration`/`setConfiguration`
  (which is not profile-scoped) or check `ConfigManager`'s current method
  list.
- **Menu entry mutation** (`MenuEntry#setOption`, `client.setMenuEntries`) —
  stable for a long time, but if the array replacement pattern changes,
  the fallback is simpler: skip relabeling in `onMenuEntryAdded` entirely
  (cosmetic only) — the actual blocking + toggle logic in
  `onMenuOptionClicked` doesn't depend on it.

Everything else (`WidgetItemOverlay`, `ItemManager#canonicalize`,
`NavigationButton`, `Plugin`/`@PluginDescriptor`, Guice injection) has been
stable across RuneLite versions for years.

## File overview

| File | Purpose |
|---|---|
| `build.gradle` / `settings.gradle` | Gradle build config |
| `BankGroupsPlugin.java` | Core plugin: state, menu interception, persistence |
| `BankGroupsConfig.java` | Fill opacity / border width settings |
| `BankGroupsOverlay.java` | Draws the border + fill over matching bank items |
| `BankGroupsPanel.java` | Side panel: edit-mode toggle, group name/colour/select rows |
| `GroupData.java` | Serializable group model (id, name, colour, item IDs) |
| `src/test/.../BankGroupsPluginTest.java` | Dev-loop launcher, boots the real client with this plugin loaded |
| `runelite-plugin.properties` | Only relevant if you later submit to the official Plugin Hub |
