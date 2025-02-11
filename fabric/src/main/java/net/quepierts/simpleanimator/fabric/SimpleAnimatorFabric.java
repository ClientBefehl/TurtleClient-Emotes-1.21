package net.quepierts.simpleanimator.fabric;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.quepierts.simpleanimator.core.SimpleAnimator;
import net.quepierts.simpleanimator.core.network.EmoteWebSocketClient;
import net.quepierts.simpleanimator.fabric.keybinds.TestKeyBind;
import net.quepierts.simpleanimator.fabric.network.FabricClientNetworkImpl;
import net.quepierts.simpleanimator.fabric.network.FabricNetworkImpl;
import net.quepierts.simpleanimator.fabric.proxy.FabricClientProxy;
import net.quepierts.simpleanimator.fabric.proxy.FabricCommonProxy;

import java.net.URI;

public class SimpleAnimatorFabric implements ModInitializer {
    private static EmoteWebSocketClient client;

    @Override
    public void onInitialize() {
        boolean isClient = FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT;
        SimpleAnimator.init(
                isClient,
                FabricCommonProxy::setup,
                FabricClientProxy::setup,
                isClient ? new FabricClientNetworkImpl() : new FabricNetworkImpl()
        );

        // WebSocket-Client initialisieren und verbinden
        client = new EmoteWebSocketClient(URI.create("ws://localhost:8080/emotes"));
        client.connect(); // Verbindung zum Server herstellen

        TestKeyBind.register();
    }

    public static EmoteWebSocketClient getClient() {
        return client;
    }
}
