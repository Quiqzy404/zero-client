package dev.zeroinject.payload;

import dev.quiqzy.client.QuiqzyClient;
import dev.quiqzy.client.config.QuiqzyConfig;
import dev.quiqzy.client.gui.engine.EngineTest;
import dev.quiqzy.client.gui.engine.ZeroPipelines;
import dev.quiqzy.client.hud.ZeroHud;
import dev.quiqzy.client.module.ModuleManager;
import dev.quiqzy.client.render.*;
import dev.quiqzy.client.util.SpawnerTracker;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.EndTick;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents.EndMain;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

/**
 * Live-inject entrypoint — called by zero-agent.jar via agentmain.
 * Guards against double-init because this class stays loaded in MC's classloader
 * after the first inject; a second inject will find INITIALIZED=true and return.
 */
public final class Payload {

    private static volatile boolean INITIALIZED = false;

    public static void init() {
        if (INITIALIZED) {
            System.out.println("[ZERO payload] already initialized, skipping");
            return;
        }
        INITIALIZED = true;
        try {
            // NOTE: KeyBindingHelper.registerKeyBinding() is NOT called here — that's
            // startup-only. Keys registered in QuiqzyClient static fields are already
            // wired by the classloader; they still work after inject.

            dev.quiqzy.client.update.ZeroUpdater.checkAsync();
            MarkerRelay.start();
            ZeroPipelines.init();
            dev.quiqzy.client.mixin.SpecialModelRenderersAccessor.zero$idMapper()
                .put(Identifier.fromNamespaceAndPath("quiqzy", "mesh"),
                     dev.quiqzy.client.render.MeshSpecialRenderer.Unbaked.MAP_CODEC);
            EngineTest.init();
            QuiqzyConfig.load();

            ClientTickEvents.START_CLIENT_TICK.register(ModuleManager::startTick);
            dev.quiqzy.client.util.ReplaceCommand.register();

            ClientTickEvents.END_CLIENT_TICK.register((EndTick) mc -> {
                while (QuiqzyClient.OPEN_GUI.consumeClick()) {
                    if (mc.screen == null) {
                        dev.quiqzy.client.util.UiSounds.gui(true);
                        mc.setScreen(new dev.quiqzy.client.gui.AuroraGui());
                    }
                }
                while (QuiqzyClient.ADD_WAYPOINT.consumeClick()) {
                    if (ModuleManager.WAYPOINTS.isEnabled()) {
                        ModuleManager.WAYPOINTS.addHere(mc, "");
                        dev.quiqzy.client.util.UiSounds.added();
                    }
                }
                while (QuiqzyClient.LAUNCH_KEY.consumeClick()) {
                    if (ModuleManager.INSTANT_LAUNCH.isEnabled()) {
                        ModuleManager.INSTANT_LAUNCH.launch(mc);
                    }
                }
                while (QuiqzyClient.INSPECT.consumeClick()) {
                    ModuleManager.WEAPON_SKINS.trick();
                }
                while (QuiqzyClient.HUD_EDITOR.consumeClick()) {
                    if (mc.screen == null) mc.setScreen(new dev.quiqzy.client.hud.HudEditorScreen());
                }
                SpawnerTracker.tick(mc);
                ModuleManager.tick(mc);
            });

            ClientChunkEvents.CHUNK_LOAD.register(ModuleManager.SUS_CHUNK::onChunkLoad);
            ClientChunkEvents.CHUNK_LOAD.register(ModuleManager.BASE_FINDER::onChunkLoad);
            ClientChunkEvents.CHUNK_UNLOAD.register(ModuleManager.BASE_FINDER::onChunkUnload);
            ClientChunkEvents.CHUNK_UNLOAD.register(ModuleManager.SUS_CHUNK::onChunkUnload);
            ClientChunkEvents.CHUNK_UNLOAD.register(ModuleManager.SOUND_CHUNK::onChunkUnload);
            ClientChunkEvents.CHUNK_LOAD.register(ModuleManager.BLOCK_ESP::onChunkLoad);
            ClientChunkEvents.CHUNK_UNLOAD.register(ModuleManager.BLOCK_ESP::onChunkUnload);
            ClientChunkEvents.CHUNK_LOAD.register(SpawnerTracker::onChunkLoad);
            ClientChunkEvents.CHUNK_UNLOAD.register(SpawnerTracker::onChunkUnload);
            ClientChunkEvents.CHUNK_LOAD.register(ModuleManager.XRAY_RESEARCH::onChunkLoad);
            ClientChunkEvents.CHUNK_UNLOAD.register(ModuleManager.XRAY_RESEARCH::onChunkUnload);
            ClientChunkEvents.CHUNK_LOAD.register(ModuleManager.ACTIVITY_INSPECTOR::onChunkLoad);
            ClientChunkEvents.CHUNK_UNLOAD.register(ModuleManager.ACTIVITY_INSPECTOR::onChunkUnload);
            ClientChunkEvents.CHUNK_LOAD.register(ModuleManager.BASE_RESEARCH::onChunkLoad);
            ClientChunkEvents.CHUNK_UNLOAD.register(ModuleManager.BASE_RESEARCH::onChunkUnload);

            GlossyFloorRenderer.init();
            HandGlowPass.init();
            ChamsRenderer.init();
            SkyRenderer.init();
            EspShaderRenderer.init();
            WorldRenderEvents.BEFORE_TRANSLUCENT.register(SkyRenderer::render);
            WorldRenderEvents.BEFORE_TRANSLUCENT.register(GlossyFloorRenderer::render);
            ActivityInspectorRenderer.init();
            WorldRenderEvents.BEFORE_TRANSLUCENT.register(ActivityInspectorRenderer::render);
            BaseFinderResearchRenderer.init();
            WorldRenderEvents.BEFORE_TRANSLUCENT.register(BaseFinderResearchRenderer::render);
            XrayResearchRenderer.init();
            WorldRenderEvents.BEFORE_TRANSLUCENT.register(XrayResearchRenderer::render);
            SusChunkRenderer.init();
            WorldRenderEvents.BEFORE_TRANSLUCENT.register(SusChunkRenderer::render);
            MarkerRenderer.init();
            WorldRenderEvents.BEFORE_TRANSLUCENT.register(MarkerRenderer::render);
            SoundChunkRenderer.init();
            WorldRenderEvents.BEFORE_TRANSLUCENT.register(SoundChunkRenderer::render);
            SpawnerRenderer.init();
            WorldRenderEvents.BEFORE_TRANSLUCENT.register(SpawnerRenderer::render);
            BlockEspRenderer.init();
            WorldRenderEvents.BEFORE_TRANSLUCENT.register(BlockEspRenderer::render);
            EspRenderer.init();
            WorldRenderEvents.BEFORE_TRANSLUCENT.register(EspRenderer::render);
            GuiWorldRenderer.init();
            StorageEspRenderer.init();
            WorldRenderEvents.BEFORE_TRANSLUCENT.register(StorageEspRenderer::render);
            WorldRenderEvents.BEFORE_TRANSLUCENT.register(BoneMealBotRenderer::render);
            WorldRenderEvents.BEFORE_TRANSLUCENT.register(GuiWorldRenderer::render);
            WorldRenderEvents.BEFORE_TRANSLUCENT.register(FxRenderer::render);
            WorldRenderEvents.BEFORE_TRANSLUCENT.register(GlowWingsRenderer::render);
            WorldRenderEvents.BEFORE_TRANSLUCENT.register(CardFxRenderer::render);
            WorldRenderEvents.END_MAIN.register((EndMain) context -> ModuleManager.frame(Minecraft.getInstance()));

            HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT,
                Identifier.fromNamespaceAndPath("quiqzy", "hud"),
                (g, dt) -> ZeroHud.render(g, dt));

            System.out.println("[ZERO payload] init complete - GUI on Right Shift");
        } catch (Throwable t) {
            INITIALIZED = false; // allow retry on failure
            System.out.println("[ZERO payload] init failed: " + t);
            t.printStackTrace(System.out);
        }
    }
}
