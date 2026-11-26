package dev.quiqzy.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.inventory.Slot;

import dev.quiqzy.client.gui.engine.ZeroBlur;
import dev.quiqzy.client.gui.engine.ZeroRender;
import dev.quiqzy.client.module.ModuleManager;

/**
 * Glass Inventory: Vanilla-Panel durch graues Glas ersetzen (gleicher Look wie Hotbar/Leisten),
 * Slot-Zellen und Hover-Markierung rund & grau. Slots und Spieler-Vorschau bleiben.
 */
@Mixin(AbstractContainerScreen.class)
public abstract class GlassInventoryMixin {
	@Shadow
	protected int leftPos;
	@Shadow
	protected int topPos;
	@Shadow
	protected int imageWidth;
	@Shadow
	protected int imageHeight;
	@Shadow
	protected Slot hoveredSlot;

	@Shadow
	protected abstract void renderBg(GuiGraphics g, float pt, int mouseX, int mouseY);

	private boolean zero$apply() {
		var m = ModuleManager.GLASS_INVENTORY;
		if (m == null || !m.isEnabled()) return false;
		Object self = this;
		if (self instanceof CreativeModeInventoryScreen) return false; // Tabs/Scrollbar -> nicht anfassen
		return m.allScreens.on() || self instanceof InventoryScreen;
	}

	/** Blur des Spielbilds hinter dem Inventar vorbereiten (immer, damit das Glas durchscheint). */
	@Inject(method = "render", at = @At("HEAD"), require = 0)
	private void zero$blur(GuiGraphics g, int mouseX, int mouseY, float pt, CallbackInfo ci) {
		if (zero$apply()) {
			try {
				ZeroBlur.capture();
			} catch (Throwable ignored) {
			}
		}
	}

	/** Vanilla-Panel ersetzen: graues Glas + runde Slot-Zellen, Spieler-Vorschau neu zeichnen. renderBg ist abstrakt -> Aufruf in renderBackground umleiten. */
	@Redirect(method = "renderBackground", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/gui/screens/inventory/AbstractContainerScreen;renderBg(Lnet/minecraft/client/gui/GuiGraphics;FII)V"), require = 0)
	private void zero$bg(AbstractContainerScreen<?> self, GuiGraphics g, float pt, int mouseX, int mouseY) {
		if (!zero$apply()) {
			renderBg(g, pt, mouseX, mouseY);
			return;
		}
		var m = ModuleManager.GLASS_INVENTORY;
		float x1 = leftPos - 7f, y1 = topPos - 7f, x2 = leftPos + imageWidth + 7f, y2 = topPos + imageHeight + 7f;
		boolean prevStroke = ZeroRender.noStroke;
		ZeroRender.noTint = true;
		ZeroRender.noStroke = true;
		try {
			ZeroRender.glow(g, x1, y1 + 5f, x2, y2 + 7f, 14f, 18f, ZeroRender.alpha(0xFF000000, 0.40f));
			ZeroRender.panel(g, x1, y1, x2, y2, 14f, 0x9E14161C, 0x14FFFFFF, 1f);
			// heller Glanz oben + feine helle Kante
			ZeroRender.gradient(g, x1, y1, x2, y1 + 18f, 14f, ZeroRender.alpha(0xFFFFFFFF, 0.06f), 0x00FFFFFF, 90f);
			ZeroRender.stroke(g, x1, y1, x2, y2, 14f, 0.8f, ZeroRender.alpha(0xFFFFFFFF, 0.14f));
			if (m.cells.on()) {
				for (Slot s : self.getMenu().slots) {
					if (!s.isActive()) continue;
					float sx = leftPos + s.x, sy = topPos + s.y;
					ZeroRender.rect(g, sx - 1f, sy - 1f, sx + 17f, sy + 17f, 4f, 0x40000000);
					ZeroRender.rect(g, sx - 1f, sy - 1f, sx + 17f, sy, 4f, 0x12FFFFFF);
				}
			}
			// Portrait-Rahmen für die Spieler-Vorschau (nur Spieler-Inventar)
			if (self instanceof InventoryScreen) {
				ZeroRender.rect(g, leftPos + 24f, topPos + 7f, leftPos + 77f, topPos + 79f, 6f, 0x55000000);
			}
		} finally {
			ZeroRender.noTint = false;
			ZeroRender.noStroke = prevStroke;
		}
	}

	/** Spieler-Vorschau NACH den Slots zeichnen, damit sie garantiert oben liegt (nicht hinter dem Glas). */
	@Inject(method = "renderContents", at = @At("TAIL"), require = 0)
	private void zero$preview(GuiGraphics g, int mouseX, int mouseY, float pt, CallbackInfo ci) {
		if (!zero$apply() || !((Object) this instanceof InventoryScreen) || Minecraft.getInstance().player == null) return;
		try {
			InventoryScreen.renderEntityInInventoryFollowsMouse(g, leftPos + 26, topPos + 8, leftPos + 75, topPos + 78,
					30, 0.0625f, mouseX, mouseY, Minecraft.getInstance().player);
		} catch (Throwable ignored) {
		}
	}

	/** Weiße Vanilla-Hover-Markierung durch rundes graues Highlight ersetzen (hinter dem Item). */
	@Inject(method = "renderSlotHighlightBack", at = @At("HEAD"), cancellable = true, require = 0)
	private void zero$highlightBack(GuiGraphics g, CallbackInfo ci) {
		if (!zero$apply()) return;
		ci.cancel();
		if (hoveredSlot == null || !hoveredSlot.isActive()) return;
		float sx = leftPos + hoveredSlot.x, sy = topPos + hoveredSlot.y;
		ZeroRender.noTint = true;
		ZeroRender.glow(g, sx - 1f, sy - 1f, sx + 17f, sy + 17f, 4f, 4f, ZeroRender.alpha(0xFFFFFFFF, 0.18f));
		ZeroRender.rect(g, sx - 1f, sy - 1f, sx + 17f, sy + 17f, 4f, 0x59C8CCD6);
		ZeroRender.noTint = false;
	}

	/** Vordere weiße Markierung (über dem Item) weglassen – sonst wird das Item überdeckt. */
	@Inject(method = "renderSlotHighlightFront", at = @At("HEAD"), cancellable = true, require = 0)
	private void zero$highlightFront(GuiGraphics g, CallbackInfo ci) {
		if (zero$apply()) ci.cancel();
	}

	/** Dunkle Vanilla-Titel ("Inventory" usw.) ausblenden – sonst unlesbar auf dem Glas. */
	@Inject(method = "renderLabels", at = @At("HEAD"), cancellable = true, require = 0)
	private void zero$labels(GuiGraphics g, int mouseX, int mouseY, CallbackInfo ci) {
		if (zero$apply() && !ModuleManager.GLASS_INVENTORY.titles.on()) ci.cancel();
	}
}
