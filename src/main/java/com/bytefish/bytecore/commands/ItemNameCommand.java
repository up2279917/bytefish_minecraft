package com.bytefish.bytecore.commands;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class ItemNameCommand implements CommandExecutor {

	@Override
	public boolean onCommand(
		@NotNull CommandSender sender,
		@NotNull Command command,
		@NotNull String label,
		String[] args
	) {
		if (!(sender instanceof Player)) {
			sender.sendMessage("This command can only be used by players.");
			return true;
		}

		Player player = (Player) sender;
		Material material = player.getInventory().getItemInMainHand().getType();

		if (material == Material.AIR) {
			player.sendMessage(
				Component.text("You are not holding an item!").color(
					NamedTextColor.RED
				)
			);
			return true;
		}

		int itemId = material.getId(); // Get the item ID
		player.sendMessage(
			Component.text(material.name() + " " + itemId).color(
				NamedTextColor.GREEN
			)
		);
		return true;
	}
}
