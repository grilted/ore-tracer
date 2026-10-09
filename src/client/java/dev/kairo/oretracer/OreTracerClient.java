package dev.kairo.oretracer;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

public final class OreTracerClient implements ClientModInitializer {
    private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(
            Identifier.fromNamespaceAndPath("oretracer", "controls"));

    @Override
    public void onInitializeClient() {
        OreTracerConfig config = OreTracerConfig.load();
        OreScanner scanner = new OreScanner(config);
        OreRenderer renderer = new OreRenderer(config, scanner);

        KeyMapping toggleKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.oretracer.toggle", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F8, CATEGORY));
        KeyMapping menuKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.oretracer.menu", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_O, CATEGORY));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (toggleKey.consumeClick()) {
                config.enabled = !config.enabled;
                config.save();
                if (!config.enabled) {
                    scanner.clear();
                }
                if (client.player != null) {
                    ChatFormatting color = config.enabled ? ChatFormatting.GREEN : ChatFormatting.RED;
                    client.player.sendOverlayMessage(
                            Component.literal("Ore Tracer " + (config.enabled ? "enabled" : "disabled"))
                                    .withStyle(color));
                }
            }

            while (menuKey.consumeClick()) {
                client.gui.setScreen(new OreTracerScreen(client.gui.screen(), config));
            }

            scanner.tick(client);
        });

        LevelExtractionEvents.END_EXTRACTION.register(renderer::extract);
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> OreType.registerOptionalOres());
        LevelRenderEvents.COLLECT_SUBMITS.register(renderer::submitLabels);
        LevelRenderEvents.AFTER_TRANSLUCENT_TERRAIN.register(renderer::render);
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> renderer.close());
    }
}
