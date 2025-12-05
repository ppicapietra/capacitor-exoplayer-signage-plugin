package com.ppicapietra.exoplayersignage;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import android.net.Uri;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.source.DefaultMediaSourceFactory;
import com.google.android.exoplayer2.upstream.cache.CacheDataSource;
import com.google.android.exoplayer2.upstream.cache.LeastRecentlyUsedCacheEvictor;
import com.google.android.exoplayer2.upstream.cache.SimpleCache;
import com.google.android.exoplayer2.database.StandaloneDatabaseProvider;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource;
import java.io.File;

@CapacitorPlugin(name = "ExoPlayerSignage")
public class ExoPlayerSignagePlugin extends Plugin {

    private ExoPlayer player;
    private SimpleCache cache;
    private CacheDataSource.Factory cacheDataSourceFactory;

    private long getSafeCacheSize() {
        File cacheDir = getContext().getCacheDir();
        long available = cacheDir.getUsableSpace();
        long target = (long) (available * 0.6); // 60% of the available space
        return Math.max(target, 2L * 1024 * 1024 * 1024); // minimum 2 GB
    }

    @Override
    public void load() {
        File cacheDir = new File(getContext().getCacheDir(), "exoplayer");
        cache = new SimpleCache(cacheDir, new LeastRecentlyUsedCacheEvictor(getSafeCacheSize()),
                new StandaloneDatabaseProvider(getContext()));

        cacheDataSourceFactory = new CacheDataSource.Factory()
                .setCache(cache)
                .setUpstreamDataSourceFactory(new DefaultHttpDataSource.Factory())
                .setFlags(CacheDataSource.FLAG_BLOCK_ON_CACHE); // key flag

        player = new ExoPlayer.Builder(getContext())
                .setMediaSourceFactory(new DefaultMediaSourceFactory(getContext())
                        .setDataSourceFactory(cacheDataSourceFactory))
                .build();
    }

    @PluginMethod
    public void play(PluginCall call) {
        String url = call.getString("url");
        if (url == null) {
            call.reject("URL required");
            return;
        }

        MediaItem mediaItem = MediaItem.fromUri(Uri.parse(url));
        player.setMediaItem(mediaItem);
        player.prepare();
        player.play();

        JSObject ret = new JSObject();
        ret.put("status", "playing");
        call.resolve(ret);
    }

    @PluginMethod
    public void stop(PluginCall call) {
        if (player != null) {
            player.stop();
        }
        JSObject ret = new JSObject();
        ret.put("status", "stopped");
        call.resolve(ret);
    }

    @PluginMethod
    public void pause(PluginCall call) {
        if (player != null) {
            player.pause();
        }
        call.resolve();
    }

    @PluginMethod
    public void setVolume(PluginCall call) {
        float volume = (float) call.getDouble("volume", 1.0); // 0.0 a 1.0
        if (player != null) {
            player.setVolume(volume);
        }
        call.resolve();
    }

    private long calculateSafeCacheSize() {
        // Lógica segura: nunca más del 40% del total o 90% del libre, máx 8 GB
        File cacheDir = getContext().getCacheDir();
        long free = cacheDir.getUsableSpace();
        long total = cacheDir.getTotalSpace();
        long maxByPercent = (total * 40) / 100;
        long maxByFree = (free * 90) / 100;
        return Math.min(Math.min(maxByPercent, maxByFree), 8L * 1024 * 1024 * 1024L);
    }

    @Override
    protected void handleOnDestroy() {
        super.handleOnDestroy();
        if (player != null) {
            player.release();
            player = null;
        }
        if (cache != null) {
            cache.release();
            cache = null;
        }
    }
}