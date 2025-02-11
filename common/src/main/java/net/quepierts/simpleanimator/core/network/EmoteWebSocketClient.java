package net.quepierts.simpleanimator.core.network;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.quepierts.simpleanimator.api.IAnimateHandler;
import net.quepierts.simpleanimator.core.SimpleAnimator;
import net.quepierts.simpleanimator.core.client.ClientAnimator;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;
import java.util.UUID;

public class EmoteWebSocketClient extends WebSocketClient {

    public EmoteWebSocketClient(URI serverUri) {
        super(serverUri);
    }

    @Override
    public void onOpen(ServerHandshake handshakedata) {
        System.out.println("Connected to server with status: " + getReadyState());
    }

    @Override
    public void onMessage(String message) {
        System.out.println("Received message: " + message);
        String[] parts = message.split(":");
        if (parts.length == 2) {
            String playerId = parts[0];
            String emote = parts[1];

            System.out.println("Received emote: " + emote + " from player: " + playerId);

            // Erstelle eine ResourceLocation für die Animation
            ResourceLocation animationId = ResourceLocation.tryParse("turtleclient-emotes:" + emote);

            // Hole den Spieler mit der entsprechenden UUID
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.level != null) {
                UUID uuid = UUID.fromString(playerId);
                Player player = minecraft.level.getPlayerByUUID(uuid);

                if (player != null && player instanceof IAnimateHandler) {
                    // Spiele die Animation für den gefundenen Spieler ab
                    IAnimateHandler animateHandler = (IAnimateHandler) player;
                    animateHandler.simpleanimator$getAnimator().play(animationId);
                } else {
                    System.out.println("Player not found or doesn't implement IAnimateHandler: " + playerId);
                }
            }
        }
    }



    @Override
    public void onClose(int code, String reason, boolean remote) {
        System.out.println("Connection closed: " + reason);
    }

    @Override
    public void onError(Exception ex) {
        ex.printStackTrace();
    }

    public void sendEmote(String emote, String playerId) {
        if (isOpen()) {
            String message = playerId + ":" + emote;
            System.out.println("Connection state before sending: " + getReadyState());
            System.out.println("Attempting to send message: " + message);
            send(message);
            System.out.println("Send call completed");
        } else {
            System.out.println("Cannot send - WebSocket not open");
        }
    }

}
