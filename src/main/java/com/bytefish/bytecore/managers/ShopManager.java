package com.bytefish.bytecore.managers;

import com.bytefish.bytecore.ByteCore;
import com.bytefish.bytecore.config.ConfigManager;
import com.bytefish.bytecore.models.Shop;
import com.bytefish.bytecore.models.ShopTransaction;
import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import java.io.*;
import java.lang.reflect.Type;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public class ShopManager {

	private final ByteCore plugin;
	private final ConfigManager config;
	private final Map<Location, Shop> shops;
	private final Map<Location, ReentrantLock> shopLocks;
	private final File shopsFile;
	private final Gson gson;

	public ShopManager(ByteCore plugin, ConfigManager config) {
		this.plugin = plugin;
		this.config = config;
		this.shops = new ConcurrentHashMap<>();
		this.shopLocks = new ConcurrentHashMap<>();
		this.shopsFile = new File(plugin.getDataFolder(), "shops.json");
		this.gson = createGsonInstance();
		loadShops();
	}

	private Gson createGsonInstance() {
		return new GsonBuilder()
			.registerTypeAdapter(Location.class, new LocationAdapter())
			.registerTypeAdapter(ItemStack.class, new ItemStackAdapter())
			.setPrettyPrinting()
			.create();
	}

	public Shop addShop(
		Location location,
		Player owner,
		ItemStack sellingItem,
		int sellingAmount,
		ItemStack priceItem,
		int priceAmount
	) {
		if (!isValidShopLocation(location)) {
			owner.sendMessage(
				Component.text("Invalid shop location!").color(
					NamedTextColor.RED
				)
			);
			return null;
		}

		Shop shop = new Shop(
			location,
			owner.getUniqueId(),
			owner.getName(),
			sellingItem,
			sellingAmount,
			priceItem,
			priceAmount
		);

		if (!shop.isValid()) {
			owner.sendMessage(
				Component.text("Invalid item amounts!").color(
					NamedTextColor.RED
				)
			);
			return null;
		}

		shops.put(location, shop);
		shopLocks.put(location, new ReentrantLock());
		saveAll();
		return shop;
	}

	public ShopTransaction processTransaction(Shop shop, Player buyer) {
		if (
			!buyer.isOnline() ||
			!shop
				.getLocation()
				.getWorld()
				.isChunkLoaded(
					shop.getLocation().getBlockX() >> 4,
					shop.getLocation().getBlockZ() >> 4
				)
		) {
			return null;
		}

		ReentrantLock lock = shopLocks.computeIfAbsent(shop.getLocation(), k ->
			new ReentrantLock()
		);

		try {
			if (!lock.tryLock(500, TimeUnit.MILLISECONDS)) {
				return null;
			}

			try {
				if (!shops.containsKey(shop.getLocation())) {
					return null;
				}

				Block block = shop.getLocation().getBlock();
				if (!(block.getState() instanceof Container container)) {
					return null;
				}

				ShopTransaction transaction = new ShopTransaction(
					shop,
					buyer,
					shop.getSellingItem(),
					shop.getPriceItem()
				);

				if (!verifyInventories(buyer, container)) {
					transaction.fail("Invalid inventory state");
					return transaction;
				}

				if (!executeTransaction(shop, buyer, container, transaction)) {
					return transaction;
				}

				transaction.complete();
				buyer.sendMessage(
					Component.text("Purchase successful!").color(
						NamedTextColor.GREEN
					)
				);
				return transaction;
			} finally {
				lock.unlock();
			}
		} catch (InterruptedException e) {
			plugin
				.getLogger()
				.warning("Shop transaction interrupted: " + e.getMessage());
			return null;
		}
	}

	private boolean verifyInventories(Player buyer, Container container) {
		return (
			buyer.isOnline() &&
			container.getInventory() != null &&
			buyer.getInventory() != null
		);
	}

	private boolean verifyShopState(
		Shop shop,
		Container container,
		ShopTransaction transaction
	) {
		if (!shops.containsKey(shop.getLocation())) {
			transaction.fail("Shop no longer exists");
			return false;
		}

		if (
			!hasStock(
				container.getInventory(),
				shop.getSellingItem(),
				shop.getSellingAmount()
			)
		) {
			transaction.fail("Shop is out of stock");
			return false;
		}

		if (
			!hasSpace(
				container.getInventory(),
				shop.getPriceItem(),
				shop.getPriceAmount()
			)
		) {
			transaction.fail("Shop is full and cannot accept payment");
			return false;
		}

		return true;
	}

	private boolean verifyBuyerInventory(
		Player buyer,
		Shop shop,
		ShopTransaction transaction
	) {
		if (
			!hasItems(
				buyer.getInventory(),
				shop.getPriceItem(),
				shop.getPriceAmount()
			)
		) {
			transaction.fail("Insufficient payment items");
			return false;
		}

		if (
			!hasSpace(
				buyer.getInventory(),
				shop.getSellingItem(),
				shop.getSellingAmount()
			)
		) {
			transaction.fail("Insufficient inventory space");
			return false;
		}

		return true;
	}

	private boolean executeTransaction(
		Shop shop,
		Player buyer,
		Container container,
		ShopTransaction transaction
	) {
		if (
			!removeItems(
				container.getInventory(),
				shop.getSellingItem(),
				shop.getSellingAmount()
			)
		) {
			transaction.fail("Failed to remove items from shop");
			return false;
		}

		if (
			!removeItems(
				buyer.getInventory(),
				shop.getPriceItem(),
				shop.getPriceAmount()
			)
		) {
			ItemStack revertItem = shop.getSellingItem().clone();
			revertItem.setAmount(shop.getSellingAmount());
			container.getInventory().addItem(revertItem);
			transaction.fail("Failed to process payment");
			return false;
		}

		ItemStack sellingItems = shop.getSellingItem().clone();
		sellingItems.setAmount(shop.getSellingAmount());
		buyer.getInventory().addItem(sellingItems);

		ItemStack paymentItems = shop.getPriceItem().clone();
		paymentItems.setAmount(shop.getPriceAmount());
		container.getInventory().addItem(paymentItems);

		return true;
	}

	private boolean hasItems(Inventory inventory, ItemStack item, int amount) {
		int count = 0;
		for (ItemStack stack : inventory.getContents()) {
			if (stack != null && stack.isSimilar(item)) {
				count += stack.getAmount();
				if (count >= amount) return true;
			}
		}
		return false;
	}

	private boolean hasStock(Inventory inventory, ItemStack item, int amount) {
		return countItems(inventory, item) >= amount;
	}

	private boolean hasSpace(Inventory inventory, ItemStack item, int amount) {
		return (
			inventory.firstEmpty() != -1 ||
			canStackWith(inventory, item, amount)
		);
	}

	private boolean canStackWith(
		Inventory inventory,
		ItemStack item,
		int amount
	) {
		for (ItemStack stack : inventory.getContents()) {
			if (
				stack != null &&
				stack.isSimilar(item) &&
				stack.getAmount() + amount <= stack.getMaxStackSize()
			) {
				return true;
			}
		}
		return false;
	}

	private int countItems(Inventory inventory, ItemStack item) {
		int count = 0;
		for (ItemStack stack : inventory.getContents()) {
			if (stack != null && stack.isSimilar(item)) {
				count += stack.getAmount();
			}
		}
		return count;
	}

	private boolean removeItems(
		Inventory inventory,
		ItemStack item,
		int amount
	) {
		int remaining = amount;
		ItemStack[] contents = inventory.getContents();

		for (int i = 0; i < contents.length && remaining > 0; i++) {
			ItemStack stack = contents[i];
			if (stack != null && stack.isSimilar(item)) {
				if (stack.getAmount() <= remaining) {
					remaining -= stack.getAmount();
					inventory.setItem(i, null);
				} else {
					stack.setAmount(stack.getAmount() - remaining);
					remaining = 0;
				}
			}
		}

		return remaining == 0;
	}

	public boolean isValidShopLocation(Location location) {
		Block block = location.getBlock();
		if (!config.isValidShopContainer(block.getType())) {
			return false;
		}

		int radius = config.getProtectionRadius();
		if (radius > 0) {
			for (int x = -radius; x <= radius; x++) {
				for (int y = -radius; y <= radius; y++) {
					for (int z = -radius; z <= radius; z++) {
						Location nearby = location.clone().add(x, y, z);
						if (shops.containsKey(nearby)) {
							return false;
						}
					}
				}
			}
		}

		return true;
	}

	public Shop getShop(Location location) {
		return shops.get(location);
	}

	public boolean isShop(Location location) {
		return shops.containsKey(location);
	}

	public void removeShop(Location location) {
		shops.remove(location);
		shopLocks.remove(location);
		saveAll();
	}

	private void loadShops() {
		if (!shopsFile.exists()) {
			saveAll();
			return;
		}

		try (Reader reader = new FileReader(shopsFile)) {
			Type listType = new TypeToken<ArrayList<ShopData>>() {}.getType();
			List<ShopData> shopDataList = gson.fromJson(reader, listType);

			if (shopDataList != null) {
				shops.clear();
				shopLocks.clear();
				for (ShopData data : shopDataList) {
					Shop shop = data.toShop();
					shops.put(shop.getLocation(), shop);
					shopLocks.put(shop.getLocation(), new ReentrantLock());
				}
			}
		} catch (IOException e) {
			plugin
				.getLogger()
				.severe("Failed to load shops: " + e.getMessage());
		}
	}

	public void saveAll() {
		try {
			if (!shopsFile.exists()) {
				plugin.getDataFolder().mkdirs();
				shopsFile.createNewFile();
			}

			List<ShopData> shopDataList = new ArrayList<>();
			for (Shop shop : shops.values()) {
				shopDataList.add(new ShopData(shop));
			}

			try (Writer writer = new FileWriter(shopsFile)) {
				gson.toJson(shopDataList, writer);
			}
		} catch (IOException e) {
			plugin
				.getLogger()
				.severe("Failed to save shops: " + e.getMessage());
		}
	}

	private static class LocationAdapter
		implements JsonSerializer<Location>, JsonDeserializer<Location> {

		@Override
		public JsonElement serialize(
			Location location,
			Type type,
			JsonSerializationContext context
		) {
			JsonObject object = new JsonObject();
			object.addProperty("world", location.getWorld().getName());
			object.addProperty("x", location.getX());
			object.addProperty("y", location.getY());
			object.addProperty("z", location.getZ());
			return object;
		}

		@Override
		public Location deserialize(
			JsonElement element,
			Type type,
			JsonDeserializationContext context
		) throws JsonParseException {
			JsonObject object = element.getAsJsonObject();
			String worldName = object.get("world").getAsString();
			double x = object.get("x").getAsDouble();
			double y = object.get("y").getAsDouble();
			double z = object.get("z").getAsDouble();

			return new Location(org.bukkit.Bukkit.getWorld(worldName), x, y, z);
		}
	}

	private static class ItemStackAdapter
		implements JsonSerializer<ItemStack>, JsonDeserializer<ItemStack> {

		@Override
		public JsonElement serialize(
			ItemStack item,
			Type type,
			JsonSerializationContext context
		) {
			JsonObject object = new JsonObject();
			object.addProperty("type", item.getType().name());
			object.addProperty("amount", item.getAmount());

			return object;
		}

		@Override
		public ItemStack deserialize(
			JsonElement element,
			Type type,
			JsonDeserializationContext context
		) throws JsonParseException {
			JsonObject object = element.getAsJsonObject();
			String materialName = object.get("type").getAsString();
			int amount = object.get("amount").getAsInt();

			return new ItemStack(
				org.bukkit.Material.valueOf(materialName),
				amount
			);
		}
	}

	private static class ShopData {

		private final UUID id;
		private final LocationData location;
		private final UUID ownerUUID;
		private final String ownerName;
		private final ItemStackData sellingItem;
		private final int sellingAmount;
		private final ItemStackData priceItem;
		private final int priceAmount;
		private final long creationTime;

		public ShopData(Shop shop) {
			this.id = shop.getId();
			this.location = new LocationData(shop.getLocation());
			this.ownerUUID = shop.getOwnerUUID();
			this.ownerName = shop.getOwnerName();
			this.sellingItem = new ItemStackData(shop.getSellingItem());
			this.sellingAmount = shop.getSellingAmount();
			this.priceItem = new ItemStackData(shop.getPriceItem());
			this.priceAmount = shop.getPriceAmount();
			this.creationTime = shop.getCreationTime();
		}

		public Shop toShop() {
			Location loc = location.toLocation();
			ItemStack selling = sellingItem.toItemStack();
			ItemStack price = priceItem.toItemStack();
			return new Shop(
				id,
				loc,
				ownerUUID,
				ownerName,
				selling,
				sellingAmount,
				price,
				priceAmount,
				creationTime
			);
		}
	}

	private static class LocationData {

		private final String world;
		private final double x;
		private final double y;
		private final double z;

		public LocationData(Location loc) {
			this.world = loc.getWorld().getName();
			this.x = loc.getX();
			this.y = loc.getY();
			this.z = loc.getZ();
		}

		public Location toLocation() {
			return new Location(org.bukkit.Bukkit.getWorld(world), x, y, z);
		}
	}

	private static class ItemStackData {

		private final String type;
		private final int amount;

		public ItemStackData(ItemStack item) {
			this.type = item.getType().name();
			this.amount = item.getAmount();
		}

		public ItemStack toItemStack() {
			return new ItemStack(org.bukkit.Material.valueOf(type), amount);
		}
	}
}
