package com.bytefish.bytecore.models;

import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

public class Shop {

	private final UUID id;
	private final Location location;
	private final UUID ownerUUID;
	private final String ownerName;
	private final ItemStack sellingItem;
	private final int sellingAmount;
	private final ItemStack priceItem;
	private final int priceAmount;
	private final long creationTime;

	public Shop(
		Location location,
		UUID ownerUUID,
		String ownerName,
		ItemStack sellingItem,
		int sellingAmount,
		ItemStack priceItem,
		int priceAmount
	) {
		this(
			UUID.randomUUID(),
			location,
			ownerUUID,
			ownerName,
			sellingItem,
			sellingAmount,
			priceItem,
			priceAmount,
			System.currentTimeMillis()
		);
	}

	public Shop(
		UUID id,
		Location location,
		UUID ownerUUID,
		String ownerName,
		ItemStack sellingItem,
		int sellingAmount,
		ItemStack priceItem,
		int priceAmount,
		long creationTime
	) {
		this.id = id;
		this.location = location;
		this.ownerUUID = ownerUUID;
		this.ownerName = ownerName;
		this.sellingItem = sellingItem.clone();
		this.sellingAmount = sellingAmount;
		this.priceItem = priceItem.clone();
		this.priceAmount = priceAmount;
		this.creationTime = creationTime;
	}

	public UUID getId() {
		return id;
	}

	public Location getLocation() {
		return location.clone();
	}

	public UUID getOwnerUUID() {
		return ownerUUID;
	}

	public String getOwnerName() {
		return ownerName;
	}

	public ItemStack getSellingItem() {
		return sellingItem.clone();
	}

	public int getSellingAmount() {
		return sellingAmount;
	}

	public ItemStack getPriceItem() {
		return priceItem.clone();
	}

	public int getPriceAmount() {
		return priceAmount;
	}

	public long getCreationTime() {
		return creationTime;
	}

	public boolean isValid() {
		return (
			sellingAmount > 0 &&
			sellingAmount <= sellingItem.getMaxStackSize() &&
			priceAmount > 0 &&
			priceAmount <= priceItem.getMaxStackSize()
		);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		Shop shop = (Shop) o;
		return id.equals(shop.id);
	}

	@Override
	public int hashCode() {
		return id.hashCode();
	}
}
