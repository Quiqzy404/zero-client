package dev.quiqzy.client.module.tiersmc;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

import dev.quiqzy.client.module.Module;
import dev.quiqzy.client.module.Setting;

/**
 * Gemeinsame Basis der TiersMC/DonutLarp-Module (wie im DonutLarp-Meteor-Addon):
 * Toast/Chat/Sound + Render-Settings (Max Distance, Y-Level, Height, Shape Mode, Side/Line Color).
 */
public abstract class DLModule extends Module {

	private static final SystemToast.SystemToastId TOAST = new SystemToast.SystemToastId(5000L);

	public final Setting.Bool toast;
	public final Setting.Bool chat;
	public final Setting.Bool sound;
	public final Setting.Int maxDist;
	public final Setting.Int yLevel;
	public final Setting.Int height;
	public final Setting.Choice shape = add(new Setting.Choice("shape_mode", "Shape Mode", "Both", "Both", "Sides", "Lines"));
	public final Setting.Color sideColor;
	public final Setting.Int sideAlpha;
	public final Setting.Color lineColor;

	protected DLModule(String id, String name, String desc, boolean hasToast, int dist, int y, int sideRgb, int sideA, int lineRgb) {
		super(id, name, desc, Category.TIERSMC);
		toast = hasToast ? add(new Setting.Bool("toast", "Achievement Toast", true)) : null;
		chat = add(new Setting.Bool("chat", "Chat Message", true));
		sound = add(new Setting.Bool("sound", "Sound", true));
		maxDist = add(new Setting.Int("max_distance", "Max Distance", dist, 1, 256, " chunks"));
		yLevel = add(new Setting.Int("y_level", "Y-Level", y, -64, 320));
		height = add(new Setting.Int("height", "Height", 1, 1, 64, " blocks"));
		sideColor = add(new Setting.Color("side_color", "Side Color", sideRgb));
		sideAlpha = add(new Setting.Int("side_alpha", "Side Opacity", sideA, 0, 255));
		lineColor = add(new Setting.Color("line_color", "Line Color", lineRgb));
	}

	public boolean sides() { return !shape.get().equals("Lines"); }

	public boolean lines() { return !shape.get().equals("Sides"); }

	/** Meldung bei neuem Fund – Text kommt nur aus Zahlen, nie aus Server-Strings. */
	protected void announce(String title, String chatText, SoundEvent snd, float pitch) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) return;
		if (toast != null && toast.on()) {
			SystemToast.addOrUpdate(mc.getToastManager(), TOAST, Component.literal(title), Component.literal(chatText));
		}
		if (chat.on()) {
			mc.player.displayClientMessage(Component.literal("§6TiersMC §7» §f" + chatText), false);
		}
		if (sound.on() && snd != null) {
			mc.player.playSound(snd, 0.6f, pitch);
		}
	}

	protected static SoundEvent challenge() {
		return SoundEvents.UI_TOAST_CHALLENGE_COMPLETE;
	}

	protected static SoundEvent ding() {
		return SoundEvents.EXPERIENCE_ORB_PICKUP;
	}
}
