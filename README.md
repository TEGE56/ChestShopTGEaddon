# ChestShopTGEaddon

An addon for **ChestShop** that enhances shop displays with automatic hologram item displays, fully customizable shop signs, and interactive Gambling Chests with animated prizes.

Gambling Chests feature configurable prices, reward probabilities, bonus rewards, sound effects, animations, and automatic reward generation. The economy integration is currently provided exclusively by XConomy.

## Features

### Shop Holograms

- Automatically displays shop items above regular ChestShop shops in hologram displays.
- Requires no additional setup.
- Works automatically alongside ChestShop.
- Regular shop holograms cannot currently be customized through `config.yml`.

### Customizable Shop Signs

- Customize sign layouts for regular player shops and admin shops independently.
- Configure separate layouts for buying, selling, and shops that support both directions.
- Customize all four sign lines with text and Minecraft color codes.
- Display prices, quantities, currency information, and shop owner information using placeholders.
- Configure the currency suffix or hide it completely.
- Customize each shop type independently.

### Gambling Chests

- Create a Gambling Chest by attaching a sign to a chest and entering `[gamble]` on the first line.
- Automatically transforms the sign into a configured Gambling Chest sign when the sign is finalized.
- Displays rotating prize items above the chest.
- Configurable game price using XConomy.
- Animated prize selection and winning result display.
- Configurable prize pool and individual reward chances.
- Configurable chance of receiving no prize.
- Optional bonus item amounts with independent bonus chances.
- Automatic generation of eligible reward materials, excluding blacklisted items.
- Customizable sign text and player-facing messages.
- Configurable animation speed, display height, and bobbing animation.
- Customizable sound effects with individual volume controls.
- Permission-based creation, use, and destruction.

## Requirements

ChestShopTGEaddon requires the following plugins:

- **ChestShop** — provides the shop functionality.
- **XConomy** — provides the economy integration for Gambling Chests.

**XConomy is currently the only supported economy plugin.** Other economy plugins are not supported at this time.

The plugin declares Minecraft/Bukkit API version `26.3`. Make sure your server implementation supports the API and plugin version you are using.

## Installation

1. Download the ChestShopTGEaddon JAR file.
2. Install ChestShop and XConomy on your server.
3. Place the ChestShopTGEaddon JAR in the server's `plugins` folder.
4. Start or restart the server.
5. Open `plugins/ChestShopTGEaddon/config.yml`.
6. Customize the configuration as needed.
7. Apply configuration changes using `/chestshoptgeaddon reload`, or restart the server if necessary.

Always back up your configuration before making major changes, especially before generating a new reward list.

## Commands

### `/chestshoptgeaddon reload`

Reloads the ChestShopTGEaddon configuration by calling the plugin's configuration reload method.

**Permission:** `chestshoptgeaddon.reload`

The command works as follows:

1. Checks that exactly one argument was provided and that it is `reload`, ignoring capitalization.
2. If the syntax is incorrect, displays the configured usage message.
3. Checks whether the sender has the required permission.
4. Reloads the plugin configuration.
5. Displays the configured success message if the reload completes successfully.
6. If a `RuntimeException` occurs during the reload, logs an error to the server console and displays the configured failure message.

The command supports tab completion. Senders with the reload permission can receive the `reload` suggestion when entering the first argument.

Reload messages can be customized in `config.yml` under `commands.reload`.

Example:

```text
/chestshoptgeaddon reload
```

## Permissions

All permissions default to operators (`op`).

| Permission | Default | Description |
|---|---|---|
| `chestshoptgeaddon.reload` | `op` | Allows reloading the plugin configuration. |
| `chestshoptgeaddon.gamble.admin` | `op` | Allows creating and destroying Gambling Chests. |
| `chestshoptgeaddon.gamble.use` | `op` | Allows using Gambling Chests. |

For regular players, grant only the permissions they need. Players who should be able to play at existing Gambling Chests do not need the administrative permission.

---

# Configuration

The main configuration file is:

`plugins/ChestShopTGEaddon/config.yml`

Configuration values use YAML syntax. Keep the indentation correct, preserve quotation marks where needed, and use `&` color codes to customize text colors.

After changing settings, run:

```text
/chestshoptgeaddon reload
```

## 1. Reload Messages

The `commands.reload` section controls the messages displayed when using the reload command.

```yaml
commands:
  reload:
    success: "&aChestShopTGEaddon configuration reloaded."
    no-permission: "&cYou do not have permission to use this command."
    usage: "&cUsage: /chestshoptgeaddon reload"
    failure: "&cConfiguration reload failed. Check the console."
```

| Setting | Description |
|---|---|
| `success` | Displayed when the configuration reload succeeds. |
| `no-permission` | Displayed when the sender lacks the required permission. |
| `usage` | Displayed when the command is used incorrectly. |
| `failure` | Displayed when the reload fails. Check the console for details. |

You can change the wording and colors of each message. The command uses these values from the configuration and falls back to its default message if a configured key is missing.

## 2. Shop Sign Configuration

The `shop-display.sign` section controls the appearance of regular player shop signs and admin shop signs.

Example configuration:

```yaml
shop-display:
  sign:
    currency-suffix: ""

    regular:
      buy:
        lines:
          - "&aShop &8| &bBuy"
          - "&fBuy: %quantity%"
          - "&a%price% %currency%"
          - "&7%owner%"

      sell:
        lines:
          - "&aShop &8| &eSell"
          - "&fSell: %quantity%"
          - "&a%price% %currency%"
          - "&7%owner%"

      both:
        lines:
          - "&aShop &8| &bBuy &8/ &eSell"
          - "&fQuantity: %quantity%"
          - "&bBuy: %buy_price% &8/ &eSell: %sell_price%"
          - "&7%owner%"

    admin:
      buy:
        lines:
          - "&4AdminShop &8| &bBuy"
          - "&fBuy: %quantity%"
          - "&a%price% %currency%"
          - ""

      sell:
        lines:
          - "&4AdminShop &8| &eSell"
          - "&fSell: %quantity%"
          - "&a%price% %currency%"
          - ""

      both:
        lines:
          - "&4AdminShop &8| &bBuy &8/ &eSell"
          - "&fQuantity: %quantity%"
          - "&bBuy: %buy_price% &8/ &eSell: %sell_price%"
          - ""
```

### Shop Types

The configuration separates signs by shop type and transaction direction.

| Setting | Description |
|---|---|
| `regular` | Sign layouts for regular player shops. |
| `admin` | Sign layouts for admin shops. |
| `buy` | The player buys items from the shop. |
| `sell` | The player sells items to the shop. |
| `both` | The shop supports both buying and selling. |

Each layout contains exactly four sign lines.

You can independently change the text, colors, placeholders, and empty lines in each layout. Use `""` for a blank line.

**Important:** Every sign format must contain exactly four lines.

### Available Shop Placeholders

| Placeholder | Description |
|---|---|
| `%buy_price%` | Buy price. |
| `%sell_price%` | Sell price. |
| `%price%` | Price for the relevant shop operation. |
| `%currency%` | Currency label. |
| `%quantity%` | Quantity represented by the shop sign. |
| `%trade_quantity%` | Trade quantity. |
| `%owner%` | Shop owner information. |

Both `%quantity%` and `%trade_quantity%` return only the number. They do not add descriptive text automatically.

For example:

```yaml
- "&fBuy quantity: %quantity%"
```

This lets you choose your own wording and formatting.

The values displayed by the placeholders depend on the shop information provided by ChestShop.

### Currency Suffix

```yaml
currency-suffix: ""
```

Controls the configured currency suffix used by the shop display.

- `""` — hides the suffix.
- `" coins"` — adds a custom suffix, such as `coins`.

Example:

```yaml
currency-suffix: " coins"
```

You can also customize the surrounding text in your sign layouts, including the text before and after `%currency%`.

### Shop Hologram Configuration

Regular shop holograms are automatic and do not have configurable settings in this plugin's `config.yml`.

Their appearance and behavior cannot currently be customized through the configuration.

---

# Gambling Chests

Gambling Chests are interactive prize machines that charge players for each game and select rewards from a configurable prize table.

## 3. Game Price

```yaml
gambling:
  price: 100.0
```

The `gambling.price` setting determines how much one game costs.

For example:

```yaml
price: 100.0
```

Each play costs 100 units of the configured XConomy currency.

The price is charged when a player starts a game. Make sure the configured value is valid and appropriate for your server's economy.

## 4. Gambling Chest Messages

The `gambling.messages` section controls messages displayed to players when creating, using, or interacting with Gambling Chests.

| Key | Description |
|---|---|
| `no-create-permission` | The player cannot create Gambling Chests. |
| `chest-required` | The sign must be attached to a chest. |
| `invalid-price-create` | The configured game price is invalid during creation. |
| `sign-created` | A Gambling Chest was created successfully. |
| `no-use-permission` | The player cannot use Gambling Chests. |
| `gamble-in-progress` | The chest is already running a game. |
| `inventory-full` | The player's inventory is full. |
| `invalid-price-use` | The configured price is invalid when starting a game. |
| `economy-unavailable` | XConomy is unavailable. |
| `chest-not-found` | No associated chest was found next to the sign. |
| `no-rewards` | The reward table contains no valid rewards. |
| `account-not-found` | The player's XConomy account could not be found. |
| `not-enough-money` | The player cannot afford the game. |
| `payment-failed` | The payment operation failed. |
| `no-prize` | The player paid but did not win a prize. |
| `prize-won` | The player won an item. |
| `refund-failed` | The reward could not be delivered and the payment could not be refunded. |
| `payment-refunded` | The payment was refunded. |
| `no-destroy-permission` | The player is not allowed to destroy a Gambling Chest. |

Example:

```yaml
messages:
  no-create-permission: "&cYou do not have permission to create gambling chests."
  chest-required: "&cPlace the sign next to a chest."
  invalid-price-create: "&cThe configured gambling price is invalid."
  sign-created: "&aGambling sign created. Price: &e%price%"
  no-use-permission: "&cYou do not have permission to use gambling chests."
  gamble-in-progress: "&eThis chest is already running a game. Please wait."
  inventory-full: "&cYour inventory is full. Make some room first."
  invalid-price-use: "&cThe gambling price is not configured correctly."
  economy-unavailable: "&cXConomy is not available."
  chest-not-found: "&cNo gambling chest was found next to the sign."
  no-rewards: "&cThe gambling reward table contains no valid rewards."
  account-not-found: "&cYour XConomy account could not be found."
  not-enough-money: "&cYou do not have enough money. Price: &e%price%"
  payment-failed: "&cPayment failed. Please try again later."
  no-prize: "&7You paid &a%price%&7, but did not win a prize this time."
  prize-won: "&7You paid &a%price% &7and won &f%amount%x %item%&7!"
  refund-failed: "&cThe reward could not be delivered and the payment could not be refunded. Contact an administrator."
  payment-refunded: "&aYour payment has been refunded."
  no-destroy-permission: "&cOnly an admin can destroy a gambling chest."
```

Available message placeholders:

- `%price%` — game price.
- `%amount%` — quantity awarded.
- `%item%` — awarded item name.

Use `&` color codes to customize the appearance of messages.

Keep the placeholders in messages where you want the relevant dynamic information to be displayed.

## 5. Gambling Chest Sign Appearance

```yaml
sign:
  lines:
    - "&6[Gamble]"
    - "&ePrice: &a%price%"
    - "&dRandom prize"
    - "&7Right-click"
```

The `gambling.sign.lines` section determines the four lines displayed on a created Gambling Chest sign.

You can customize each line independently using text, color codes, and `%price%`.

The `[gamble]` text used during creation is the creation marker. Once the sign is finalized, the plugin transforms it into the configured Gambling Chest sign.

## 6. Prize Display Animation

```yaml
display:
  change-seconds: 1
  height: 1.15
  bob-height: 0.03
```

These settings control the floating item display above the Gambling Chest.

| Setting | Description |
|---|---|
| `change-seconds` | How often the preview item changes. |
| `height` | Fixed vertical offset measured from the bottom of the chest block. |
| `bob-height` | Small up-and-down animation around the configured height. |

### `change-seconds`

Determines the interval between preview item changes.

A smaller value makes the preview cycle faster. A larger value makes it cycle more slowly.

### `height`

Controls the height of the displayed item above the chest.

Adjust this value to position the preview where it looks best.

### `bob-height`

Controls the strength of the floating item's vertical bobbing animation.

A value of `0.0` removes the bobbing movement, while a larger value makes the movement more pronounced.

The preview chooses reward entries uniformly. This means that items appearing frequently in the preview do not necessarily have higher winning chances.

**Actual reward probabilities are determined by the reward configuration, not by the preview animation.**

## 7. Winning Animation

```yaml
animation:
  duration-seconds: 3.0
  result-display-seconds: 1.5
```

| Setting | Description |
|---|---|
| `duration-seconds` | Duration of the prize selection animation. |
| `result-display-seconds` | How long the final winning item remains displayed after the animation. |

Increasing `duration-seconds` makes the prize selection take longer.

Increasing `result-display-seconds` keeps the winning item visible for longer before the result display ends.

These settings affect the visual experience, not the reward probabilities.

## 8. Sound Effects and Volumes

```yaml
sounds:
  spin-start: "minecraft:block.note_block.hat"
  spin-tick: "minecraft:block.note_block.pling"
  win: "minecraft:entity.player.levelup"
  no-prize: "minecraft:block.anvil.land"

  spin-start-volume: 1.0
  spin-tick-volume: 1.0
  win-volume: 1.0
  no-prize-volume: 1.0
```

### Sound Settings

| Setting | Description |
|---|---|
| `spin-start` | Sound played when the spinning animation starts. |
| `spin-tick` | Sound played during the animation. |
| `win` | Sound played when a prize is won. |
| `no-prize` | Sound played when no prize is awarded. |

Use valid Minecraft sound identifiers for your server version.

Set any sound to an empty string (`""`) to disable it completely.

For example:

```yaml
sounds:
  spin-start: ""
  spin-tick: "minecraft:block.note_block.pling"
  win: "minecraft:entity.player.levelup"
  no-prize: ""
```

### Individual Sound Volumes

Each sound has its own volume setting.

| Volume | Effect |
|---|---|
| `0.0` | Muted. |
| `0.5` | Half the default volume. |
| `1.0` | Default volume. |
| `2.0` | Louder than the default volume. |

Example:

```yaml
spin-start-volume: 2.0
spin-tick-volume: 0.5
win-volume: 1.0
no-prize-volume: 0.0
```

Volume values can be adjusted independently. Setting a volume to `0.0` mutes that sound without removing the configuration key.

---

## 9. Reward Configuration

The reward system is configured under:

`gambling.rewards`

Rewards determine which items can be won, how many items are awarded, and how likely each reward is to be selected.

### No-Prize Chance

```yaml
no-prize-chance: 10.0
```

Determines the percentage chance that a paid game awards no prize.

Examples:

| Value | Meaning |
|---|---|
| `0.0` | No no-prize outcome; a valid reward is always selected. |
| `10.0` | 10% chance of winning nothing. |
| `25.0` | 25% chance of winning nothing. |

The remaining probability is used to select one of the configured reward entries.

Do not add a separate no-prize item to the reward list. The no-prize outcome is controlled by `no-prize-chance`.

For example, with `no-prize-chance: 10.0`, the configured rewards collectively account for the remaining 90% of game outcomes.

### Manually Configuring Rewards

Rewards are configured in the `items` list.

Example:

```yaml
items:
  - material: TORCH
    amount: 16
    chance: 70.0

  - material: DIAMOND
    amount: 1
    chance: 30.0
```

Each reward entry supports the following settings:

| Setting | Required | Description |
|---|---|---|
| `material` | Yes | Bukkit material name in uppercase. |
| `amount` | Yes | Normal quantity awarded. Must fit one item stack. |
| `chance` | Yes | Relative probability of selecting this reward. |
| `bonus-amount` | No | Alternate quantity awarded if the bonus chance succeeds. |
| `bonus-chance` | No | Percentage chance of receiving the bonus amount when this reward is selected. |

### How Reward Chances Work

Reward chances are normalized proportionally.

For example:

```yaml
items:
  - material: TORCH
    amount: 16
    chance: 70.0

  - material: DIAMOND
    amount: 1
    chance: 30.0
```

This creates a 70/30 split between torches and diamonds among prize outcomes.

The chance values do not have to total exactly 100.

For example, these configurations produce the same relative distribution:

- `70` and `30`
- `700` and `300`
- `7` and `3`

If the chances total exactly 100, the values directly represent each reward's share of prize outcomes.

The no-prize chance is separate. To calculate the overall chance of winning a particular item, account for both the no-prize chance and that item's normalized share of the reward table.

For example, with a 10% no-prize chance and a 70/30 reward distribution:

- Torches: 63% overall chance.
- Diamonds: 27% overall chance.
- No prize: 10% chance.

These probabilities assume both reward entries are valid and available for selection.

### Bonus Amounts

You can optionally give a reward an alternate quantity with its own bonus chance.

Example:

```yaml
items:
  - material: DIAMOND
    amount: 1
    chance: 30.0
    bonus-amount: 2
    bonus-chance: 2.0
```

In this example:

- `amount: 1` — the normal reward is one diamond.
- `bonus-amount: 2` — the alternate reward quantity is two diamonds.
- `bonus-chance: 2.0` — there is a 2% chance of receiving the bonus quantity when this reward is selected.

The bonus chance is evaluated for the selected reward. It does not increase the probability of selecting that reward in the first place.

### Material Names

Use uppercase Bukkit material names supported by your server.

Examples:

```yaml
items:
  - material: TORCH
    amount: 16
    chance: 70.0

  - material: DIAMOND
    amount: 1
    chance: 20.0

  - material: NETHERITE_INGOT
    amount: 1
    chance: 10.0
```

The amount must fit into one item stack. Invalid materials or invalid reward configurations may be excluded from the usable reward table.

### Reward Blacklist

The blacklist prevents specified materials from being awarded, even if they appear in the reward list.

Example:

```yaml
blacklist:
  - ELYTRA
  - BEDROCK
  - DRAGON_EGG
  - BARRIER
  - COMMAND_BLOCK
  - CHAIN_COMMAND_BLOCK
  - REPEATING_COMMAND_BLOCK
  - COMMAND_BLOCK_MINECART
  - STRUCTURE_BLOCK
  - STRUCTURE_VOID
  - JIGSAW
  - DEBUG_STICK
  - KNOWLEDGE_BOOK
  - LIGHT
  - SPAWNER
  - TRIAL_SPAWNER
  - END_PORTAL_FRAME
  - END_PORTAL
  - END_GATEWAY
  - NETHER_PORTAL
  - MOVING_PISTON
  - PISTON_HEAD
  - BUDDING_AMETHYST
  - REINFORCED_DEEPSLATE
  - NETHER_STAR
  - BEACON
  - END_CRYSTAL
  - VAULT
```

You can add or remove materials according to your server's rules.

Materials in the blacklist can never be won, even if they are manually added to the reward table.

The default blacklist excludes several special, unobtainable, or potentially problematic materials. Netherite equipment is intentionally allowed unless you add it to the blacklist yourself.

The blacklist is especially important when using automatic reward generation.

### Automatically Generate All Eligible Rewards

```yaml
generate-all-survival-items: false
```

This option generates a reward list containing every eligible item material supported by the server, except materials listed in the blacklist.

| Value | Behavior |
|---|---|
| `false` | No automatic reward generation takes place. |
| `true` | Generates and replaces the reward list once. |

After successful generation, the option automatically changes back to `false`.

**Important:** Automatic generation replaces the existing reward list. Back up your configuration before enabling it if you want to preserve manually configured rewards.

#### How to Generate All Eligible Items

1. Back up your current `config.yml`.
2. Set `gambling.rewards.generate-all-survival-items` to `true`.
3. Save the configuration.
4. Run `/chestshoptgeaddon reload` or restart the server as appropriate.
5. Check the generated reward list and the server console.
6. Once generation succeeds, the setting returns to `false`.

Review the generated items and adjust the reward table to suit your server's economy.

The blacklist is applied to the generated rewards, so excluded materials are not added as eligible prizes.

When the setting is `false`, an empty reward list remains empty. The plugin does not generate rewards automatically just because the list is empty.

### Empty Reward List

The default configuration uses:

```yaml
items: []
```

This means the reward list is empty.

An empty list disables prize selection until valid rewards are configured.

To add rewards manually, replace `items: []` with actual reward entries.

For example:

```yaml
items:
  - material: IRON_INGOT
    amount: 8
    chance: 60.0

  - material: DIAMOND
    amount: 1
    chance: 30.0

  - material: EMERALD
    amount: 2
    chance: 10.0
```

Do not leave `items: []` in place when adding entries. Keep automatic generation disabled when you want to manage the reward table manually.

---

## Creating a Gambling Chest

Creating a Gambling Chest does not require manually filling out the sign's other lines.

### Step-by-Step Instructions

1. Place a chest where you want the Gambling Chest to operate.
2. Attach a sign to the chest.
3. Enter `[gamble]` on the **first line**, including both square brackets.
4. Leave the other sign lines empty if you want.
5. Press **Done** to finish editing the sign.

That's all! The plugin recognizes `[gamble]` and transforms the sign into a Gambling Chest sign using the configured `gambling.sign.lines` layout.

After creation, the prize items begin rotating above the chest.

The floating items are previews of possible rewards from the configured prize table. Their display changes according to the `gambling.display` settings.

The chest itself acts as the machine's location and can remain empty. You do not need to put the prize items inside it.

**Creation permission:** `chestshoptgeaddon.gamble.admin`

### Using a Gambling Chest

1. Make sure you have the `chestshoptgeaddon.gamble.use` permission.
2. Ensure your XConomy balance covers the configured game price.
3. Right-click the Gambling Chest sign to play.
4. The configured price is charged and the prize animation starts.
5. After the animation, you either receive a reward or get the no-prize outcome.

Make sure your inventory has enough room before playing.

If a reward cannot be delivered, the plugin reports the outcome through its configured refund messages.

### Destroying a Gambling Chest

The `chestshoptgeaddon.gamble.admin` permission allows authorized players to destroy Gambling Chests.

Players without the required permission receive the configured `no-destroy-permission` message when the corresponding permission check prevents destruction.

---

## Troubleshooting

### The Plugin Does Not Load

- Check that ChestShop is installed.
- Check that XConomy is installed and working.
- Review the server console for dependency or configuration errors.
- Ensure your server version is compatible with the plugin.

### Shop Signs Do Not Display Correctly

- Check that every sign format contains exactly four lines.
- Verify YAML indentation and quotation marks.
- Confirm that placeholders are spelled correctly.
- Check that the relevant `regular` or `admin` layout is configured as intended.

### The Reload Command Does Not Work

- Use `/chestshoptgeaddon reload`.
- Ensure you have `chestshoptgeaddon.reload`.
- Check the server console if the plugin reports a reload failure.
- Verify that the configuration contains valid YAML.

### Gambling Chests Cannot Be Created

- Attach the sign to a chest.
- Enter `[gamble]` on the first line, including the brackets.
- Press Done to finalize the sign.
- Ensure you have `chestshoptgeaddon.gamble.admin`.
- Check the configured game price if the plugin reports an invalid price.

### Gambling Chests Cannot Be Used

- Ensure you have `chestshoptgeaddon.gamble.use`.
- Verify that XConomy is available.
- Ensure your account has enough money.
- Check that the reward list contains valid rewards.
- Make sure your inventory has room for the reward.

### No Valid Rewards Are Available

- Check `gambling.rewards.items`.
- Ensure material names are valid and supported by your server.
- Check whether the materials are listed in the blacklist.
- If you want automatic generation, enable `generate-all-survival-items` and verify that generation succeeds.

---

## Compatibility and Support

ChestShopTGEaddon is designed to work alongside ChestShop and XConomy.

Currently supported dependencies:

- ChestShop
- XConomy

Other economy plugins are not supported at this time.

For issues, unexpected behavior, or feature requests, open an issue in this GitHub repository and include relevant server console errors and configuration details. Remove passwords, tokens, and other sensitive information before sharing logs.

## License

Add a `LICENSE` file to this repository if you intend to distribute ChestShopTGEaddon publicly. The permissions to use, modify, and redistribute the plugin depend on the license you choose.
