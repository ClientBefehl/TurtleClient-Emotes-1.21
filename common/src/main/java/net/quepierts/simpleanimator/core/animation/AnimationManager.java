package net.quepierts.simpleanimator.core.animation;

import com.google.common.collect.ImmutableMap;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import net.minecraft.Util;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.players.PlayerList;
import net.minecraft.util.profiling.ProfilerFiller;
import net.quepierts.simpleanimator.api.animation.Animation;
import net.quepierts.simpleanimator.api.animation.Interaction;
import net.quepierts.simpleanimator.core.SimpleAnimator;
import net.quepierts.simpleanimator.core.network.packet.batch.ClientUpdateAnimationPacket;
import net.quepierts.simpleanimator.core.network.packet.batch.ClientUpdateInteractionPacket;
import net.quepierts.simpleanimator.core.network.packet.batch.PacketCache;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class AnimationManager implements PreparableReloadListener {
    public static final FileToIdConverter ANIMATION_LISTER = FileToIdConverter.json("animations");
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Path ANIMATIONS_PATH = Path.of("animations");
    private static final String GITHUB_RAW_URL = "https://raw.githubusercontent.com/TurtleClient/TurtleAssets/main/data/turtleclient-emotes/animations/";
    private static final String GITHUB_API_URL = "https://api.github.com/repos/TurtleClient/TurtleAssets/contents/data/turtleclient-emotes/animations";

    private ImmutableMap<ResourceLocation, Animation> animations;
    private ImmutableMap<ResourceLocation, Interaction> interactions;

    private final PacketCache cacheAnimations = new PacketCache();
    private final PacketCache cacheInteractions = new PacketCache();

    @Nullable
    public Animation getAnimation(ResourceLocation location) {
        return animations.get(location);
    }

    @Nullable
    public Interaction getInteraction(ResourceLocation location) {
        return interactions.get(location);
    }

    private void downloadAnimations() {
        try {
            // Create animations directory if it doesn't exist
            Files.createDirectories(ANIMATIONS_PATH);

            // Fetch directory listing from GitHub API
            URL apiUrl = new URL(GITHUB_API_URL);
            HttpURLConnection conn = (HttpURLConnection) apiUrl.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Accept", "application/vnd.github.v3+json");

            if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                    String response = reader.lines().collect(Collectors.joining());
                    JsonParser.parseString(response).getAsJsonArray().forEach(element -> {
                        JsonObject file = element.getAsJsonObject();
                        String fileName = file.get("name").getAsString();

                        if (fileName.endsWith(".json")) {
                            downloadFile(fileName);
                        }
                    });
                }
            }
        } catch (IOException e) {
            LOGGER.error("Failed to download animations from GitHub", e);
        }
    }

    private void downloadFile(String fileName) {
        try {
            URL fileUrl = new URL(GITHUB_RAW_URL + fileName);
            Path targetPath = ANIMATIONS_PATH.resolve(fileName);

            // Download file
            try (InputStream in = fileUrl.openStream()) {
                Files.copy(in, targetPath, StandardCopyOption.REPLACE_EXISTING);
            }
            LOGGER.debug("Downloaded animation: {}", fileName);
        } catch (IOException e) {
            LOGGER.error("Failed to download animation file: {}", fileName, e);
        }
    }

    @Override
    @NotNull
    public CompletableFuture<Void> reload(PreparationBarrier pPreparationBarrier, ResourceManager pResourceManager,
                                          ProfilerFiller pPreparationsProfiler, ProfilerFiller pReloadProfiler,
                                          Executor pBackgroundExecutor, Executor pGameExecutor) {

        // Download animations from GitHub first
        return CompletableFuture.runAsync(this::downloadAnimations, pBackgroundExecutor)
                .thenCompose(v -> {
                    CompletableFuture<List<Pair<ResourceLocation, Animation[]>>> animations = load(pResourceManager, pBackgroundExecutor);

                    return CompletableFuture.allOf(animations)
                            .thenCompose(pPreparationBarrier::wait)
                            .thenAcceptAsync((void_) -> {
                                ImmutableMap.Builder<ResourceLocation, Animation> animationBuilder = ImmutableMap.builder();
                                ImmutableMap.Builder<ResourceLocation, Interaction> interactionBuilder = ImmutableMap.builder();
                                List<Pair<ResourceLocation, Animation[]>> extern = loadExtern();
                                List<Pair<ResourceLocation, Animation[]>> join = animations.join();
                                collect(extern, animationBuilder, interactionBuilder);
                                collect(join, animationBuilder, interactionBuilder);
                                this.animations = animationBuilder.build();
                                this.interactions = interactionBuilder.build();

                                this.cacheAnimations.reset(new ClientUpdateAnimationPacket(this.animations));
                                this.cacheInteractions.reset(new ClientUpdateInteractionPacket(this.interactions));
                            });
                });
    }

    private void collect(
            List<Pair<ResourceLocation, Animation[]>> list,
            ImmutableMap.Builder<ResourceLocation, Animation> animations,
            ImmutableMap.Builder<ResourceLocation, Interaction> interactions
    ) {
        list.stream()
                .flatMap((Function<Pair<ResourceLocation, Animation[]>, Stream<Pair<ResourceLocation, Animation>>>) pair -> {
                    if (pair.getSecond().length > 1) {
                        LOGGER.debug("Load Interaction: {}", pair.getFirst());
                        interactions.put(pair.getFirst(), new Interaction(
                                pair.getFirst().withPrefix(Animation.Type.INVITE.path),
                                pair.getFirst().withPrefix(Animation.Type.REQUESTER.path),
                                pair.getFirst().withPrefix(Animation.Type.RECEIVER.path)
                        ));
                    }
                    return Arrays.stream(pair.getSecond())
                            .map(animation -> new Pair<>(pair.getFirst().withPrefix(animation.getType().path), animation));
                })
                .forEach(pair -> animations.put(pair.getFirst(), pair.getSecond()));
    }

    private CompletableFuture<List<Pair<ResourceLocation, Animation[]>>> load(ResourceManager pResourceManager, Executor pBackgroundExecutor) {
        return CompletableFuture.supplyAsync(() -> ANIMATION_LISTER.listMatchingResourceStacks(pResourceManager), pBackgroundExecutor)
                .thenCompose(map -> {
                    List<CompletableFuture<Pair<ResourceLocation, Animation[]>>> list = new ArrayList<>(map.size());

                    for (Map.Entry<ResourceLocation, List<Resource>> entry : map.entrySet()) {
                        ResourceLocation location = entry.getKey();
                        ResourceLocation resourceLocation = ANIMATION_LISTER.fileToId(location);

                        for (Resource resource : entry.getValue()) {
                            list.add(CompletableFuture.supplyAsync(() -> {
                                try (Reader reader = resource.openAsReader()) {
                                    Animation[] animations = Animation.fromStream(reader);
                                    return Pair.of(resourceLocation, animations);
                                } catch (IOException e) {
                                    LOGGER.warn("Couldn't read animation {} from {} in data pack {}",
                                            resourceLocation, location, resource.sourcePackId());
                                    return null;
                                }
                            }));
                        }
                    }

                    return Util.sequence(list)
                            .thenApply(result -> result.stream()
                                    .filter(Objects::nonNull)
                                    .collect(Collectors.toList())
                            );
                });
    }

    public Set<ResourceLocation> getAnimationNames() {
        return animations.keySet();
    }

    public Set<ResourceLocation> getInteractionNames() {
        return interactions.keySet();
    }

    public List<Pair<ResourceLocation, Animation[]>> loadExtern() {
        List<Pair<ResourceLocation, Animation[]>> animations = new ArrayList<>();

        if (!Files.exists(ANIMATIONS_PATH)) {
            try {
                Files.createDirectories(ANIMATIONS_PATH);
            } catch (IOException e) {
                LOGGER.warn("Failed to create animations directory", e);
            }
            LOGGER.info("Created animations directory");
            return animations;
        }

        try {
            Files.walkFileTree(ANIMATIONS_PATH, new SimpleFileVisitor<>() {
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (Files.isRegularFile(file) && file.toString().endsWith(".json")) {
                        load(file, animations);
                    }
                    return FileVisitResult.CONTINUE;
                }

                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    LOGGER.warn("Failed to visit file: {}", file, exc);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            LOGGER.warn("Failed to load animations: {}", e.getMessage());
        }

        return animations;
    }

    private static void load(Path path, List<Pair<ResourceLocation, Animation[]>> list) {
        try (Reader reader = Files.newBufferedReader(path)) {
            JsonObject object = JsonParser.parseReader(reader).getAsJsonObject();

            String name = path.getFileName().toString();
            name = name.substring(0, name.length() - 5);
            Animation[] animations = Animation.serialize(object);

            LOGGER.debug("Load External Animation: {}", name);
            list.add(Pair.of(
                    ResourceLocation.fromNamespaceAndPath("external", name),
                    animations
            ));
        } catch (RuntimeException | IOException e) {
            LOGGER.warn("Failed to read resource {}", path, e);
        }
    }

    public void handleUpdateAnimations(ClientUpdateAnimationPacket packet) {
        SimpleAnimator.getProxy().getAnimatorManager().reset();
        LOGGER.debug("Sync Animations From Server");
        Map<ResourceLocation, Animation> animations = packet.getAnimations();
        this.animations = ImmutableMap.copyOf(animations);
    }

    public void handleUpdateInteractions(ClientUpdateInteractionPacket packet) {
        LOGGER.debug("Sync Interactions From Server");
        Map<ResourceLocation, Interaction> interactions = packet.getInteractions();
        this.interactions = ImmutableMap.copyOf(interactions);
    }

    public void sync(ServerPlayer player) {
        LOGGER.info("Send Animations[{}] and Interactions[{}] to Client", this.animations.size(), this.interactions.size());
        if (this.cacheAnimations.ready())
            SimpleAnimator.getNetwork().sendToPlayer(this.cacheAnimations, player);

        if (this.cacheInteractions.ready())
            SimpleAnimator.getNetwork().sendToPlayer(this.cacheInteractions, player);
    }

    public void sync(PlayerList list) {
        LOGGER.info("Send Animations[{}] and Interactions[{}] to All Players",
                this.animations.size(), this.interactions.size());

        for (ServerPlayer player : list.getPlayers()) {
            if (this.cacheAnimations.ready())
                SimpleAnimator.getNetwork().sendToPlayer(this.cacheAnimations, player);

            if (this.cacheInteractions.ready())
                SimpleAnimator.getNetwork().sendToPlayer(this.cacheInteractions, player);
        }
    }
}