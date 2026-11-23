package dev.quiqzy.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;

import dev.quiqzy.client.module.ModuleManager;

/** Fast Place / Fast Use: setzt den Rechtsklick-Cooldown (rightClickDelay) auf 0. */
@Mixin(Minecraft.class)
public abstract class FastActionMixin {
	@Shadow
	private int rightClickDelay;
	@Shadow
	public LocalPlayer player;

	@Inject(method = "handleKeybinds", at = @At("HEAD"), require = 0)
	private void zero$fast(CallbackInfo ci) {
		if (player == null || rightClickDelay <= 0) return;
		ItemStack held = player.getMainHandItem();
		boolean block = held.getItem() instanceof BlockItem;
		var fp = ModuleManager.FAST_PLACE;
		var fu = ModuleManager.FAST_USE;
		if (block && fp != null && fp.isEnabled()) rightClickDelay = 0;
		else if (!block && fu != null && fu.isEnabled()) rightClickDelay = 0;
	}
}
