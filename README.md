# MiraAuctionHouse

MiraAuctionHouse is the fixed-price player marketplace for the Mira Paper server suite. Players can list real ItemStacks for sale, browse/search listings, buy from other players and reclaim expired or cancelled items through protected claim storage.

## Download

[**Download MiraAuctionHouse v0.1.4**](https://github.com/FiveSOCE/Mira-AuctionHouse/releases/download/v0.1.4/MiraAuctionHouse-0.1.4.jar)

[View All Releases](https://github.com/FiveSOCE/Mira-AuctionHouse/releases)

## Requirements / Dependencies

- Paper 1.21.11
- Java 21
- Vault
- A Vault-compatible economy provider
- PlaceholderAPI optional for global market-stat placeholders
- MiraCosmetics optional for centralized audio effects

## How MiraAuctionHouse Works

A seller holds an item and creates a fixed-price listing with `/ah sell <price>`. The exact Bukkit `ItemStack` is preserved, including custom metadata. Active listings are displayed newest-first and standard listings expire after the configured period (48 hours by default). Expired listings and seller-cancelled listings move into protected claim storage so items are not lost even when the player's inventory is full.

Buyers browse the Auction House GUI, use automatic categories or search, and confirm purchases through the purchase-confirmation interface. Vault handles buyer withdrawals and seller payouts. Blacklists can block listings by material, display name or persistent data, with MiraRedeem and MiraFly voucher items blocked by default. Listing limits are permission/configuration-aware and `miraauctionhouse.noexpiry` exempts authorized users from normal expiry behaviour.

MiraAuctionHouse keeps transaction history and persistent expired-listing history. Market intelligence uses completed `SOLD` transactions to calculate actual historical sale prices rather than asking prices. v0.1.3 also records sold quantity for new transactions and provides 24h, 7d and 30d market windows with average listing price, average/median per-unit price, low/high range, sold volume and trend versus the preceding equal-length window.

## Commands

| Command | Permission | What it does |
| --- | --- | --- |
| `/ah` | `miraauctionhouse.use` | Opens the main Auction House browser. |
| `/ah sell <price>` | `miraauctionhouse.sell` | Lists the item held in the player's main hand at a fixed price, subject to blacklist and listing limits. |
| `/ah my` | `miraauctionhouse.use` | Opens the player's own active listings. |
| `/ah claim` | `miraauctionhouse.use` | Claims expired/cancelled/overflow items from protected claim storage. |
| `/ah history` | `miraauctionhouse.use` | Opens the player's transaction/listing history. |
| `/ah pricehistory <material>` | `miraauctionhouse.use` | Shows recent completed sales with stack quantity and per-unit pricing. |
| `/ah market <material> [24h|7d|30d]` | `miraauctionhouse.use` | Shows windowed market sales, unit volume, average, median, low/high and trend data. |
| `/ah expired` | `miraauctionhouse.use` | Shows persistent expired-listing history. |
| `/ah search [text]` | `miraauctionhouse.search` | Searches Auction House listings; without text, starts the GUI/chat search flow. |
| `/ah reload` | `miraauctionhouse.admin` | Reloads Auction House configuration/data. |
| `/ah admin remove <listingId>` | `miraauctionhouse.admin` | Force-removes a listing and returns it to the seller's claim storage. |

Aliases: `/auctionhouse`, `/auction`.

Purchasing through the GUI requires `miraauctionhouse.buy`.

## Permissions

| Permission | Default | What it does |
| --- | --- | --- |
| `miraauctionhouse.use` | Everyone | Allows normal Auction House browsing, personal listings/history and claim storage. |
| `miraauctionhouse.sell` | Everyone | Allows creating Auction House listings. |
| `miraauctionhouse.buy` | Everyone | Allows buying listings. |
| `miraauctionhouse.search` | Everyone | Allows Auction House searching. |
| `miraauctionhouse.admin` | OP | Allows reloads and administrative listing removal. |
| `miraauctionhouse.noexpiry` | OP | Exempts authorized listings/users from normal listing expiry behaviour. |


## PlaceholderAPI

Global market placeholders do not require player context. Format:

`%miraauctionhouse_<material>_<24h|7d|30d>_<metric>%`

Metrics: `avg`, `median`, `low`, `high`, `trend`, `volume`, `sales`.

Example: `%miraauctionhouse_diamond_7d_median%`.

## MiraCosmetics Audio Integration (0.1.4)

MiraCosmetics audio hooks cover listing creation, buyer purchase confirmation and online seller sold notifications.
