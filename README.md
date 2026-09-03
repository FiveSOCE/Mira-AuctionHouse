# MiraAuctionHouse

Fixed-price player marketplace for the Mira Minecraft plugin ecosystem.

## Download

Current release: **v0.1.2**

[**Download MiraAuctionHouse v0.1.2**](https://github.com/FiveSOCE/Mira-AuctionHouse/releases/download/v0.1.2/MiraAuctionHouse-0.1.2.jar)

[View all releases](https://github.com/FiveSOCE/Mira-AuctionHouse/releases)

## v0.1.2 market intelligence

- completed-sale price history
- average sale-price lookup
- persistent expired-listing history

Commands:

```text
/ah pricehistory <material>
/ah expired
```

Price history uses actual completed `SOLD` transactions, not listing asking prices. Expired history is derived from persistent `EXPIRED` marketplace history.

## Core behaviour

- fixed-price listings
- newest-first browsing
- 48-hour standard expiry
- expired/cancelled items move to claim storage
- Vault buyer/seller transactions
- exact Bukkit `ItemStack` preservation
- search and automatic categories
- purchase confirmation GUI
- seller cancellation
- overflow protection through `/ah claim`
- transaction history
- permission-based listing limits
- material/display-name/PDC blacklists
- MiraRedeem and MiraFly vouchers blocked by default

## Commands

```text
/ah
/ah sell <price>
/ah my
/ah claim
/ah history
/ah pricehistory <material>
/ah expired
/ah search [text]
/ah reload
/ah admin remove <listingId>
```

## Requirements

- Paper 1.21.11
- Java 21
- Vault
- Vault-compatible economy provider

## Building

```bash
gradle clean build
```

Output:

```text
build/libs/MiraAuctionHouse-0.1.2.jar
```
