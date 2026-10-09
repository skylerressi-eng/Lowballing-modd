package dev.lowball.helper.client.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(AbstractSignEditScreen.class)
public interface AbstractSignEditScreenAccessor {
	@Accessor("messages")
	String[] lowball$messages();

	@Accessor("line")
	void lowball$setLine(int line);

	@Invoker("setMessage")
	void lowball$setMessage(String message);
}
