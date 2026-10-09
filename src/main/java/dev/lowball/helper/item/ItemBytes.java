package dev.lowball.helper.item;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;

import dev.lowball.helper.util.Text;

/** Decodes the base64 gzipped NBT in Hypixel API {@code item_bytes}. */
public final class ItemBytes {
	private ItemBytes() {
	}

	public static List<SkyblockItem> decode(String base64, String fallbackName) throws IOException {
		byte[] raw = Base64.getDecoder().decode(base64);
		CompoundTag root = NbtIo.readCompressed(new ByteArrayInputStream(raw), NbtAccounter.create(8L << 20));
		ListTag list = root.getListOrEmpty("i");
		List<SkyblockItem> out = new ArrayList<>(1);
		for (int i = 0; i < list.size(); i++) {
			CompoundTag entry = list.getCompoundOrEmpty(i);
			CompoundTag tag = entry.getCompoundOrEmpty("tag");
			CompoundTag attrs = tag.getCompoundOrEmpty("ExtraAttributes");
			CompoundTag display = tag.getCompoundOrEmpty("display");
			String name = display.getStringOr("Name", fallbackName);
			int count = entry.getIntOr("Count", 1);
			int color = display.contains("color") ? display.getIntOr("color", -1) : -1;
			SkyblockItem item = SkyblockItem.of(attrs, Text.strip(name), count, color);
			if (item != null) {
				out.add(item);
			}
		}
		return out;
	}

	public static SkyblockItem decodeFirst(String base64, String fallbackName) throws IOException {
		List<SkyblockItem> items = decode(base64, fallbackName);
		return items.isEmpty() ? null : items.get(0);
	}
}
