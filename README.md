# MiraAuctionHouse

Fixed-price player marketplace for the Mira Minecraft plugin ecosystem.

## Download

[**Download MiraAuctionHouse v0.1.1 (.jar)**](https://github.com/FiveSOCE/Mira-AuctionHouse/releases/download/v0.1.1/MiraAuctionHouse-0.1.1.jar)

[View all releases](https://github.com/FiveSOCE/Mira-AuctionHouse/releases)

## Core behaviour

- Fixed-price listings only. There is no bidding system.
- Listings are always displayed newest to oldest.
- Standard listings expire after 48 hours.
- Expired and cancelled items move into the seller's claim storage.
- Cancelling a listing reports the actual item name instead of an internal listing ID, including renamed/custom display names.
- Auction data is stored in one actively maintained `auctions.yml` file.
- Vault handles buyer withdrawals and seller deposits.
- Exact Bukkit `ItemStack` data is preserved in YAML.
- Search and automatic item categories are supported.
- Purchases use a confirmation GUI.
- Sellers can cancel their own active listings.
- Purchase overflow goes to `/ah claim` instead of dropping on the ground.
- Transaction history is retained with a configurable history cap.
- Listing limits are permission controlled.
- Blacklisted materials, display-name fragments and PDC keys cannot be listed.
- MiraRedeem and MiraFly vouchers are blocked from AH by default.

## Commands

```text
/ah
/ah sell <price>
/ah my
/ah claim
/ah history
/ah search [text]
/ah reload
/ah admin remove <listingId>
```

Aliases for `/ah` are `/auction` and `/auctionhouse`.

## Permissions

```text
miraauctionhouse.use
miraauctionhouse.sell
miraauctionhouse.buy
miraauctionhouse.search
miraauctionhouse.admin
miraauctionhouse.noexpiry

miraauctionhouse.limit.5
miraauctionhouse.limit.10
miraauctionhouse.limit.25
miraauctionhouse.limit.50
miraauctionhouse.limit.unlimited
```

Regular players receive the normal browse/sell/buy/search permissions by default. Administration remains OP-only unless explicitly granted.

## Storage

```text
plugins/MiraAuctionHouse/
├── config.yml
└── auctions.yml
```

`auctions.yml` stores active listings, pending item claims and transaction history. It is rewritten whenever AH state changes.

## Requirements

- Paper 1.21.11
- Java 21
- Vault
- A Vault-compatible economy provider

## Building

```bash
gradle clean build
```

Output:

```text
build/libs/MiraAuctionHouse-0.1.1.jar
```
