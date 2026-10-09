package dev.lowball.helper.client.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(AbstractContainerScreen.class)
public interface AbstractContainerScreenAccessor {
	@Accessor("leftPos")
	int lowball$leftPos();

	@Accessor("topPos")
	int lowball$topPos();

	@Accessor("imageWidth")
	int lowball$imageWidth();

	@Accessor("imageHeight")
	int lowball$imageHeight();

	@Accessor("hoveredSlot")
	@Nullable Slot lowball$hoveredSlot();
}
