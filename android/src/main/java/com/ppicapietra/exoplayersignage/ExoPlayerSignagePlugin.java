// android/src/main/java/com/ppicapietra/exoplayersignage/ExoPlayerSignagePlugin.java
package com.ppicapietra.exoplayersignage;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import android.net.Uri;
import android.content.Context;
import android.view.TextureView;
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
    
    // Container for all video TextureViews with controlled z-order
    // This ensures videos stay below the WebView (which contains the modal HTML)
    private FrameLayout videoContainer;
    
    // Helper class to manage a single player instance
    private static class PlayerInstance {
        ExoPlayer player;
        TextureView textureView; // Changed from TextureView to TextureView for better z-order integration
        String type; // "video" or "audio"
        String id;
        
        PlayerInstance(ExoPlayer player, TextureView textureView, String type, String id) {
            this.player = player;
            this.textureView = textureView;
            this.type = type;
            this.id = id;
        }
    }
    
    // Helper method to remove ALL TextureViews from the view hierarchy (for debugging)
    private void removeAllTextureViewsFromRoot() {
        android.app.Activity activity = getBridge().getActivity();
        if (activity == null) return;
        
        activity.runOnUiThread(() -> {
            try {
                ViewGroup rootView = (ViewGroup) activity.findViewById(android.R.id.content);
                if (rootView != null) {
                    // Find and remove all TextureViews
                    java.util.ArrayList<TextureView> textureViews = new java.util.ArrayList<>();
                    findTextureViews(rootView, textureViews);
                    for (TextureView tv : textureViews) {
                        try {
                            ViewGroup parent = (ViewGroup) tv.getParent();
                            if (parent != null) {
                                parent.removeView(tv);
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
    
    private void findTextureViews(ViewGroup parent, java.util.ArrayList<TextureView> result) {
        for (int i = 0; i < parent.getChildCount(); i++) {
            android.view.View child = parent.getChildAt(i);
            if (child instanceof TextureView) {
                result.add((TextureView) child);
            } else if (child instanceof ViewGroup) {
                findTextureViews((ViewGroup) child, result);
            }
        }
    }
    
    /**
     * Get or create the video container FrameLayout with controlled z-order.
     * This container ensures all video TextureViews stay below the WebView.
     * Note: This method should only be called from the UI thread (runOnUiThread).
     */
    private FrameLayout getOrCreateVideoContainer() {
        if (videoContainer != null) {
            return videoContainer;
        }
        
        android.app.Activity activity = getBridge().getActivity();
        if (activity == null) {
            return null;
        }
        
        // Ensure we're on UI thread
        if (android.os.Looper.myLooper() != android.os.Looper.getMainLooper()) {
            // Not on UI thread - this shouldn't happen as we call from runOnUiThread blocks
            // But if it does, return null and let the caller handle it
            return null;
        }
        
        ViewGroup rootView = (ViewGroup) activity.findViewById(android.R.id.content);
        if (rootView == null) {
            return null;
        }
        
        // Create FrameLayout container
        videoContainer = new FrameLayout(getContext());
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        );
        videoContainer.setLayoutParams(params);
        
        // Set z-order to 1000 (low, below WebView)
        // NOTE: The WebView should have its z-order set in MainActivity.java to ensure
        // it stays above this container. This plugin only manages its own container's z-order.
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            videoContainer.setZ(1000f);
        }
        
        // Add container to root view at the BEGINNING (index 0) to ensure it's below WebView
        // The WebView should already be in the hierarchy with a higher z-order set in MainActivity
        rootView.addView(videoContainer, 0);
        
        // Initially hidden - will be shown when videos are added
        videoContainer.setVisibility(android.view.View.GONE);
        
        return videoContainer;
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
        
        // Get z-index (optional, null = use bringToFront() by default)
        Double zIndexValue = call.getDouble("zIndex", null);
        Float zIndex = zIndexValue != null ? zIndexValue.floatValue() : null;
        
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
                
                TextureView textureView = null;
                
                if ("video".equals(type)) {
                    // Don't create TextureView here - it will be created when video is played
                    // TextureView respects z-order better than TextureView, allowing HTML elements to appear on top
                    
                    // Configure AudioAttributes for video
                    AudioAttributes audioAttributes = new AudioAttributes.Builder()
                            .setUsage(C.USAGE_MEDIA)
                            .setContentType(C.CONTENT_TYPE_MOVIE)
                            .build();
                    player.setAudioAttributes(audioAttributes, false);
                } else {
                    // Audio player - no TextureView needed
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
                PlayerInstance instance = new PlayerInstance(player, textureView, type, playerId);
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
                
                // Stop any current playback before setting new media item
                // This must be done BEFORE creating/associating TextureView to avoid conflicts
                if (player.getPlaybackState() != Player.STATE_IDLE) {
                    player.stop();
                }
                
                if ("video".equals(instance.type)) {
                    // Video playback - create TextureView ONLY if visible is true
                    // TextureView respects z-order, allowing HTML elements (modal, images) to appear on top
                    if (!visible) {
                        // Don't create or show TextureView if not visible
                        if (instance.textureView != null) {
                            // Hide TextureView using INVISIBLE (keep it in container)
                            try {
                                player.clearVideoTextureView(instance.textureView);
                            } catch (Exception e) {
                                // Ignore
                            }
                            // Use INVISIBLE instead of removing from parent
                            instance.textureView.setVisibility(android.view.View.INVISIBLE);
                            
                            // DO NOT hide container - keep it visible to maintain z-order
                            // Container visibility should be managed separately, not based on TextureView visibility
                            
                            // DO NOT remove from parent - keep it in container
                        }
                    } else {
                        // visible is true - create TextureView if needed
                        // Get or create video container (with controlled z-order)
                        FrameLayout container = getOrCreateVideoContainer();
                        
                        if (instance.textureView == null) {
                            instance.textureView = new TextureView(getContext());
                            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                                FrameLayout.LayoutParams.MATCH_PARENT,
                                FrameLayout.LayoutParams.MATCH_PARENT
                            );
                            instance.textureView.setLayoutParams(params);
                            
                            // Add TextureView to video container (not root view)
                            // Container has z-order 1000, ensuring it stays below WebView
                            if (container != null) {
                                container.addView(instance.textureView);
                                container.setVisibility(android.view.View.VISIBLE);
                            }
                        } else {
                            // TextureView exists but might not be in layout - re-add if needed
                            ViewGroup parent = (ViewGroup) instance.textureView.getParent();
                            if (parent == null) {
                                // Add to container if not already in layout
                                if (container != null) {
                                    container.addView(instance.textureView);
                                    container.setVisibility(android.view.View.VISIBLE);
                                }
                            } else if (parent != container) {
                                // TextureView is in wrong parent (e.g., root view) - move to container
                                // This is a migration case - hide first, then move
                                instance.textureView.setVisibility(android.view.View.INVISIBLE);
                                try {
                                    parent.removeView(instance.textureView);
                                } catch (Exception e) {
                                    // Ignore
                                }
                                if (container != null) {
                                    container.addView(instance.textureView);
                                    container.setVisibility(android.view.View.VISIBLE);
                                }
                            }
                        }
                        
                        // Show TextureView
                        instance.textureView.setVisibility(android.view.View.VISIBLE);
                        // Note: We'll associate TextureView with player AFTER MediaItem is set (see below)
                    }
                } else {
                    // Audio playback - ensure NO TextureView exists or is visible
                    // Audio players should NEVER have a TextureView
                    if (instance.textureView != null) {
                        // Clear video texture first
                        try {
                            player.clearVideoTextureView(instance.textureView);
                        } catch (Exception e) {
                            // Ignore errors
                        }
                        
                        // Hide TextureView using INVISIBLE (keep in layout but hidden)
                        // Don't remove from parent - use visibility instead
                        instance.textureView.setVisibility(android.view.View.INVISIBLE);
                        
                        // IMPORTANT: Set to null to ensure it's never reused for audio
                        instance.textureView = null;
                    }
                    
                    // Ensure no video texture is set on the player
                    // Note: clearVideoTextureView requires a TextureView parameter, so we only clear if one exists
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
                
                // For video players, associate TextureView AFTER MediaItem is set but BEFORE prepare()
                // This ensures the TextureView is ready when the player prepares
                if ("video".equals(instance.type) && visible && instance.textureView != null) {
                    player.setVideoTextureView(instance.textureView);
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
                // Remove TextureView when stopped (for video players only)
                // CRITICAL: Audio players should NEVER have a TextureView
                if ("audio".equals(instance.type)) {
                    if (instance.textureView != null) {
                        try {
                            instance.player.clearVideoTextureView(instance.textureView);
                        } catch (Exception e) {
                            // Ignore
                        }
                        // Hide TextureView using INVISIBLE (keep in layout but hidden)
                        // Don't remove from parent - use visibility instead
                        instance.textureView.setVisibility(android.view.View.INVISIBLE);
                        instance.textureView = null;
                    }
                } else if (instance.textureView != null) {
                    // Video player - hide TextureView but keep it in container for reuse
                    // CRITICAL: DO NOT call clearVideoTextureView() here - it desassociates the TextureView
                    // from the player, making it impossible to restore playback later
                    // Instead, just pause and hide the TextureView
                    instance.player.pause();
                    // Hide TextureView using INVISIBLE (keeps it in layout, maintains z-order)
                    instance.textureView.setVisibility(android.view.View.INVISIBLE);
                    
                    // CRITICAL: Ensure container remains VISIBLE even when TextureView is INVISIBLE
                    // This maintains the z-order and allows quick restoration
                    FrameLayout container = getOrCreateVideoContainer();
                    if (container != null) {
                        container.setVisibility(android.view.View.VISIBLE);
                    }
                    
                    // Don't set to null - we'll reuse it when video plays again
                    // Don't remove from parent - keep it in container to maintain z-order
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
                // Pause playback to ensure TextureView doesn't block UI
                if (instance.player != null) {
                    instance.player.pause();
                }
                
                // CRITICAL: Audio players should NEVER have a TextureView - ensure it's null
                if ("audio".equals(instance.type)) {
                    if (instance.textureView != null) {
                        try {
                            instance.player.clearVideoTextureView(instance.textureView);
                        } catch (Exception e) {
                            // Ignore
                        }
                        // Hide TextureView using INVISIBLE (keep in layout but hidden)
                        // Don't remove from parent - use visibility instead
                        instance.textureView.setVisibility(android.view.View.INVISIBLE);
                        instance.textureView = null;
                    }
                    // DO NOT call removeAllTextureViewsFromRoot() - it removes TextureViews from container
                    call.resolve();
                    return;
                }
                
                // Hide TextureView without removing it from layout (video only)
                // This allows it to be shown again later without recreation
                if (instance.textureView != null) {
                    // Pause playback but DO NOT clear video texture
                    // CRITICAL: clearVideoTextureView() desassociates the TextureView from the player,
                    // making it impossible to restore playback later without recreating the TextureView
                    if (instance.player != null) {
                        try {
                            instance.player.pause();
                            // DO NOT call clearVideoTextureView() here - keep TextureView associated with player
                        } catch (Exception e) {
                            // Ignore errors
                        }
                    }
                    // Set visibility to INVISIBLE to hide it (but keep it in the container)
                    // Using INVISIBLE instead of GONE maintains layout space and z-order
                    instance.textureView.setVisibility(android.view.View.INVISIBLE);
                    
                    // CRITICAL: Ensure container remains VISIBLE even when TextureView is INVISIBLE
                    // This maintains the z-order and allows quick restoration
                    FrameLayout container = getOrCreateVideoContainer();
                    if (container != null) {
                        container.setVisibility(android.view.View.VISIBLE);
                    }
                    
                    // DO NOT remove TextureView from parent
                    // DO NOT set instance.textureView = null
                    // This allows show() to restore it quickly without recreation
                }
                
                // DO NOT call removeAllTextureViewsFromRoot() - it removes TextureViews from container
                
                call.resolve();
            } catch (Exception e) {
                call.reject("Error hiding TextureView: " + e.getMessage(), e);
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
                // CRITICAL: Audio players should NEVER have a TextureView
                if ("audio".equals(instance.type)) {
                    // Ensure no TextureView exists for audio
                    if (instance.textureView != null) {
                        try {
                            instance.player.clearVideoTextureView(instance.textureView);
                        } catch (Exception e) {
                            // Ignore
                        }
                        // Hide TextureView using INVISIBLE (keep in layout but hidden)
                        // Don't remove from parent - use visibility instead
                        instance.textureView.setVisibility(android.view.View.INVISIBLE);
                        instance.textureView = null;
                    }
                    call.resolve();
                    return;
                }
                
                // Only show TextureView for video players
                // CRITICAL: Show TextureView even if player is in STATE_IDLE (after stop())
                // This allows restoring video after modal closes
                if ("video".equals(instance.type) && instance.player != null) {
                    // Check player state
                    int playbackState = instance.player.getPlaybackState();
                    boolean isPlaying = instance.player.isPlaying();
                    
                    // Show TextureView if player has content (not STATE_IDLE) OR if TextureView exists
                    // This allows restoring after stop() when player is in STATE_IDLE
                    if (playbackState != Player.STATE_IDLE || instance.textureView != null) {
                        // Get or create video container
                        FrameLayout container = getOrCreateVideoContainer();
                        
                        // Recreate TextureView only if it was completely removed (null)
                        if (instance.textureView == null) {
                            instance.textureView = new TextureView(getContext());
                            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                                FrameLayout.LayoutParams.MATCH_PARENT,
                                FrameLayout.LayoutParams.MATCH_PARENT
                            );
                            instance.textureView.setLayoutParams(params);
                            
                            // Add TextureView to video container (not root view)
                            if (container != null) {
                                container.addView(instance.textureView);
                                container.setVisibility(android.view.View.VISIBLE);
                            }
                        } else {
                            // TextureView exists - check if it's in the correct container
                            ViewGroup parent = (ViewGroup) instance.textureView.getParent();
                            if (parent == null) {
                                // TextureView was removed from parent - re-add to container
                                if (container != null) {
                                    container.addView(instance.textureView);
                                    container.setVisibility(android.view.View.VISIBLE);
                                }
                            } else if (parent != container) {
                                // TextureView is in wrong parent (e.g., root view) - move to container
                                // This is a migration case - hide first, then move
                                instance.textureView.setVisibility(android.view.View.INVISIBLE);
                                try {
                                    parent.removeView(instance.textureView);
                                } catch (Exception e) {
                                    // Ignore
                                }
                                if (container != null) {
                                    container.addView(instance.textureView);
                                    container.setVisibility(android.view.View.VISIBLE);
                                }
                            } else {
                                // TextureView is already in correct container - just show container
                                if (container != null) {
                                    container.setVisibility(android.view.View.VISIBLE);
                                }
                            }
                        }
                        
                        // Show TextureView and associate with player
                        instance.textureView.setVisibility(android.view.View.VISIBLE);
                        instance.player.setVideoTextureView(instance.textureView);
                        
                        // If player is in STATE_IDLE (after stop()), prepare and play
                        if (playbackState == Player.STATE_IDLE) {
                            // Player was stopped - prepare and start playback
                            instance.player.prepare();
                            instance.player.play();
                        } else if (!isPlaying) {
                            // Player is paused - resume playback
                            instance.player.play();
                        }
                    } else {
                        // Player is in STATE_IDLE and TextureView doesn't exist - nothing to show
                        // This shouldn't happen in normal flow, but handle gracefully
                        if (instance.textureView != null) {
                            try {
                                instance.player.clearVideoTextureView(instance.textureView);
                            } catch (Exception e) {
                                // Ignore
                            }
                            // Hide using INVISIBLE (keeps it in layout, maintains z-order)
                            instance.textureView.setVisibility(android.view.View.INVISIBLE);
                            
                            // DO NOT hide container - keep it visible to maintain z-order
                        }
                    }
                }
                call.resolve();
            } catch (Exception e) {
                call.reject("Error showing TextureView: " + e.getMessage(), e);
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
                    if (instance.textureView != null) {
                        instance.player.clearVideoTextureView(instance.textureView);
                    }
                    instance.player.release();
                }
                if (instance.textureView != null) {
                    // Hide TextureView using INVISIBLE instead of removing from parent
                    // This maintains the view hierarchy and z-order
                    instance.textureView.setVisibility(android.view.View.INVISIBLE);
                    try {
                        instance.player.clearVideoTextureView(instance.textureView);
                    } catch (Exception e) {
                        // Ignore
                    }
                }
                players.remove(playerId);
                call.resolve();
            });
        } else {
            if (instance.player != null) {
                if (instance.textureView != null) {
                    instance.player.clearVideoTextureView(instance.textureView);
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
                        if (instance.textureView != null) {
                            instance.player.clearVideoTextureView(instance.textureView);
                        }
                        instance.player.release();
                    }
                    if (instance.textureView != null) {
                        // Hide TextureView using INVISIBLE instead of removing from parent
                        // This maintains the view hierarchy and z-order
                        instance.textureView.setVisibility(android.view.View.INVISIBLE);
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
                    if (instance.textureView != null) {
                        instance.player.clearVideoTextureView(instance.textureView);
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
