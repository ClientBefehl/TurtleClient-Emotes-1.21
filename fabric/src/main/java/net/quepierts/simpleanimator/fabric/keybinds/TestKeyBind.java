package net.quepierts.simpleanimator.fabric.keybinds;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.quepierts.simpleanimator.api.IAnimateHandler;
import net.quepierts.simpleanimator.core.network.EmoteWebSocketClient;
import net.quepierts.simpleanimator.fabric.SimpleAnimatorFabric;
import org.lwjgl.glfw.GLFW;

import java.net.URI;

public class TestKeyBind {
    public static KeyMapping EMOTE_WHEEL;

    public static void register() {
        EMOTE_WHEEL = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.turtleclient.emote_wheel",
                GLFW.GLFW_KEY_B,  // Standard-Taste B
                "category.turtleclient.general"
        ));

        // Client Tick Event registrieren
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (EMOTE_WHEEL.consumeClick()) {
                //Minecraft.getInstance().setScreen(new EmoteScreen());

                // Animation abspielen
                LocalPlayer player = Minecraft.getInstance().player;
                if (player instanceof IAnimateHandler handler) {
                    playAnimation(handler, "wave");
                    player.sendSystemMessage(Component.literal("\u00a7aAnimation wird abgespielt!"));
                } else {
                    player.sendSystemMessage(Component.literal("\u00a7cFehler: Kann Animation nicht abspielen!"));
                }
            }
        });
    }

    public static void playAnimation(IAnimateHandler entity, String animationName) {
        ResourceLocation animationId = ResourceLocation.fromNamespaceAndPath("turtleclient-emotes", animationName);
        entity.simpleanimator$getAnimator().play(animationId);

        // Verwende den WebSocket-Client, der in der Hauptklasse initialisiert wurde
        EmoteWebSocketClient client = SimpleAnimatorFabric.getClient();

        // Überprüfe, ob die Verbindung erfolgreich ist, bevor du das Emote sendest
        if (client != null && client.isOpen()) {
            String playerId = Minecraft.getInstance().player.getUUID().toString(); // UUID des Spielers
            client.sendEmote(animationName, playerId);
        } else {
            System.out.println("WebSocket client is not connected. Cannot send emote.");
        }
    }



}
