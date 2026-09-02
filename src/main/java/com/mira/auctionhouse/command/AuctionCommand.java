package com.mira.auctionhouse.command;

import com.mira.auctionhouse.MiraAuctionHousePlugin;
import com.mira.auctionhouse.gui.AuctionGuiService;
import com.mira.auctionhouse.service.AuctionService;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

public final class AuctionCommand implements TabExecutor {
    private final MiraAuctionHousePlugin plugin;
    private final AuctionService service;
    private final AuctionGuiService gui;

    public AuctionCommand(MiraAuctionHousePlugin plugin, AuctionService service, AuctionGuiService gui) {
        this.plugin = plugin;
        this.service = service;
        this.gui = gui;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            if (args.length > 0 && args[0].equalsIgnoreCase("reload") && sender.hasPermission("miraauctionhouse.admin")) {
                plugin.reloadAll();
                plugin.msg(sender, "&aMiraAuctionHouse reloaded.");
                return true;
            }
            plugin.msg(sender, "&cThis command must be run by a player.");
            return true;
        }

        if (args.length == 0) {
            gui.openBrowse(player, 0);
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "sell" -> sell(player, args);
            case "my" -> gui.openMy(player, 0);
            case "claim" -> {
                int claimed = service.claimAll(player);
                plugin.msg(player, claimed > 0 ? "&aClaimed &f" + claimed + " &aitem stack(s)." : "&eYou have nothing claimable, or your inventory is full.");
            }
            case "history" -> gui.openHistory(player);
            case "search" -> {
                if (!player.hasPermission("miraauctionhouse.search")) {
                    plugin.msg(player, "&cYou do not have permission to search the Auction House.");
                } else if (args.length == 1) {
                    gui.beginSearch(player);
                } else {
                    gui.submitSearch(player, String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length)));
                }
            }
            case "reload" -> {
                if (!player.hasPermission("miraauctionhouse.admin")) plugin.msg(player, "&cNo permission.");
                else {
                    plugin.reloadAll();
                    plugin.msg(player, "&aMiraAuctionHouse reloaded.");
                }
            }
            case "admin" -> admin(player, args);
            default -> gui.openBrowse(player, 0);
        }
        return true;
    }

    private void sell(Player player, String[] args) {
        if (args.length < 2) {
            plugin.msg(player, "&eUsage: /ah sell <price>");
            return;
        }
        double price;
        try {
            price = Double.parseDouble(args[1]);
        } catch (NumberFormatException ex) {
            plugin.msg(player, "&cPrice must be a valid positive number.");
            return;
        }
        if (service.isBlacklisted(player.getInventory().getItemInMainHand())) {
            plugin.msg(player, "&cThat item cannot be listed on the Auction House.");
            return;
        }
        if (service.activeCount(player.getUniqueId()) >= service.listingLimit(player)) {
            plugin.msg(player, "&cYou have reached your Auction House listing limit.");
            return;
        }
        if (!service.createListing(player, price)) {
            plugin.msg(player, "&cCould not create that listing. Hold the item in your main hand and use a valid price.");
            return;
        }
        plugin.msg(player, "&aListed your held item for &f$" + price + "&a. It will appear at the front of the Auction House.");
    }

    private void admin(Player player, String[] args) {
        if (!player.hasPermission("miraauctionhouse.admin")) {
            plugin.msg(player, "&cNo permission.");
            return;
        }
        if (args.length >= 3 && args[1].equalsIgnoreCase("remove")) {
            boolean removed = service.cancel(player, args[2], true);
            plugin.msg(player, removed ? "&aRemoved listing &f" + args[2] + "&a and returned it to the seller's claims." : "&cListing not found.");
            return;
        }
        plugin.msg(player, "&eAdmin: /ah admin remove <listingId>");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return match(args[0], List.of("sell", "my", "claim", "history", "search", "reload", "admin"));
        if (args.length == 2 && args[0].equalsIgnoreCase("admin") && sender.hasPermission("miraauctionhouse.admin")) return match(args[1], List.of("remove"));
        return List.of();
    }

    private static List<String> match(String prefix, List<String> values) {
        String p = prefix.toLowerCase(Locale.ROOT);
        return values.stream().filter(v -> v.startsWith(p)).toList();
    }
}
