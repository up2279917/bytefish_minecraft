package com.bytefish.bytecore.listeners;

import com.bytefish.bytecore.config.ConfigManager;
import com.bytefish.bytecore.managers.ShopManager;
import com.bytefish.bytecore.models.Shop;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.block.Sign;
import org.bukkit.block.data.type.WallSign;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.InventoryHolder;

public class ShopInteractionListener implements Listener {

	private final ShopManager shopManager;
	private final ConfigManager configManager;

	public ShopInteractionListener(
		ShopManager shopManager,
		ConfigManager configManager
	) {
		this.shopManager = shopManager;
		this.configManager = configManager;
	}

	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onPlayerInteract(PlayerInteractEvent event) {
		if (
			event.getAction() != Action.RIGHT_CLICK_BLOCK ||
			event.getClickedBlock() == null
		) {
			return;
		}

		Block block = event.getClickedBlock();

		if (
			block.getState() instanceof Sign &&
			block.getBlockData() instanceof WallSign wallSign
		) {
			Block attached = block.getRelative(
				wallSign.getFacing().getOppositeFace()
			);
			handleShopInteraction(event, attached);
			return;
		}

		if (block.getState() instanceof Container) {
			handleShopInteraction(event, block);
		}
	}

	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onBlockBreak(BlockBreakEvent event) {
		Block block = event.getBlock();

		if (
			configManager.isValidShopContainer(block.getType()) &&
			shopManager.isShop(block.getLocation())
		) {
			Shop shop = shopManager.getShop(block.getLocation());
			if (shop != null) {
				if (
					!event.getPlayer().getUniqueId().equals(shop.getOwnerUUID())
				) {
					event.setCancelled(true);
					return;
				}
				shopManager.removeShop(block.getLocation());
			}
			return;
		}

		if (block.getBlockData() instanceof WallSign wallSign) {
			Block attached = block.getRelative(
				wallSign.getFacing().getOppositeFace()
			);
			if (
				attached != null && shopManager.isShop(attached.getLocation())
			) {
				Shop shop = shopManager.getShop(attached.getLocation());
				if (
					shop != null &&
					!event.getPlayer().getUniqueId().equals(shop.getOwnerUUID())
				) {
					event.setCancelled(true);
				}
			}
		}
	}

	private void handleShopInteraction(
		PlayerInteractEvent event,
		Block container
	) {
		if (!shopManager.isShop(container.getLocation())) {
			return;
		}

		Player player = event.getPlayer();
		Shop shop = shopManager.getShop(container.getLocation());

		if (shop == null) {
			return;
		}

		if (player.getUniqueId().equals(shop.getOwnerUUID())) {
			return;
		}

		event.setCancelled(true);
		shopManager.processTransaction(shop, player);
	}

	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onInventoryClick(InventoryClickEvent event) {
		if (!(event.getWhoClicked() instanceof Player player)) {
			return;
		}

		InventoryHolder holder = event.getInventory().getHolder();
		if (
			holder instanceof Container container &&
			shopManager.isShop(container.getLocation())
		) {
			Shop shop = shopManager.getShop(container.getLocation());
			if (
				shop != null &&
				!player.getUniqueId().equals(shop.getOwnerUUID())
			) {
				event.setCancelled(true);
			}
		}
	}

	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onInventoryDrag(InventoryDragEvent event) {
		if (!(event.getWhoClicked() instanceof Player player)) {
			return;
		}

		InventoryHolder holder = event.getInventory().getHolder();
		if (
			holder instanceof Container container &&
			shopManager.isShop(container.getLocation())
		) {
			Shop shop = shopManager.getShop(container.getLocation());
			if (
				shop != null &&
				!player.getUniqueId().equals(shop.getOwnerUUID())
			) {
				event.setCancelled(true);
			}
		}
	}
}
