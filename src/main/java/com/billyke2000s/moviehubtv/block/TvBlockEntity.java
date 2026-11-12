package com.billyke2000s.moviehubtv.block;

import com.billyke2000s.moviehubtv.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * Saved TV state: power, tuned channel, whether the guide is showing, volume and linked speakers.
 * Changed only on the server: remote/hand actions through {@link #apply}, speaker links through {@link SpeakerLink}.
 */
public class TvBlockEntity extends BlockEntity implements com.billyke2000s.moviehubtv.catalogue.ResolvedMedia.Watcher {
    public static final int ACTION_POWER = 0;
    public static final int ACTION_OPEN_GUIDE = 1;
    public static final int ACTION_CLOSE_GUIDE = 2;
    public static final int ACTION_TUNE = 3;
    public static final int ACTION_REPAIR_LAYOUT = 4;
    public static final int ACTION_VOLUME_UP = 5;
    public static final int ACTION_VOLUME_DOWN = 6;
    public static final int ACTION_PAUSE = 7;
    public static final int ACTION_SEEK_BACK = 8;
    public static final int ACTION_SEEK_FORWARD = 9;

    public static final int DEFAULT_VOLUME = 80;
    public static final int VOLUME_STEP = 5;
    /** Speakers each TV block can hold; a joined screen counts every member's speakers. */
    public static final int MAX_SPEAKERS = 8;

    private boolean on;
    private String channelId = "";
    private String mediaSource = "";
    private boolean guideOpen = true;
    private boolean mediaPaused;
    private long mediaPosition, mediaTick;
    private int mediaRevision;
    private int volume = DEFAULT_VOLUME;
    private final List<BlockPos> speakers = new ArrayList<>();
    /** Place in a TV wall: column/row from the viewer's bottom-left, and the wall's size (1x1 = alone). */
    private int wallX, wallY, wallW = 1, wallH = 1;

    public TvBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistry.TV_BLOCK_ENTITY.get(), pos, state);
    }

    public boolean isOn() { return on; }
    /** 0-100. For a joined screen, the controller's volume is used. */
    public int volume() { return volume; }
    /** Speakers linked to this TV block (not the rest of its wall). */
    public List<BlockPos> speakers() { return List.copyOf(speakers); }
    public String channelId() { return channelId; }
    public boolean isOnDemand() { return channelId.startsWith("vod:"); }
    public String mediaSource() { return mediaSource; }
    public boolean mediaPaused() { return mediaPaused; }
    public int mediaRevision() { return mediaRevision; }
    public long mediaPosition() {
        long tick = level == null ? mediaTick : level.getGameTime();
        return Math.max(0, mediaPosition + (!mediaPaused && on && isOnDemand() ? Math.max(0, tick - mediaTick) * 50 : 0));
    }
    /** The guide shows when it was opened, or when nothing has been tuned yet. */
    public boolean isGuideShowing() { return on && (guideOpen || channelId.isEmpty()); }

    public int wallX() { return wallX; }
    public int wallY() { return wallY; }
    public int wallWidth() { return wallW; }
    public int wallHeight() { return wallH; }
    /** True when this TV is part of a multi-TV screen. */
    public boolean inWall() { return wallW * wallH > 1; }
    /** The bottom-left TV of a wall, which holds the wall's power and channel and draws the screen. */
    public boolean isWallController() { return !inWall() || (wallX == 0 && wallY == 0); }

    /** The block whose state this TV shows: itself, or its wall's controller. */
    public BlockPos controllerPos() {
        if (!inWall()) return worldPosition;
        return TvWall.memberPos(worldPosition, getBlockState().getValue(TvBlock.FACING), -wallX, -wallY);
    }

    boolean guideOpenFlag() { return guideOpen; }

    /** Copies power/channel/guide over from an old big TV ({@link BigTvConverter}). Server side only. */
    void restore(boolean on, String channel, boolean guide) {
        this.on = on;
        this.channelId = channel == null ? "" : channel;
        this.guideOpen = guide;
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    /** Old crafted big TVs turn themselves into normal TVs, and TV walls re-check their frames, when they load. */
    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null) mediaTick = level.getGameTime();
        if (!(level instanceof net.minecraft.server.level.ServerLevel server)) return;
        if (getBlockState().getBlock() instanceof BigTvBlock) {
            BigTvConverter.queue(server, worldPosition);
        } else if (getBlockState().is(ModRegistry.TV.get())) {
            // Every member can repair the joined wall when its chunk loads.
            BigTvConverter.queueRegroup(server, worldPosition);
        }
    }

    /** Set by {@link TvWall}. Server side only. */
    void setWall(int x, int y, int w, int h) {
        if (x == wallX && y == wallY && w == wallW && h == wallH) return;
        wallX = x; wallY = y; wallW = w; wallH = h;
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    /** Re-applies the lit/unlit look (to the whole wall, from its controller). Server side only. */
    void refreshPower() {
        if (isWallController()) sync();
    }

    /** Applies a remote or hand action. Server side only. */
    public void apply(int action, String channel) {
        if (action == ACTION_REPAIR_LAYOUT || TvScreenLayout.resolve(level, worldPosition) == null) {
            if (level instanceof net.minecraft.server.level.ServerLevel server) {
                if (getBlockState().getBlock() instanceof BigTvBlock) BigTvConverter.queue(server, worldPosition);
                else BigTvConverter.queueRegroup(server, worldPosition);
            }
            return;
        }
        switch (action) {
            case ACTION_POWER -> {
                mediaPosition = mediaPosition();
                mediaTick = level.getGameTime();
                on = !on;
                if (on) guideOpen = true; // turning on goes straight to the TV guide
            }
            case ACTION_OPEN_GUIDE -> { if (on) guideOpen = true; }
            case ACTION_CLOSE_GUIDE -> { if (on && !channelId.isEmpty()) guideOpen = false; }
            case ACTION_VOLUME_UP -> volume = Math.min(100, volume + VOLUME_STEP);
            case ACTION_VOLUME_DOWN -> volume = Math.max(0, volume - VOLUME_STEP);
            case ACTION_TUNE -> {
                if (channel == null || channel.isBlank()) return;
                // Only fixed Pluto episode locators, never a client-supplied arbitrary URL.
                if (channel.startsWith("vod:moviehub:")) {
                    String source = com.billyke2000s.moviehubtv.catalogue.ResolvedMedia.find(channel);
                    if (source.isEmpty()) return;
                    mediaSource = source;
                    com.billyke2000s.moviehubtv.catalogue.ResolvedMedia.watch(channel, this);
                } else {
                    if (channel.startsWith("vod:") && !channel.matches("vod:pluto:[0-9a-f]{24}\\|[^\\p{Cntrl}|]{1,100}")) return;
                    mediaSource = "";
                }
                on = true;
                channelId = channel;
                guideOpen = false;
                mediaPosition = 0; mediaTick = level.getGameTime(); mediaPaused = false;
                mediaRevision++;
            }
            case ACTION_PAUSE -> {
                if (!on || !isOnDemand()) return;
                mediaPosition = mediaPosition(); mediaTick = level.getGameTime(); mediaPaused = !mediaPaused;
                mediaRevision++;
            }
            case ACTION_SEEK_BACK, ACTION_SEEK_FORWARD -> {
                if (!on || !isOnDemand()) return;
                mediaPosition = Math.max(0, mediaPosition() + (action == ACTION_SEEK_BACK ? -10000 : 10000));
                mediaTick = level.getGameTime();
                mediaRevision++;
            }
            default -> { return; }
        }
        sync();
    }

    /**
     * The title's search found another source: pass the longer fallback list to players without
     * restarting anyone's playback. Server side only.
     */
    @Override
    public void mediaSourcesAdded() {
        if (level == null || level.isClientSide() || isRemoved() || !channelId.startsWith("vod:moviehub:")) return;
        String now = com.billyke2000s.moviehubtv.catalogue.ResolvedMedia.find(channelId);
        if (now.isEmpty() || now.equals(mediaSource)) return;
        mediaSource = now;
        sendState();
    }

    /** Called by {@link SpeakerLink}. Drops the oldest speaker when full. Server side only. */
    void addSpeaker(BlockPos speaker) {
        BlockPos p = speaker.immutable();
        if (speakers.contains(p)) return;
        if (speakers.size() >= MAX_SPEAKERS) speakers.remove(0);
        speakers.add(p);
        sendState();
    }

    /** Called by {@link SpeakerLink}. Server side only. */
    void removeSpeaker(BlockPos speaker) {
        if (speakers.remove(speaker)) sendState();
    }

    private void sendState() {
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    private void sync() {
        setChanged();
        if (level == null) return;
        BlockState state = level.getBlockState(worldPosition);
        if (inWall()) {
            Direction facing = state.getValue(TvBlock.FACING);
            for (int x = 0; x < wallW; x++) {
                for (int y = 0; y < wallH; y++) {
                    BlockPos p = TvWall.memberPos(worldPosition, facing, x, y);
                    if (!level.isLoaded(p)) continue;
                    BlockState s = level.getBlockState(p);
                    if (s.is(ModRegistry.TV.get()) && s.getValue(TvBlock.FACING) == facing && s.getValue(TvBlock.POWERED) != on) {
                        level.setBlock(p, s.setValue(TvBlock.POWERED, on), Block.UPDATE_ALL);
                    }
                }
            }
            BlockState now = level.getBlockState(worldPosition);
            level.sendBlockUpdated(worldPosition, now, now, Block.UPDATE_CLIENTS);
        } else if (state.getBlock() instanceof BigTvBlock big) {
            big.setPoweredAll(level, worldPosition, state, on);
            BlockState now = level.getBlockState(worldPosition);
            level.sendBlockUpdated(worldPosition, now, now, Block.UPDATE_CLIENTS);
        } else if (state.getValue(TvBlock.POWERED) != on) {
            level.setBlock(worldPosition, state.setValue(TvBlock.POWERED, on), Block.UPDATE_ALL);
        } else {
            level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putBoolean("On", on);
        tag.putString("Channel", channelId);
        tag.putBoolean("MediaPaused", mediaPaused);
        tag.putLong("MediaPosition", mediaPosition());
        tag.putInt("MediaRevision", mediaRevision);
        tag.putBoolean("Guide", guideOpen);
        tag.putIntArray("Wall", new int[]{wallX, wallY, wallW, wallH});
        tag.putInt("Volume", volume);
        tag.putLongArray("Speakers", speakers.stream().mapToLong(BlockPos::asLong).toArray());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        on = tag.getBoolean("On");
        channelId = tag.getString("Channel");
        mediaSource = tag.getString("ResolvedMedia");
        mediaPaused = tag.getBoolean("MediaPaused");
        mediaPosition = Math.max(0, tag.getLong("MediaPosition"));
        mediaRevision = tag.getInt("MediaRevision");
        mediaTick = level == null ? 0 : level.getGameTime();
        guideOpen = !tag.contains("Guide") || tag.getBoolean("Guide");
        volume = tag.contains("Volume") ? Math.max(0, Math.min(100, tag.getInt("Volume"))) : DEFAULT_VOLUME;
        speakers.clear();
        for (long packed : tag.getLongArray("Speakers")) {
            if (speakers.size() < MAX_SPEAKERS) speakers.add(BlockPos.of(packed));
        }
        int[] wall = tag.getIntArray("Wall");
        if (wall.length == 4 && wall[2] >= 1 && wall[3] >= 1 && wall[2] <= TvWall.MAX_WIDTH && wall[3] <= TvWall.MAX_HEIGHT
                && wall[0] >= 0 && wall[0] < wall[2] && wall[1] >= 0 && wall[1] < wall[3]) {
            wallX = wall[0]; wallY = wall[1]; wallW = wall[2]; wallH = wall[3];
        } else {
            wallX = 0; wallY = 0; wallW = 1; wallH = 1;
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag, registries);
        // Playback headers belong only in the live update, not in the saved world.
        tag.putString("ResolvedMedia", mediaSource);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}

