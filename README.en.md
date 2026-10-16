# uxmFarmerOtoEritme

[Türkçe](README.md)

![uxmFarmerOtoEritme — automatic smelting bridge](assets/hero.svg)

[![Latest release](https://img.shields.io/github/v/release/SerhatDew/uxmfarmer-autosmelt?display_name=tag&sort=semver&color=f59e0b)](https://github.com/SerhatDew/uxmfarmer-autosmelt/releases/latest)

[**Download the latest JAR**](https://github.com/SerhatDew/uxmfarmer-autosmelt/releases/latest/download/uxmFarmerOtoEritme.jar) · [Release notes and SHA-256](https://github.com/SerhatDew/uxmfarmer-autosmelt/releases/latest)

![Java 21+](https://img.shields.io/badge/Java-21%2B-f59e0b)
![Paper API 1.21.1](https://img.shields.io/badge/Paper_API-1.21.1-38bdf8)
[![GPL-3.0](https://img.shields.io/badge/License-GPL--3.0-22c55e)](LICENSE)

## At a glance

- **Storage-first:** debits raw ore from Farmer storage and writes ingots back to that same storage; no player-inventory rewards.
- **Permission-gated:** automatic processing requires the Farmer owner's `uxmfarmerotosmelter.auto`; `instant` only skips the countdown and never enables automation by itself.
- **Predictable jobs:** 10-second default duration, manual GUI, recipe-level capacity/permission checks, and runtime reflection for the Farmer bridge.

![Timed raw iron smelting flow](assets/smelting-flow.gif)

*Illustrative workflow animation, not gameplay footage. It shows a Farmer with automatic permission but without instant permission. The animation is accelerated; the default operation takes 10 seconds.*

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
- For a ready-to-use JAR, use the [latest release](https://github.com/SerhatDew/uxmfarmer-autosmelt/releases/latest); do not install the **Source code** archive on the server.
- The build target is Paper API 1.21.1; this is not a promise of runtime support for other Paper versions.
- Run `mvn clean verify` at the repository root, then copy `target/uxmFarmerOtoEritme.jar` to your server's `plugins/` directory. The config is created on first startup.
- No paid or proprietary Farmer JAR is required to compile. Third-party Farmer files are not included; the corresponding Farmer plugin must be installed on the running server.
- See [LICENSE](LICENSE) for the GPL-3.0-only license.
Remove the previous JAR for this hook; never run two copies together. PlugMan unloading/reloading is unsupported; prefer a full server restart.

## How it works

With the default `automatic-smelting.enabled: true`, Farmer storage is scanned regularly. When its owner is online and has `uxmfarmerotosmelter.auto`, sufficient uncooked ore starts a job without clicking or keeping a menu open. It also works with the GUI disabled. Recipes are considered in rotation.

Raw, normal, and deepslate iron/gold/copper variants, plus Nether gold ore, are supported. Only metal-ore `plugin` recipes configured under `items` are automated; custom command recipes never dispatch automatic reward commands.

Manual smelting remains available through the `gui.slot` button. Automatic and manual jobs cannot overlap on the same Farmer, and one player cannot start two timed manual jobs. Timed manual jobs display a boss bar. Closing the menu keeps the job running.

Manual jobs check ownership/membership, `uxmfarmerotosmelter.use`, `gui.permission`, and the recipe permission. Automatic jobs check the owner's `automatic-smelting.permission` and any recipe permission at start and completion. Default VIP automation does not run offline; permission loss preserves the source. Quitting cancels manual jobs.

`uxmfarmerotosmelter.instant` skips the countdown for the initiating player in manual mode, or the Farmer owner in automatic mode. It grants neither automatic processing, GUI access, nor recipe permissions; capacity and amount checks remain. Instant automation completes on the first eligible scan, still respecting the scan interval and batch limit.

The default recipe converts raw iron (`RAW_IRON`) to iron ingots. For example, with 3 raw iron in storage, after 10 seconds 3 raw iron are debited from the same Farmer and its `IRON_INGOT` stock increases by 3. **No items are given to the player's inventory.** Resources are not consumed during the countdown. `RAW_IRON` and `IRON_ORE` stock are separate.

Iron ores become `IRON_INGOT`, gold ores `GOLD_INGOT`, and copper ores `COPPER_INGOT` in the same storage. The source must already be in Farmer storage; this plugin does not collect dropped items. The output material must be defined in Farmer's `collected-materials.yml`; missing output records are not created and the operation stops without consuming the source. If destination capacity is insufficient, the operation also stops without consuming the source.

## Farmer-style menu

The default menu has 6 rows: a gray-glass frame, 28 central product slots, and separate controls. Product lore shows source stock, output stock/capacity, batch amount, and duration. The menu displays automatic processing status and active-job progress. Back returns to the Farmer main menu; pagination and refresh controls do not collide with product slots.

Stock and progress values refresh when opening the menu or clicking **Refresh**. Product names follow the Minecraft client's language.

Use `gui.title` / `gui.filler-material` to configure the title and filler. New defaults follow uxmFarmer's MiniMessage colors; existing custom text using `&` color codes is also supported.

## Configuration

- The config is created at `plugins/uxmFarmerOtoEritme/config.yml`; packaged defaults are in [config.yml](src/main/resources/config.yml).

- Keys under `items` are Bukkit `Material` names (case-insensitive).
- The default `mode: plugin` debits the source and stores `output-material` in **the same Farmer**, without executing player reward commands. If omitted, recognized materials use their default smelting mapping. Invalid fields, types or recipes fail explicitly; components are not activated with invalid configuration. The optional `mode: commands` debits the source and executes a custom `commands` list from the console.
- `min-amount`, `max-amount` (`0` means unlimited), `duration-seconds`, and `batch-size` can be set per item. `batch-size: 0` means the entire suitable stock.
- `automatic-smelting.enabled` controls storage-based automatic smelting; `interval-ticks` is the scan interval (default: 20 ticks).
- `automatic-smelting.permission` defaults to `uxmfarmerotosmelter.auto`. Set `''` to allow everyone; offline processing is then possible when no additional recipe permission is required.
- `instant-smelting.permission` defaults to `uxmfarmerotosmelter.instant`; `''` disables instant processing.
- Command placeholders are `%player%`, `%uuid%`, `%owner%`, `%item%`, `%amount%`, `%farmer_uuid%`, and their brace-wrapped equivalents.
- `gui.rows` controls the smelting menu size; the farmer main menu does not need to have the same size. `gui.slot` is the button position in the farmer main menu; an occupied active button is never overwritten. The default slot is `7`, a filler slot in UXMFarmer 3.18's main menu.
- GUI size is constrained to 3–6 rows; the default 6-row layout shows 28 products. Slot and occupied-inventory safety checks remain in place.

Startup or reload upgrades old configs to `config-version: 4`. Missing VIP permission fields are added while custom permission values are preserved. Upgrading from v3 does not change recipes. Older versions also migrate bundled inventory rewards to storage recipes and add the v3 ore variants; custom recipes/text are retained.

`config-version` is the schema version only. Configuration is read directly from disk into immutable settings; resource defaults do not restore removed recipes. Existing files are not overwritten on startup; migrations change only the required fields. Product capacities, prices, membership and permission rules come from Farmer's API; the recipes/GUI/permissions here configure bridge behavior only.

`trigger: automatic` enables the old collection-event/command hook; **it does not control storage-based automatic smelting**. The new processing loop uses `automatic-smelting.enabled`. If Farmer loads late, the processing and GUI connections are retried automatically.

A fragment of the default raw iron recipe:

```yaml
trigger: click
automatic-smelting:
  enabled: true
  interval-ticks: 20
  permission: uxmfarmerotosmelter.auto
instant-smelting:
  permission: uxmfarmerotosmelter.instant
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

### Complete settings reference and examples

| Group | Key (default) | Meaning and validation |
| --- | --- | --- |
| Schema | `config-version: 4` | Integer 0–4; schema version only. |
| General | `enabled: true` | Enables/disables this plugin; must be boolean. |
| Legacy event | `trigger: click` | `click` exposes only the GUI button; `automatic` enables the old collection-event/command hook. It does not affect storage scanning. |
| Hook | `hook.plugin-name: uxmFarmer`, `event-class: ...FarmerCollectProductEvent`, `priority: NORMAL` | Dependency and legacy event selection. `priority` accepts only `LOWEST`, `LOW`, `NORMAL`, `HIGH`, `HIGHEST`, or `MONITOR`. This is not the GUI event; keep these values as strings and the section as a section. |
| Automatic | `automatic-smelting.enabled: true`, `interval-ticks: 20`, `permission: uxmfarmerotosmelter.auto` | Storage scan loop; `interval-ticks` is a positive integer (20 ticks = 1 second). `''` disables the automatic permission check; Farmer ownership/membership and recipe permissions still apply. |
| Instant | `instant-smelting.permission: uxmfarmerotosmelter.instant` | `''` disables countdown-free jobs. It never grants automation or GUI access by itself. |
| Default recipe | `defaults.mode: plugin`, `min-amount: 1`, `max-amount: 0`, `consume: false`, `permission: ''`, `commands: []` | Values inherited by recipes. `max-amount: 0` means unlimited. `commands` is meaningful only in `commands` mode and must be a string list. |
| GUI | `enabled: true`, `title: <black><bold>Oto Eritme`, `filler-material: GRAY_STAINED_GLASS_PANE`, `slot: 7`, `rows: 6`, `duration-seconds: 10`, `batch-size: 64`, `permission: uxmfarmerotosmelter.use` | `rows` is 3–6; `slot` must be 0 through `rows*9-1` and also be usable/non-colliding in the Farmer main menu. Duration is 1–86400 seconds, batch is 0 or positive; `gui.progress.bar-length` is 1–50 (default 20). Defaults are `button.material: BLAST_FURNACE`, `button.name: <gold>Oto Eritme`, `button.lore: [<gray>Eritme menüsünü açmak için tıkla]`, and `progress.name: <gold>Eritiliyor`. |
| Recipe | `items.<MATERIAL>` | Bukkit material name (case-insensitive). `plugin` mode requires a valid, different `output-material`; `commands` mode requires a non-empty `commands` list. `duration-seconds`, `batch-size`, `min/max-amount`, `consume`, and `permission` may be overridden per recipe. |

`min-amount` and `max-amount` must be 0 or greater; `max-amount: 0` means unlimited, otherwise `max-amount >= min-amount` is required. Each recipe's `duration-seconds` is 1–86400 and `batch-size` is 0 or positive. If recipe values are absent, duration and batch inherit the GUI values. The scan interval (`interval-ticks`) controls how often the new automatic loop looks; batch size controls how much one scan/job may take, while duration controls completion wait. Sources are not debited during the wait.

**Two same-storage recipe examples:**

```yaml
items:
  RAW_IRON: {mode: plugin, output-material: IRON_INGOT, min-amount: 1, duration-seconds: 10, batch-size: 64}
  GOLD_ORE: {mode: plugin, output-material: GOLD_INGOT, min-amount: 8, max-amount: 64, duration-seconds: 5, batch-size: 16}
```

For a normal manual player, satisfy `gui.permission` and any recipe `permission`; for VIP automation grant `auto`, and grant `instant` separately for countdown-free VIP jobs. LuckPerms example:

```text
lp group player permission set uxmfarmerotosmelter.use true
lp group vip permission set uxmfarmerotosmelter.use true
lp group vip permission set uxmfarmerotosmelter.auto true
lp group vip permission set uxmfarmerotosmelter.instant true
```

Manual processing starts from the GUI button and is cancelled if that player quits. Automatic processing does not require a GUI, but with the defaults the Farmer owner must be online and have `auto`. Manual and automatic jobs cannot overlap on one Farmer. `plugin` mode debits the source from Farmer storage and stores output in that same storage; both materials must be defined in that Farmer's `collected-materials.yml`. A missing output record or insufficient capacity leaves the source untouched. In the modern storage loop, command recipes are not dispatched automatically; in legacy `trigger: automatic` mode, configured `commands` may run from the collection event. Command side effects cannot be rolled back. Do not invent command-mode settings: use only the real `commands` list and supported placeholders.

Farmer licensing, region/membership rules, economy prices, and NPC settings are not this hook's configuration; they come from Farmer's own config and runtime API. A valid license, active Farmer, source/output entries in `collected-materials.yml`, and any region/economy/NPC plugins required by Farmer are prerequisites. This README contains no real license, token, or password. Setting `automatic-smelting.permission: ''` removes the automatic permission check and may allow offline processing; the automatic loop's ownership/membership and recipe-permission checks still apply.

`/otoeritme reload` reloads only this hook's config; it closes open menus and cancels active jobs without consuming sources. Use a full server restart when changing Farmer, Paper, or the plugin JAR; PlugMan reload is unsupported. Migration reads the disk file directly, adds only required fields, preserves existing permissions/recipes, and does not restore recipes that a user removed.
For a manual-only setup, merge this fragment into the existing file (do not replace the entire file):
```yaml
automatic-smelting:
  enabled: false
```
For VIP automation, leave `enabled: true` and grant the owner `uxmfarmerotosmelter.auto`; grant `instant` separately if needed. These fragments do not delete recipes or replace the rest of the defaults.

**Step-by-step check:** (1) fix Farmer license/startup errors; (2) define source and output in `collected-materials.yml`; (3) put source stock in storage for one `plugin` recipe; (4) grant `use` to a normal group and `auto` (plus optional `instant`) to the VIP owner; (5) check `/otoeritme status`, the GUI, and console logs; (6) use `reload` and confirm an open job is cancelled while its source remains. Real Farmer GUI/region transfers and full live loot proof were not verified; the animation is illustrative.

## Compatibility and event contracts

The GUI hook automatically looks for UXMFarmer 3.18's `com.uxplima.uxmfarmer.api.event.impl.FarmerOpenMainMenuEvent` or legacy Farmer v6's `xyz.geik.farmer.api.handlers.FarmerMainGuiOpenEvent`. The UXMFarmer 3.18 plugin name is `uxmFarmer`; the legacy `Farmer` name in older configs is also supported.

Storage-based automatic smelting uses modern UXMFarmer 3.18's storage/enumeration API. Legacy Farmer versions without that API explicitly report automatic processing as unavailable; the manual menu integration remains supported.

`hook.plugin-name` selects the dependency; `hook.event-class` selects the **collection event for automatic mode**, not the GUI opening event. Check event and storage API compatibility separately when using another fork.

Folia is not supported. Leaf/Paper 26.2 with Java 25 was exercised for startup/banner, waiting, status/reload, disabled-config, malformed-config recovery, and shutdown behavior; live Farmer GUI interaction, storage transfers, and region/loot behavior were not verified. Targeting Java 21 does not by itself guarantee server compatibility.

## Commands and permissions

- `/uxmfarmerotosmelter reload` — reloads the config; `/otoeritme` and `farmerotosmelter` are aliases.
- `/otoeritme status` — reports the Farmer connection, menu state, and automatic smelting status.
- `uxmfarmerotosmelter.use` — start smelting from the GUI (default: op).
- `uxmfarmerotosmelter.admin` — reload and administration (default: op).
- `uxmfarmerotosmelter.auto` — automatic processing for the Farmer owner (default: false, including operators).
- `uxmfarmerotosmelter.instant` — skip the countdown (default: false; automatic permission is still separate).

With LuckPerms, explicitly grant these to the VIP group:

```text
lp group vip permission set uxmfarmerotosmelter.use true
lp group vip permission set uxmfarmerotosmelter.auto true
lp group vip permission set uxmfarmerotosmelter.instant true
```

Grant `uxmfarmerotosmelter.use` separately to normal groups that should use timed manual smelting. VIP benefits do not require the `admin` permission.

Reload or plugin shutdown closes open smelting menus; all active jobs are cancelled without consuming resources. Normal menu closing does not cancel a job. The connection is retried if Farmer loads late; quitting cancels only the player's manual job.

The startup/shutdown robot banner uses the actual plugin name and Maven version; platform and elapsed time are obtained at runtime. After `Yapılandırma yüklendi.`, an enabled configuration emits one `Bekleniyor: ...` warning when dependencies are late and one successful ready message once they attach. `enabled: false` emits `Pasif: ...` and never reports successful readiness. Config/API failures use the standard startup-failed state; stopping before readiness and cleanup failures use their separate standard states. Reload/shutdown cleans only this hook's task handles and listener instances; a cleanup failure does not prevent remaining resources from being closed.

## Verification

43 tests passed, covering stock transfers/rollback, automatic and instant VIP permissions, modern/legacy GUI integration, startup-bound event APIs and configuration migration/validation.

An isolated Leaf 26.2 / Java 25 run exercised the packaged JAR's robot banner, missing-Farmer waiting state, `status`/`reload`, `enabled: false`, malformed boolean rejection, recovery and clean shutdown. A standalone fault-injection smoke program confirmed that retry/GUI cleanup failures do not skip remaining listener/task cleanup or report false success. Live-player GUI interaction and world/region transfers with a real Farmer installation were not exercised.


## Troubleshooting

| Symptom | Check |
| --- | --- |
| Auto-smelting button is missing | Check `/otoeritme status`, `gui.enabled`, and the startup log line `Farmer GUI oto eritme düğmesi aktif`. |
| GUI connection is active, but no button appears | `gui.slot` must be empty or a filler slot in the farmer main menu; active buttons are not overwritten. |
| A player cannot start smelting | Check farmer ownership/membership, `uxmfarmerotosmelter.use`, `gui.permission`, and the recipe's additional permission. |
| A recipe is missing from the menu | Check the Material name, `output-material` or command list, and skipped-recipe warnings in the log. |
| Smelting does not complete | Check source stock, the output material's Farmer configuration, destination capacity, and access permissions at completion. |
| Stored ores are not processed automatically | Check `automatic-smelting.enabled`, whether the owner is online with `automatic-smelting.permission`, any recipe permission, `plugin` mode, and destination capacity. |
| A custom recipe's command fails, but stock was debited | Command side effects cannot be rolled back; the source is deliberately not refunded. Check the console command and failure log. Default storage-based recipes do not execute commands. |

If Farmer is disabled by a license, database, or other startup error, fix Farmer's own startup error first. Include the server, Java, and Farmer versions, `/otoeritme status`, and the relevant console error when reporting a problem.
