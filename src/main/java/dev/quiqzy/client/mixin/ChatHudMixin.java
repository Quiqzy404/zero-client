package dev.quiqzy.client.mixin;

import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.input.MouseButtonEvent;

import dev.quiqzy.client.hud.HudDrag;
import dev.quiqzy.client.hud.ZeroHud;
import dev.quiqzy.client.module.ModuleManager;

/** HUD im Chat verschieben: Klick auf ein HUD-Element zieht es, sonst normales Chat-Verhalten. */
@Mixin(ChatScreen.class)
public abstract class ChatHudMixin {
	private static boolean zero$on() {
		var a = ModuleManager.ACCENT;
		return a != null && a.chatEdit.on();
	}

	@Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true, require = 0)
	private void zero$click(MouseButtonEvent event, boolean dbl, CallbackInfoReturnable<Boolean> cir) {
		if (zero$on() && HudDrag.click(event.x(), event.y(), event.button())) cir.setReturnValue(true);
	}

	@Inject(method = "render", at = @At("TAIL"), require = 0)
	private void zero$render(GuiGraphics g, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		if (!zero$on()) return;
		Minecraft mc = Minecraft.getInstance();
		// laufendes Ziehen aktualisieren / beenden (Chat hat kein mouseDragged/Released)
		if (HudDrag.dragging()) {
			HudDrag.drag(mouseX, mouseY);
			boolean down = GLFW.glfwGetMouseButton(mc.getWindow().handle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
			if (!down) HudDrag.release();
		}
		float s = ZeroHud.lastScale / ZeroHud.lastK;
		if (s <= 0f || Float.isNaN(s)) return;
		g.pose().pushMatrix();
		g.pose().scale(s);
		HudDrag.outlines(g, mouseX, mouseY, true);
		g.pose().popMatrix();
	}
}
