// android/src/main/java/com/ppicapietra/exoplayersignage/ExoPlayerSignagePlugin.java
package com.ppicapietra.exoplayersignage;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import android.net.Uri;
import android.content.Context;
import android.view.SurfaceView;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.audio.AudioAttributes;
import com.google.android.exoplayer2.source.DefaultMediaSourceFactory;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.upstream.cache.CacheDataSource;
import com.google.android.exoplayer2.upstream.cache.LeastRecentlyUsedCacheEvictor;
import com.google.android.exoplayer2.upstream.cache.SimpleCache;
import com.google.android.exoplayer2.database.StandaloneDatabaseProvider;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource;
import com.google.android.exoplayer2.C;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@CapacitorPlugin(name = "ExoPlayerSignage")
public class ExoPlayerSignagePlugin extends Plugin {

    // Shared cache for all players
    private SimpleCache cache;
    private CacheDataSource.Factory cacheDataSourceFactory;
    
    // Map to store player instances by ID
    private Map<String, PlayerInstance> players = new HashMap<>();
    
    // Helper class to manage a single player instance
    private static class PlayerInstance {
        ExoPlayer player;
        SurfaceView surfaceView;
        String type; // "video" or "audio"
        String id;
        
        PlayerInstance(ExoPlayer player, SurfaceView surfaceView, String type, String id) {
            this.player = player;
            this.surfaceView = surfaceView;
            this.type = type;
            this.id = id;
        }
    }
    
    // Helper method to remove ALL SurfaceViews from the view hierarchy (for debugging)
    private void removeAllSurfaceViewsFromRoot() {
        android.app.Activity activity = getBridge().getActivity();
        if (activity == null) return;
        
        activity.runOnUiThread(() -> {
            try {
                ViewGroup rootView = (ViewGroup) activity.findViewById(android.R.id.content);
                if (rootView != null) {
                    // Find and remove all SurfaceViews
                    java.util.ArrayList<SurfaceView> surfaceViews = new java.util.ArrayList<>();
                    findSurfaceViews(rootView, surfaceViews);
                    for (SurfaceView sv : surfaceViews) {
                        try {
                            ViewGroup parent = (ViewGroup) sv.getParent();
                            if (parent != null) {
                                parent.removeView(sv);
                            }
                        } catch (Exception e) {
                            // Ignore
                        }
                    }
                }
            } catch (Exception e) {
                // Ignore
            }
        });
    }
    
    private void findSurfaceViews(ViewGroup parent, java.util.ArrayList<SurfaceView> result) {
        for (int i = 0; i < parent.getChildCount(); i++) {
            android.view.View child = parent.getChildAt(i);
            if (child instanceof SurfaceView) {
                result.add((SurfaceView) child);
            } else if (child instanceof ViewGroup) {
                findSurfaceViews((ViewGroup) child, result);
            }
        }
    }

    private long getSafeCacheSize() {
        File cacheDir = getContext().getCacheDir();
        long available = cacheDir.getUsableSpace();
        long target = (long) (available * 0.6);
        return Math.max(target, 2L * 1024 * 1024 * 1024); // mínimo 2 GB
    }

    @Override
    public void load() {
        File cacheDir = new File(getContext().getCacheDir(), "exoplayer");

        cache = new SimpleCache(
                cacheDir,
                new LeastRecentlyUsedCacheEvictor(getSafeCacheSize()),
                new StandaloneDatabaseProvider(getContext()));

        // Create HttpDataSourceFactory that can accept custom headers
        DefaultHttpDataSource.Factory httpDataSourceFactory = new DefaultHttpDataSource.Factory();
        httpDataSourceFactory.setUserAgent("ExoPlayerSignage");
        httpDataSourceFactory.setConnectTimeoutMs(DefaultHttpDataSource.DEFAULT_CONNECT_TIMEOUT_MILLIS);
        httpDataSourceFactory.setReadTimeoutMs(DefaultHttpDataSource.DEFAULT_READ_TIMEOUT_MILLIS);
        httpDataSourceFactory.setAllowCrossProtocolRedirects(true);
        
        cacheDataSourceFactory = new CacheDataSource.Factory()
                .setCache(cache)
                .setUpstreamDataSourceFactory(httpDataSourceFactory)
                .setFlags(CacheDataSource.FLAG_BLOCK_ON_CACHE | CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR);
    }

    @PluginMethod
    public void createPlayer(PluginCall call) {
        String type = call.getString("type", "video");
        if (!"video".equals(type) && !"audio".equals(type)) {
            call.reject("Invalid type. Must be 'video' or 'audio'");
            return;
        }
        
        // Get initial volume (optional, defaults based on type)
        Double volumeValue = call.getDouble("volume", null);
        float initialVolume;
        if (volumeValue != null) {
            initialVolume = volumeValue.floatValue();
        } else {
            // Default volumes: 0.0 for video, 1.0 for audio
            initialVolume = "audio".equals(type) ? 1.0f : 0.0f;
        }
        
        android.app.Activity activity = getBridge().getActivity();
        if (activity == null) {
            call.reject("Activity not available");
            return;
        }
        
        activity.runOnUiThread(() -> {
            try {
                String playerId = UUID.randomUUID().toString();
                ExoPlayer player = new ExoPlayer.Builder(getContext())
                        .setMediaSourceFactory(
                                new DefaultMediaSourceFactory(getContext())
                                        .setDataSourceFactory(cacheDataSourceFactory))
                        .build();
                
                SurfaceView surfaceView = null;
                
                if ("video".equals(type)) {
                    // Don't create SurfaceView here - it will be created when video is played
                    // This prevents SurfaceView from blocking images or modals
                    
                    // Configure AudioAttributes for video
                    AudioAttributes audioAttributes = new AudioAttributes.Builder()
                            .setUsage(C.USAGE_MEDIA)
                            .setContentType(C.CONTENT_TYPE_MOVIE)
                            .build();
                    player.setAudioAttributes(audioAttributes, false);
                } else {
                    // Audio player - no SurfaceView needed
                    // Configure AudioAttributes for audio
                    AudioAttributes audioAttributes = new AudioAttributes.Builder()
                            .setUsage(C.USAGE_MEDIA)
                            .setContentType(C.CONTENT_TYPE_MUSIC)
                            .build();
                    player.setAudioAttributes(audioAttributes, false);
                    
                    // Add listener for audio playback ended
                    player.addListener(new Player.Listener() {
                        @Override
                        public void onPlaybackStateChanged(int playbackState) {
                            if (playbackState == Player.STATE_ENDED) {
                                JSObject data = new JSObject();
                                data.put("playerId", playerId);
                                notifyListeners("audioPlaybackEnded", data);
                            }
                        }
                    });
                }
                
                // Set initial volume (configurable from tv_app)
                player.setVolume(initialVolume);
                
                player.setPlayWhenReady(true);
                player.setRepeatMode(ExoPlayer.REPEAT_MODE_OFF);
                
                // Store player instance
                PlayerInstance instance = new PlayerInstance(player, surfaceView, type, playerId);
                players.put(playerId, instance);
                
                JSObject result = new JSObject();
                result.put("playerId", playerId);
                call.resolve(result);
            } catch (Exception e) {
                call.reject("Error creating player: " + e.getMessage(), e);
            }
        });
    }

    @PluginMethod
    public void play(PluginCall call) {
        String playerId = call.getString("playerId");
        String url = call.getString("url");
        
        if (playerId == null) {
            call.reject("playerId requerido");
            return;
        }
        
        if (url == null) {
            call.reject("URL requerida");
            return;
        }
        
        PlayerInstance instance = players.get(playerId);
        if (instance == null) {
            call.reject("Player not found: " + playerId);
            return;
        }
        
        Boolean visibleValue = call.getBoolean("visible", true);
        boolean visible = visibleValue != null ? visibleValue : true;
        
        android.app.Activity activity = getBridge().getActivity();
        if (activity == null) {
            call.reject("Activity not available");
            return;
        }
        
        activity.runOnUiThread(() -> {
            try {
                ExoPlayer player = instance.player;
                
                if ("video".equals(instance.type)) {
                    // Video playback - create SurfaceView if it doesn't exist
                    if (instance.surfaceView == null) {
                        instance.surfaceView = new SurfaceView(getContext());
                        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.MATCH_PARENT,
                            FrameLayout.LayoutParams.MATCH_PARENT
                        );
                        instance.surfaceView.setLayoutParams(params);
                        instance.surfaceView.setZOrderMediaOverlay(true);
                        instance.surfaceView.setZOrderOnTop(false);
                        
                        // Add SurfaceView to root view
                        ViewGroup rootView = (ViewGroup) activity.findViewById(android.R.id.content);
                        if (rootView != null) {
                            rootView.addView(instance.surfaceView);
                        }
                    } else {
                        // SurfaceView exists but might not be in layout - re-add if needed
                        ViewGroup parent = (ViewGroup) instance.surfaceView.getParent();
                        if (parent == null) {
                            ViewGroup rootView = (ViewGroup) activity.findViewById(android.R.id.content);
                            if (rootView != null) {
                                rootView.addView(instance.surfaceView);
                            }
                        }
                    }
                    
                    // Show/hide SurfaceView based on visible parameter
                    instance.surfaceView.setVisibility(visible ? android.view.View.VISIBLE : android.view.View.GONE);
                    player.setVideoSurfaceView(instance.surfaceView);
                } else {
                    // Audio playback - ensure NO SurfaceView exists or is visible
                    // Audio players should NEVER have a SurfaceView
                    if (instance.surfaceView != null) {
                        // Clear video surface first
                        try {
                            player.clearVideoSurfaceView(instance.surfaceView);
                        } catch (Exception e) {
                            // Ignore errors
                        }
                        
                        // Remove from layout if it exists
                        ViewGroup parent = (ViewGroup) instance.surfaceView.getParent();
                        if (parent != null) {
                            try {
                                parent.removeView(instance.surfaceView);
                            } catch (Exception e) {
                                // Ignore errors
                            }
                        }
                        
                        // Set visibility to GONE as additional safety
                        instance.surfaceView.setVisibility(android.view.View.GONE);
                        
                        // IMPORTANT: Set to null to ensure it's never reused for audio
                        instance.surfaceView = null;
                    }
                    
                    // Ensure no video surface is set on the player
                    player.clearVideoSurfaceView(null);
                }
                
                // Stop any current playback before setting new media item
                if (player.getPlaybackState() != Player.STATE_IDLE) {
                    player.stop();
                }
                
                // Get auth token from call if provided
                String authToken = call.getString("authToken");
                
                // Create MediaItem
                MediaItem mediaItem = MediaItem.fromUri(Uri.parse(url));
                
                // If auth token is provided, create a new MediaSourceFactory with auth headers
                if (authToken != null && !authToken.isEmpty()) {
                    // Create HttpDataSourceFactory with auth header
                    DefaultHttpDataSource.Factory httpFactory = new DefaultHttpDataSource.Factory();
                    httpFactory.setUserAgent("ExoPlayerSignage");
                    httpFactory.setConnectTimeoutMs(DefaultHttpDataSource.DEFAULT_CONNECT_TIMEOUT_MILLIS);
                    httpFactory.setReadTimeoutMs(DefaultHttpDataSource.DEFAULT_READ_TIMEOUT_MILLIS);
                    httpFactory.setAllowCrossProtocolRedirects(true);
                    java.util.Map<String, String> headers = new java.util.HashMap<>();
                    headers.put("Authorization", "Bearer " + authToken);
                    httpFactory.setDefaultRequestProperties(headers);
                    
                    // Create CacheDataSourceFactory with auth
                    CacheDataSource.Factory authCacheFactory = new CacheDataSource.Factory()
                            .setCache(cache)
                            .setUpstreamDataSourceFactory(httpFactory)
                            .setFlags(CacheDataSource.FLAG_BLOCK_ON_CACHE | CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR);
                    
                    // Create MediaSourceFactory with auth and apply to player
                    DefaultMediaSourceFactory mediaSourceFactory = new DefaultMediaSourceFactory(getContext())
                            .setDataSourceFactory(authCacheFactory);
                    
                    // Replace player's MediaSourceFactory temporarily
                    // Note: This requires storing the original factory to restore later
                    // For now, we'll create a new player with the auth factory if needed
                    // Actually, we can use the MediaItem.Builder to set custom data source
                    // But ExoPlayer 2.x doesn't support this directly
                    // Workaround: Create a new MediaSource with custom factory
                    com.google.android.exoplayer2.source.MediaSource mediaSource = 
                            mediaSourceFactory.createMediaSource(mediaItem);
                    player.setMediaSource(mediaSource);
                } else {
                    // Use default cache factory (no auth)
                    player.setMediaItem(mediaItem);
                }
                
                player.prepare();
                
                // Ensure volume is set correctly (especially for audio after video)
                if ("audio".equals(instance.type)) {
                    player.setVolume(1.0f);
                } else if ("video".equals(instance.type)) {
                    player.setVolume(0.0f);
                }
                
                // Start playback
                player.play();
                
                call.resolve();
            } catch (Exception e) {
                call.reject("Error playing: " + e.getMessage(), e);
            }
        });
    }

    @PluginMethod
    public void pause(PluginCall call) {
        String playerId = call.getString("playerId");
        if (playerId == null) {
            call.reject("playerId requerido");
            return;
        }
        
        PlayerInstance instance = players.get(playerId);
        if (instance == null) {
            call.reject("Player not found: " + playerId);
            return;
        }
        
        android.app.Activity activity = getBridge().getActivity();
        if (activity == null) {
            call.reject("Activity not available");
            return;
        }
        
        activity.runOnUiThread(() -> {
            try {
                instance.player.pause();
                call.resolve();
            } catch (Exception e) {
                call.reject("Error pausing: " + e.getMessage(), e);
            }
        });
    }

    @PluginMethod
    public void stop(PluginCall call) {
        String playerId = call.getString("playerId");
        if (playerId == null) {
            call.reject("playerId requerido");
            return;
        }
        
        PlayerInstance instance = players.get(playerId);
        if (instance == null) {
            call.reject("Player not found: " + playerId);
            return;
        }
        
        android.app.Activity activity = getBridge().getActivity();
        if (activity == null) {
            call.reject("Activity not available");
            return;
        }
        
        activity.runOnUiThread(() -> {
            try {
                instance.player.stop();
                // Remove SurfaceView when stopped (for video players only)
                // CRITICAL: Audio players should NEVER have a SurfaceView
                if ("audio".equals(instance.type)) {
                    if (instance.surfaceView != null) {
                        try {
                            instance.player.clearVideoSurfaceView(instance.surfaceView);
                        } catch (Exception e) {
                            // Ignore
                        }
                        ViewGroup parent = (ViewGroup) instance.surfaceView.getParent();
                        if (parent != null) {
                            try {
                                parent.removeView(instance.surfaceView);
                            } catch (Exception e) {
                                // Ignore
                            }
                        }
                        instance.surfaceView = null;
                    }
                } else if (instance.surfaceView != null) {
                    // Video player - remove SurfaceView but keep reference for reuse
                    instance.player.clearVideoSurfaceView(instance.surfaceView);
                    // Remove from view hierarchy
                    ViewGroup parent = (ViewGroup) instance.surfaceView.getParent();
                    if (parent != null) {
                        try {
                            parent.removeView(instance.surfaceView);
                        } catch (Exception e) {
                            // Ignore
                        }
                    }
                    // Don't set to null - we'll reuse it when video plays again
                    // instance.surfaceView = null;
                }
                call.resolve();
            } catch (Exception e) {
                call.reject("Error stopping: " + e.getMessage(), e);
            }
        });
    }

    @PluginMethod
    public void setVolume(PluginCall call) {
        String playerId = call.getString("playerId");
        if (playerId == null) {
            call.reject("playerId requerido");
            return;
        }
        
        PlayerInstance instance = players.get(playerId);
        if (instance == null) {
            call.reject("Player not found: " + playerId);
            return;
        }
        
        Double volumeValue = call.getDouble("volume", 1.0);
        float vol = volumeValue != null ? volumeValue.floatValue() : 1.0f;
        
        android.app.Activity activity = getBridge().getActivity();
        if (activity == null) {
            call.reject("Activity not available");
            return;
        }
        
        activity.runOnUiThread(() -> {
            try {
                instance.player.setVolume(vol);
                call.resolve();
            } catch (Exception e) {
                call.reject("Error setting volume: " + e.getMessage(), e);
            }
        });
    }

    @PluginMethod
    public void hide(PluginCall call) {
        String playerId = call.getString("playerId");
        if (playerId == null) {
            call.reject("playerId requerido");
            return;
        }
        
        PlayerInstance instance = players.get(playerId);
        if (instance == null) {
            call.reject("Player not found: " + playerId);
            return;
        }
        
        android.app.Activity activity = getBridge().getActivity();
        if (activity == null) {
            call.reject("Activity not available");
            return;
        }
        
        activity.runOnUiThread(() -> {
            try {
                // Pause playback to ensure SurfaceView doesn't block UI
                if (instance.player != null) {
                    instance.player.pause();
                }
                
                // CRITICAL: Audio players should NEVER have a SurfaceView - ensure it's null
                if ("audio".equals(instance.type)) {
                    if (instance.surfaceView != null) {
                        try {
                            instance.player.clearVideoSurfaceView(instance.surfaceView);
                        } catch (Exception e) {
                            // Ignore
                        }
                        ViewGroup parent = (ViewGroup) instance.surfaceView.getParent();
                        if (parent != null) {
                            try {
                                parent.removeView(instance.surfaceView);
                            } catch (Exception e) {
                                // Ignore
                            }
                        }
                        instance.surfaceView = null;
                    }
                    // Also ensure no other SurfaceViews are blocking (cleanup any orphaned ones)
                    removeAllSurfaceViewsFromRoot();
                    call.resolve();
                    return;
                }
                
                // Remove SurfaceView from layout to ensure it doesn't block modals/images (video only)
                if (instance.surfaceView != null) {
                    // Clear video surface first (before removing from view)
                    if (instance.player != null) {
                        try {
                            instance.player.clearVideoSurfaceView(instance.surfaceView);
                        } catch (Exception e) {
                            // Ignore errors when clearing surface
                        }
                    }
                    // Set visibility to GONE first to stop rendering
                    instance.surfaceView.setVisibility(android.view.View.GONE);
                    // Remove from view hierarchy
                    ViewGroup parent = (ViewGroup) instance.surfaceView.getParent();
                    if (parent != null) {
                        try {
                            parent.removeView(instance.surfaceView);
                        } catch (Exception e) {
                            // Ignore errors when removing view
                        }
                    }
                    // Don't set to null - we'll reuse it when video plays again
                    // instance.surfaceView = null;
                }
                call.resolve();
            } catch (Exception e) {
                call.reject("Error hiding SurfaceView: " + e.getMessage(), e);
            }
        });
    }

    @PluginMethod
    public void show(PluginCall call) {
        String playerId = call.getString("playerId");
        if (playerId == null) {
            call.reject("playerId requerido");
            return;
        }
        
        PlayerInstance instance = players.get(playerId);
        if (instance == null) {
            call.reject("Player not found: " + playerId);
            return;
        }
        
        android.app.Activity activity = getBridge().getActivity();
        if (activity == null) {
            call.reject("Activity not available");
            return;
        }
        
        activity.runOnUiThread(() -> {
            try {
                // CRITICAL: Audio players should NEVER have a SurfaceView
                if ("audio".equals(instance.type)) {
                    // Ensure no SurfaceView exists for audio
                    if (instance.surfaceView != null) {
                        try {
                            instance.player.clearVideoSurfaceView(instance.surfaceView);
                        } catch (Exception e) {
                            // Ignore
                        }
                        ViewGroup parent = (ViewGroup) instance.surfaceView.getParent();
                        if (parent != null) {
                            try {
                                parent.removeView(instance.surfaceView);
                            } catch (Exception e) {
                                // Ignore
                            }
                        }
                        instance.surfaceView = null;
                    }
                    call.resolve();
                    return;
                }
                
                // Only show SurfaceView for video players
                if ("video".equals(instance.type) && instance.player != null) {
                    // Recreate SurfaceView if it was removed
                    if (instance.surfaceView == null) {
                        instance.surfaceView = new SurfaceView(getContext());
                        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.MATCH_PARENT,
                            FrameLayout.LayoutParams.MATCH_PARENT
                        );
                        instance.surfaceView.setLayoutParams(params);
                        instance.surfaceView.setZOrderMediaOverlay(true);
                        instance.surfaceView.setZOrderOnTop(false);
                        
                        // Add SurfaceView to root view
                        ViewGroup rootView = (ViewGroup) activity.findViewById(android.R.id.content);
                        if (rootView != null) {
                            rootView.addView(instance.surfaceView);
                        }
                    } else {
                        // SurfaceView exists but might not be in layout - re-add if needed
                        ViewGroup parent = (ViewGroup) instance.surfaceView.getParent();
                        if (parent == null) {
                            ViewGroup rootView = (ViewGroup) activity.findViewById(android.R.id.content);
                            if (rootView != null) {
                                rootView.addView(instance.surfaceView);
                            }
                        }
                    }
                    
                    instance.surfaceView.setVisibility(android.view.View.VISIBLE);
                    instance.player.setVideoSurfaceView(instance.surfaceView);
                }
                call.resolve();
            } catch (Exception e) {
                call.reject("Error showing SurfaceView: " + e.getMessage(), e);
            }
        });
    }

    @PluginMethod
    public void releasePlayer(PluginCall call) {
        String playerId = call.getString("playerId");
        if (playerId == null) {
            call.reject("playerId requerido");
            return;
        }
        
        PlayerInstance instance = players.get(playerId);
        if (instance == null) {
            call.reject("Player not found: " + playerId);
            return;
        }
        
        android.app.Activity activity = getBridge().getActivity();
        if (activity != null) {
            activity.runOnUiThread(() -> {
                if (instance.player != null) {
                    if (instance.surfaceView != null) {
                        instance.player.clearVideoSurfaceView(instance.surfaceView);
                    }
                    instance.player.release();
                }
                if (instance.surfaceView != null) {
                    ViewGroup parent = (ViewGroup) instance.surfaceView.getParent();
                    if (parent != null) {
                        parent.removeView(instance.surfaceView);
                    }
                }
                players.remove(playerId);
                call.resolve();
            });
        } else {
            if (instance.player != null) {
                if (instance.surfaceView != null) {
                    instance.player.clearVideoSurfaceView(instance.surfaceView);
                }
                instance.player.release();
            }
            players.remove(playerId);
            call.resolve();
        }
    }

    @Override
    protected void handleOnDestroy() {
        android.app.Activity activity = getBridge().getActivity();
        if (activity != null) {
            activity.runOnUiThread(() -> {
                // Release all players
                for (PlayerInstance instance : players.values()) {
                    if (instance.player != null) {
                        if (instance.surfaceView != null) {
                            instance.player.clearVideoSurfaceView(instance.surfaceView);
                        }
                        instance.player.release();
                    }
                    if (instance.surfaceView != null) {
                        ViewGroup parent = (ViewGroup) instance.surfaceView.getParent();
                        if (parent != null) {
                            parent.removeView(instance.surfaceView);
                        }
                    }
                }
                players.clear();
                
                if (cache != null) {
                    cache.release();
                    cache = null;
                }
            });
        } else {
            for (PlayerInstance instance : players.values()) {
                if (instance.player != null) {
                    if (instance.surfaceView != null) {
                        instance.player.clearVideoSurfaceView(instance.surfaceView);
                    }
                    instance.player.release();
                }
            }
            players.clear();
            
            if (cache != null) {
                cache.release();
                cache = null;
            }
        }
    }
}
