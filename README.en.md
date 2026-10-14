# uxmFarmerOtoEritme

[Türkçe](README.md)

![Java 21+](https://img.shields.io/badge/Java-21%2B-f59e0b)
![Paper API 1.21.1](https://img.shields.io/badge/Paper_API-1.21.1-38bdf8)
[![GPL-3.0](https://img.shields.io/badge/License-GPL--3.0-22c55e)](LICENSE)

A Paper plugin that automatically smelts uncooked metal ores already in Farmer storage and deposits the output back into the same Farmer. A uxmFarmer-style menu shows stock, capacity, and active jobs, with manual smelting available too. Farmer classes are not used at compile time; event and API links are established at runtime through reflection.

![Timed raw iron smelting flow](assets/smelting-flow.gif)

*Illustrative workflow animation, not gameplay footage. The animation is accelerated; the default operation takes 10 seconds.*

## Contents

- [Requirements and installation](#requirements-and-installation)
- [How it works](#how-it-works)
- [Farmer-style menu](#farmer-style-menu)
- [Configuration](#configuration)
- [Compatibility and event contracts](#compatibility-and-event-contracts)
- [Commands and permissions](#commands-and-permissions)
- [Troubleshooting](#troubleshooting)

## Requirements and installation

- Java 21 or newer is required on the server; Maven 3 is additionally required to build from source.
- The build target is Paper API 1.21.1; this is not a promise of runtime support for other Paper versions.
- Run `mvn clean verify`, then copy `target/uxmFarmerOtoEritme.jar` to your server's `plugins/` directory. The config is created on first startup.
- No paid or proprietary Farmer JAR is required to compile. Third-party Farmer files are not included; the corresponding Farmer plugin must be installed on the running server.
- See [LICENSE](LICENSE) for the GPL-3.0-only license.

## How it works

With the default `automatic-smelting.enabled: true`, Farmer storage is scanned regularly. A timed job starts automatically when sufficient uncooked ore is present; clicking or keeping a menu open is not required. Automatic processing also works with the GUI disabled. Recipes are considered in rotation so continuous iron production cannot starve other ores.

Raw, normal, and deepslate iron/gold/copper variants, plus Nether gold ore, are supported. Only metal-ore `plugin` recipes configured under `items` are automated; custom command recipes never dispatch automatic reward commands.

Manual smelting remains available through the `gui.slot` button in the Farmer main menu. Automatic and manual jobs cannot overlap on the same Farmer, and one player cannot start two manual jobs. Manual jobs display a boss bar. Closing the menu keeps the job running; quitting cancels only that player's manual job.

Manual jobs recheck ownership/membership, `uxmfarmerotosmelter.use`, `gui.permission`, and the recipe permission at start and completion. Automatic processing is enabled by the server setting; default recipes work while the owner is offline. A recipe requiring an extra permission is automated only while its owner is online and has that permission.

The default recipe converts raw iron (`RAW_IRON`) to iron ingots. For example, with 3 raw iron in storage, after 10 seconds 3 raw iron are debited from the same Farmer and its `IRON_INGOT` stock increases by 3. **No items are given to the player's inventory.** Resources are not consumed during the countdown. `RAW_IRON` and `IRON_ORE` stock are separate.

Iron ores become `IRON_INGOT`, gold ores `GOLD_INGOT`, and copper ores `COPPER_INGOT` in the same storage. The source must already be in Farmer storage; this plugin does not collect dropped items. The output material must be defined in Farmer's `collected-materials.yml`. If destination capacity is insufficient, the operation stops without consuming the source.

## Farmer-style menu

The default menu has 6 rows: a gray-glass frame, 28 central product slots, and separate controls. Product lore shows source stock, output stock/capacity, batch amount, and duration. The menu displays automatic processing status and active-job progress. Back returns to the Farmer main menu; pagination and refresh controls do not collide with product slots.

Stock and progress values refresh when opening the menu or clicking **Refresh**. Product names follow the Minecraft client's language.

Use `gui.title` / `gui.filler-material` to configure the title and filler. New defaults follow uxmFarmer's MiniMessage colors; existing custom text using `&` color codes is also supported.

## Configuration

The config is created at `plugins/uxmFarmerOtoEritme/config.yml`; packaged defaults are in [config.yml](src/main/resources/config.yml).

- Keys under `items` are Bukkit `Material` names (case-insensitive).
- The default `mode: plugin` debits the source and stores `output-material` in **the same Farmer**, without executing player reward commands. If omitted, recognized materials use their default smelting mapping; invalid recipes are logged and skipped. The optional `mode: commands` debits the source and executes a custom `commands` list from the console.
- `min-amount`, `max-amount` (`0` means unlimited), `duration-seconds`, and `batch-size` can be set per item. `batch-size: 0` means the entire suitable stock.
- `automatic-smelting.enabled` controls storage-based automatic smelting; `interval-ticks` is the scan interval (default: 20 ticks).
- Command placeholders are `%player%`, `%uuid%`, `%owner%`, `%item%`, `%amount%`, `%farmer_uuid%`, and their brace-wrapped equivalents.
- `gui.rows` controls the smelting menu size; the farmer main menu does not need to have the same size. `gui.slot` is the button position in the farmer main menu; an occupied active button is never overwritten. The default slot is `7`, a filler slot in UXMFarmer 3.18's main menu.
- GUI size is constrained to 3–6 rows; the default 6-row layout shows 28 products. Slot and occupied-inventory safety checks remain in place.

On startup or reload, an old config is upgraded to `config-version: 3`. The previous bundled `RAW_IRON`, `IRON_ORE`, and `GOLD_ORE` inventory reward recipes are converted to storage-based `plugin` recipes while retaining amounts, durations, and permissions. New ore variants are added only if missing. Custom recipes/text are retained; original default recipes intentionally removed in earlier versions are not re-added.

`trigger: automatic` enables the old collection-event/command hook; **it does not control storage-based automatic smelting**. The new processing loop uses `automatic-smelting.enabled`. If Farmer loads late, the processing and GUI connections are retried automatically.

A fragment of the default raw iron recipe:

```yaml
trigger: click
automatic-smelting:
  enabled: true
  interval-ticks: 20
gui:
  title: '<black><bold>Oto Eritme'
  filler-material: GRAY_STAINED_GLASS_PANE
  rows: 6
  slot: 7
  duration-seconds: 10
  batch-size: 64
items:
  RAW_IRON:
    mode: plugin
    output-material: IRON_INGOT
    min-amount: 1
```

## Compatibility and event contracts

The GUI hook automatically looks for UXMFarmer 3.18's `com.uxplima.uxmfarmer.api.event.impl.FarmerOpenMainMenuEvent` or legacy Farmer v6's `xyz.geik.farmer.api.handlers.FarmerMainGuiOpenEvent`. The UXMFarmer 3.18 plugin name is `uxmFarmer`; the legacy `Farmer` name in older configs is also supported.

Storage-based automatic smelting uses modern UXMFarmer 3.18's storage/enumeration API. Legacy Farmer versions without that API explicitly report automatic processing as unavailable; the manual menu integration remains supported.

`hook.plugin-name` selects the dependency; `hook.event-class` selects the **collection event for automatic mode**, not the GUI opening event. Check event and storage API compatibility separately when using another fork.

Folia is not supported. A live server running Paper 26.2 or Java 25 has not been verified; targeting Java 21 does not by itself guarantee server compatibility.

## Commands and permissions

- `/uxmfarmerotosmelter reload` — reloads the config; `/otoeritme` and `farmerotosmelter` are aliases.
- `/otoeritme status` — reports the Farmer connection, menu state, and automatic smelting status.
- `uxmfarmerotosmelter.use` — start smelting from the GUI (default: op).
- `uxmfarmerotosmelter.admin` — reload and administration (default: op).

Reload or plugin shutdown closes open smelting menus; all active jobs are cancelled without consuming resources. Normal menu closing does not cancel a job. The connection is retried if Farmer loads late; quitting cancels only the player's manual job.

## Troubleshooting

| Symptom | Check |
| --- | --- |
| Auto-smelting button is missing | Check `/otoeritme status`, `gui.enabled`, and the startup log line `Farmer GUI oto eritme düğmesi aktif`. |
| GUI connection is active, but no button appears | `gui.slot` must be empty or a filler slot in the farmer main menu; active buttons are not overwritten. |
| A player cannot start smelting | Check farmer ownership/membership, `uxmfarmerotosmelter.use`, `gui.permission`, and the recipe's additional permission. |
| A recipe is missing from the menu | Check the Material name, `output-material` or command list, and skipped-recipe warnings in the log. |
| Smelting does not complete | Check source stock, the output material's Farmer configuration, destination capacity, and access permissions at completion. |
| Stored ores are not processed automatically | Check `automatic-smelting.enabled`, `/otoeritme status`, the recipe's `plugin` mode, and output capacity. An extra-permission recipe requires its owner to be online. |
| A custom recipe's command fails, but stock was debited | Command side effects cannot be rolled back; the source is deliberately not refunded. Check the console command and failure log. Default storage-based recipes do not execute commands. |

If Farmer is disabled by a license, database, or other startup error, fix Farmer's own startup error first. Include the server, Java, and Farmer versions, `/otoeritme status`, and the relevant console error when reporting a problem.
