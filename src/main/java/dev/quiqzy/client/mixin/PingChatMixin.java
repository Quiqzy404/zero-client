package dev.quiqzy.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.GuiMessageTag;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;

import dev.quiqzy.client.module.misc.PingModule;

/**
 * Fängt eingehende Chat-Zeilen ab: ist es ein geteilter ZERO-Ping ("ZPING x y z dim name"),
 * wird daraus eine Welt-Markierung und die Chat-Zeile versteckt. Sonst normal anzeigen.
 */
@Mixin(ChatComponent.class)
public abstract class PingChatMixin {

	@Inject(method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/GuiMessageTag;)V",
			at = @At("HEAD"), cancellable = true, require = 0)
	private void zero$catchPing(Component msg, MessageSignature signature, GuiMessageTag tag, CallbackInfo ci) {
		try {
			if (msg == null) return;
			// Immer empfangen: handleIncoming bricht nur bei echten ZERO-Markern ab, normale
			// Chat-Zeilen gibt es ganz normal zurück. So sehen Freunde geteilte Marker auch dann,
			// wenn ihr Marker-Ping-Modul gerade aus ist (Hauptsache sie haben den Client).
			if (PingModule.handleIncoming(msg.getString())) ci.cancel();
		} catch (Throwable ignored) {
		}
	}
}
