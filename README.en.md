# uxmFarmerOtoEritme

[Türkçe](README.md)

![Java 21+](https://img.shields.io/badge/Java-21%2B-f59e0b)
![Paper API 1.21.1](https://img.shields.io/badge/Paper_API-1.21.1-38bdf8)
[![GPL-3.0](https://img.shields.io/badge/License-GPL--3.0-22c55e)](LICENSE)

A Paper plugin that adds a click-driven, timed auto-smelting button to the UXMFarmer main farmer menu. Farmer classes are not used at compile time; event and API links are established at runtime through reflection.

![Timed raw iron smelting flow](assets/smelting-flow.gif)

*Illustrative workflow animation, not gameplay footage. The animation is accelerated; the default operation takes 10 seconds.*

## Contents

- [Requirements and installation](#requirements-and-installation)
- [How it works](#how-it-works)
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

With the default `trigger: click`, a collection event does not start auto-smelting. The player clicks the `gui.slot` button in the farmer main menu, then chooses an item to smelt. Each operation displays a boss bar for `gui.duration-seconds`. Closing the menu does not stop it; quitting cancels it. A second operation cannot start for the same farmer or player.

The owner/member relationship and permissions are checked both when an operation starts and immediately before completion. `uxmfarmerotosmelter.use` and `gui.permission` are required for the GUI; administration requires `uxmfarmerotosmelter.admin`. An item rule may require an additional `permission`.

The default recipe converts raw iron (`RAW_IRON`) to iron ingots. For example, with 3 raw iron in storage, after 10 seconds 3 raw iron are debited from the same Farmer and its `IRON_INGOT` stock increases by 3. **No items are given to the player's inventory.** Resources are not consumed during the countdown. `RAW_IRON` and `IRON_ORE` stock are separate.

Iron ore also becomes `IRON_INGOT`, and gold ore becomes `GOLD_INGOT`, in the same storage. The output material must be defined in Farmer's `collected-materials.yml`. If destination capacity is insufficient, the operation stops without consuming the source.

## Configuration

The config is created at `plugins/uxmFarmerOtoEritme/config.yml`; packaged defaults are in [config.yml](src/main/resources/config.yml).

- Keys under `items` are Bukkit `Material` names (case-insensitive).
- The default `mode: plugin` debits the source and stores `output-material` in **the same Farmer**, without executing player reward commands. If omitted, recognized materials use their default smelting mapping; invalid recipes are logged and skipped. The optional `mode: commands` debits the source and executes a custom `commands` list from the console.
- `min-amount`, `max-amount` (`0` means unlimited), `duration-seconds`, and `batch-size` can be set per item. `batch-size: 0` means the entire suitable stock.
- Command placeholders are `%player%`, `%uuid%`, `%owner%`, `%item%`, `%amount%`, `%farmer_uuid%`, and their brace-wrapped equivalents.
- `gui.rows` controls the smelting menu size; the farmer main menu does not need to have the same size. `gui.slot` is the button position in the farmer main menu; an occupied active button is never overwritten. The default slot is `7`, a filler slot in UXMFarmer 3.18's main menu.
- GUI size is constrained to 1–6 rows, with slot and occupied-inventory safety checks.

On startup or reload, an old config is upgraded to `config-version: 2`. The previous bundled `RAW_IRON`, `IRON_ORE`, and `GOLD_ORE` inventory reward recipes are converted to storage-based `plugin` recipes while retaining amounts, durations, and permissions. Custom command recipes are left unchanged. A recipe intentionally removed in version 1 is not re-added; older configurations receive a missing raw iron recipe. `enabled`, `trigger`, `hook.plugin-name`, `hook.event-class`, and event priority are configurable.

`trigger: automatic` only enables the legacy collection-event hook; `click` is the recommended and default mode for new installations. If Farmer loads late during server startup, the hook and GUI connection are retried automatically.

A fragment of the default raw iron recipe:

```yaml
trigger: click
gui:
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

`hook.plugin-name` selects the dependency; `hook.event-class` selects the **collection event for automatic mode**, not the GUI opening event. Check event and storage API compatibility separately when using another fork.

Folia is not supported. A live server running Paper 26.2 or Java 25 has not been verified; targeting Java 21 does not by itself guarantee server compatibility.

## Commands and permissions

- `/uxmfarmerotosmelter reload` — reloads the config; `/otoeritme` and `farmerotosmelter` are aliases.
- `/otoeritme status` — reports whether the Farmer GUI connection is active, disabled, or waiting for a connection.
- `uxmfarmerotosmelter.use` — start smelting from the GUI (default: op).
- `uxmfarmerotosmelter.admin` — reload and administration (default: op).

Reload or plugin shutdown closes open smelting menus; active operations are cancelled without consuming resources. Normal menu closing does not cancel an operation. The connection is retried if Farmer loads late; quitting cancels the player's operation.

## Troubleshooting

| Symptom | Check |
| --- | --- |
| Auto-smelting button is missing | Check `/otoeritme status`, `gui.enabled`, and the startup log line `Farmer GUI oto eritme düğmesi aktif`. |
| GUI connection is active, but no button appears | `gui.slot` must be empty or a filler slot in the farmer main menu; active buttons are not overwritten. |
| A player cannot start smelting | Check farmer ownership/membership, `uxmfarmerotosmelter.use`, `gui.permission`, and the recipe's additional permission. |
| A recipe is missing from the menu | Check the Material name, `output-material` or command list, and skipped-recipe warnings in the log. |
| Smelting does not complete | Check source stock, the output material's Farmer configuration, destination capacity, and access permissions at completion. |
| A custom recipe's command fails, but stock was debited | Command side effects cannot be rolled back; the source is deliberately not refunded. Check the console command and failure log. Default storage-based recipes do not execute commands. |

If Farmer is disabled by a license, database, or other startup error, fix Farmer's own startup error first. Include the server, Java, and Farmer versions, `/otoeritme status`, and the relevant console error when reporting a problem.
