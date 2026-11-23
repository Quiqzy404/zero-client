package dev.quiqzy.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;

import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.world.item.ItemStack;

import dev.quiqzy.client.module.ModuleManager;

/**
 * Item Skins: tauscht die Anzeige des Items (nur beim Zeichnen) gegen das
 * Weihnachts-Modell – GUI, Hand und Totem-Pop laufen alle ueber appendItemLayers.
 */
@Mixin(ItemModelResolver.class)
public abstract class ItemModelResolverMixin {

	@ModifyVariable(method = "appendItemLayers(Lnet/minecraft/client/renderer/item/ItemStackRenderState;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/ItemOwner;I)V", at = @At("HEAD"), argsOnly = true, index = 2, require = 0)
	private ItemStack zero$itemSkins(ItemStack stack) {
		if (stack == null || ModuleManager.ITEM_SKINS == null) return stack;
		return ModuleManager.ITEM_SKINS.skin(stack);
	}

	@Shadow
	public abstract void updateForLiving(ItemStackRenderState state, ItemStack stack, ItemDisplayContext ctx, LivingEntity entity);

	@Unique
	private boolean zero$inSkin;

	/** Weapon Skins auch in 3rd Person: fuer den eigenen Spieler das Schwert gegen den Skin tauschen. */
	@Inject(method = "updateForLiving", at = @At("HEAD"), cancellable = true, require = 0)
	private void zero$weaponSkin3rd(ItemStackRenderState state, ItemStack stack, ItemDisplayContext ctx, LivingEntity entity, CallbackInfo ci) {
		if (zero$inSkin || ctx.firstPerson() || entity != Minecraft.getInstance().player) return;
		if (ctx != ItemDisplayContext.THIRD_PERSON_RIGHT_HAND && ctx != ItemDisplayContext.THIRD_PERSON_LEFT_HAND) return;
		if (!ModuleManager.WEAPON_SKINS.applies(stack)) return;
		zero$inSkin = true;
		try {
			updateForLiving(state, ModuleManager.WEAPON_SKINS.skin(stack), ctx, entity);
		} finally {
			zero$inSkin = false;
		}
		ci.cancel();
	}
}
