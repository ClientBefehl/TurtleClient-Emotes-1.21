package net.quepierts.simpleanimator.core.network;

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
        System.out.println(message);
        String[] parts = message.split(":");
        if (parts.length == 2) {
            String playerId = parts[0];
            String emote = parts[1];

            // Hier kannst du die Logik hinzufügen, um das Emote für den Spieler mit playerId auszuführen
            System.out.println("Received emote: " + emote + " from player: " + playerId);

//            // Füge hier die Logik hinzu, um das Emote für den Spieler auszuführen
//            ClientAnimator animator = SimpleAnimator.getProxy().getAnimatorManager().getAnimator(UUID.fromString(playerId));
//            if (animator != null) {
//                animator.play(emote); // Stelle sicher, dass die Methode zum Abspielen des Emotes korrekt ist
//            }
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
