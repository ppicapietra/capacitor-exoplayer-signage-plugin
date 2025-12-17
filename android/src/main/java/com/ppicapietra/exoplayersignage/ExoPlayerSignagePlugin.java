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
import com.google.android.exoplayer2.audio.AudioAttributes;
import com.google.android.exoplayer2.source.DefaultMediaSourceFactory;
import com.google.android.exoplayer2.upstream.cache.CacheDataSource;
import com.google.android.exoplayer2.upstream.cache.LeastRecentlyUsedCacheEvictor;
import com.google.android.exoplayer2.upstream.cache.SimpleCache;
import com.google.android.exoplayer2.database.StandaloneDatabaseProvider;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource;
import com.google.android.exoplayer2.C;

import java.io.File;

@CapacitorPlugin(name = "ExoPlayerSignage")
public class ExoPlayerSignagePlugin extends Plugin {

    private ExoPlayer player;
    private SimpleCache cache;
    private CacheDataSource.Factory cacheDataSourceFactory;
    private SurfaceView surfaceView;

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

        cacheDataSourceFactory = new CacheDataSource.Factory()
                .setCache(cache)
                .setUpstreamDataSourceFactory(new DefaultHttpDataSource.Factory())
                .setFlags(CacheDataSource.FLAG_BLOCK_ON_CACHE | CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR);

        // ExoPlayer initialization and configuration should be on main thread
        android.app.Activity activity = getBridge().getActivity();
        if (activity != null) {
            activity.runOnUiThread(() -> {
                // Create SurfaceView for video rendering
                surfaceView = new SurfaceView(getContext());
                FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                );
                surfaceView.setLayoutParams(params);
                // Set z-order to ensure SurfaceView is above WebView for video rendering
                surfaceView.setZOrderMediaOverlay(true); // Above WebView for video overlay
                surfaceView.setZOrderOnTop(false); // Not on top (allows UI overlays)
                
                // Add SurfaceView to root view (initially hidden)
                ViewGroup rootView = (ViewGroup) activity.findViewById(android.R.id.content);
                if (rootView != null) {
                    rootView.addView(surfaceView);
                    surfaceView.setVisibility(android.view.View.GONE); // Hidden until video plays
                }
                
                player = new ExoPlayer.Builder(getContext())
                        .setMediaSourceFactory(
                                new DefaultMediaSourceFactory(getContext())
                                        .setDataSourceFactory(cacheDataSourceFactory))
                        .build();
                
                // Associate SurfaceView with ExoPlayer
                player.setVideoSurfaceView(surfaceView);

                // Configure AudioAttributes for muted playback
                AudioAttributes audioAttributes = new AudioAttributes.Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.CONTENT_TYPE_MOVIE)
                        .build();
                player.setAudioAttributes(audioAttributes, false); // false = don't handle audio focus

                player.setPlayWhenReady(true);
                player.setRepeatMode(ExoPlayer.REPEAT_MODE_OFF);
                player.setVolume(0.0f); // Mute videos by default (signage videos should be silent)
            });
        } else {
            // Fallback if activity is not available (shouldn't happen during load, but safety check)
            player = new ExoPlayer.Builder(getContext())
                    .setMediaSourceFactory(
                            new DefaultMediaSourceFactory(getContext())
                                    .setDataSourceFactory(cacheDataSourceFactory))
                    .build();

            // Configure AudioAttributes for muted playback
            AudioAttributes audioAttributes = new AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.CONTENT_TYPE_MOVIE)
                    .build();
            player.setAudioAttributes(audioAttributes, false); // false = don't handle audio focus

            player.setPlayWhenReady(true);
            player.setRepeatMode(ExoPlayer.REPEAT_MODE_OFF);
            player.setVolume(0.0f); // Mute videos by default (signage videos should be silent)
        }
    }

    @PluginMethod
    public void play(PluginCall call) {
        String url = call.getString("url");
        if (url == null) {
            call.reject("URL requerida");
            return;
        }
        
        // Get visible parameter (default to true for backward compatibility)
        Boolean visibleValue = call.getBoolean("visible", true);
        boolean visible = visibleValue != null ? visibleValue : true;
        
        // ExoPlayer must be accessed from the main thread
        android.app.Activity activity = getBridge().getActivity();
        if (activity == null) {
            call.reject("Activity not available");
            return;
        }
        
        activity.runOnUiThread(() -> {
            try {
                // Associate SurfaceView with player (always needed for playback)
                if (surfaceView != null) {
                    // Set visibility based on visible parameter
                    surfaceView.setVisibility(visible ? android.view.View.VISIBLE : android.view.View.GONE);
                    // Re-associate in case it was cleared
                    // Note: We don't call bringToFront() to allow WebView overlays (like modals) to appear above the video
                    player.setVideoSurfaceView(surfaceView);
                }
                
                // Set media item first
                player.setMediaItem(MediaItem.fromUri(Uri.parse(url)));
                player.prepare();
                
                // Ensure volume is set to 0 AFTER prepare() to ensure it takes effect
                player.setVolume(0.0f); // Ensure videos are muted (signage videos should be silent)
                
                // Start playback
                player.play();
                
                // Set volume again after play() as additional safeguard
                player.setVolume(0.0f);
                
                call.resolve();
            } catch (Exception e) {
                call.reject("Error playing: " + e.getMessage(), e);
            }
        });
    }

    @PluginMethod
    public void pause(PluginCall call) {
        // ExoPlayer must be accessed from the main thread
        android.app.Activity activity = getBridge().getActivity();
        if (activity == null) {
            call.reject("Activity not available");
            return;
        }
        
        activity.runOnUiThread(() -> {
            try {
                player.pause();
                call.resolve();
            } catch (Exception e) {
                call.reject("Error pausing: " + e.getMessage(), e);
            }
        });
    }

    @PluginMethod
    public void stop(PluginCall call) {
        // ExoPlayer must be accessed from the main thread
        android.app.Activity activity = getBridge().getActivity();
        if (activity == null) {
            call.reject("Activity not available");
            return;
        }
        
        activity.runOnUiThread(() -> {
            try {
                player.stop();
                // Hide SurfaceView when stopped
                if (surfaceView != null) {
                    surfaceView.setVisibility(android.view.View.GONE);
                    player.clearVideoSurfaceView(surfaceView);
                }
                call.resolve();
            } catch (Exception e) {
                call.reject("Error stopping: " + e.getMessage(), e);
            }
        });
    }

    @PluginMethod
    public void setVolume(PluginCall call) {
        Double volumeValue = call.getDouble("volume", 1.0);
        float vol = volumeValue != null ? volumeValue.floatValue() : 1.0f;
        
        // ExoPlayer must be accessed from the main thread
        android.app.Activity activity = getBridge().getActivity();
        if (activity == null) {
            call.reject("Activity not available");
            return;
        }
        
        activity.runOnUiThread(() -> {
            try {
                player.setVolume(vol);
                call.resolve();
            } catch (Exception e) {
                call.reject("Error setting volume: " + e.getMessage(), e);
            }
        });
    }

    @PluginMethod
    public void hide(PluginCall call) {
        // Hide SurfaceView without stopping playback
        android.app.Activity activity = getBridge().getActivity();
        if (activity == null) {
            call.reject("Activity not available");
            return;
        }
        
        activity.runOnUiThread(() -> {
            try {
                if (surfaceView != null) {
                    surfaceView.setVisibility(android.view.View.GONE);
                }
                call.resolve();
            } catch (Exception e) {
                call.reject("Error hiding SurfaceView: " + e.getMessage(), e);
            }
        });
    }

    @PluginMethod
    public void show(PluginCall call) {
        // Show SurfaceView without restarting playback
        android.app.Activity activity = getBridge().getActivity();
        if (activity == null) {
            call.reject("Activity not available");
            return;
        }
        
        activity.runOnUiThread(() -> {
            try {
                if (surfaceView != null && player != null) {
                    surfaceView.setVisibility(android.view.View.VISIBLE);
                    // Re-associate SurfaceView with player in case it was cleared
                    player.setVideoSurfaceView(surfaceView);
                }
                call.resolve();
            } catch (Exception e) {
                call.reject("Error showing SurfaceView: " + e.getMessage(), e);
            }
        });
    }

    @Override
    protected void handleOnDestroy() {
        // ExoPlayer must be accessed from the main thread
        android.app.Activity activity = getBridge().getActivity();
        if (activity != null) {
            activity.runOnUiThread(() -> {
                if (player != null) {
                    // Clear video surface before releasing
                    if (surfaceView != null) {
                        player.clearVideoSurfaceView(surfaceView);
                    }
                    player.release();
                    player = null;
                }
                if (surfaceView != null) {
                    // Remove SurfaceView from view hierarchy
                    ViewGroup parent = (ViewGroup) surfaceView.getParent();
                    if (parent != null) {
                        parent.removeView(surfaceView);
                    }
                    surfaceView = null;
                }
                if (cache != null) {
                    cache.release();
                    cache = null;
                }
            });
        } else {
            // Fallback if activity is not available (shouldn't happen, but safety check)
            if (player != null) {
                if (surfaceView != null) {
                    player.clearVideoSurfaceView(surfaceView);
                }
                player.release();
                player = null;
            }
            if (surfaceView != null) {
                surfaceView = null;
            }
            if (cache != null) {
                cache.release();
                cache = null;
            }
        }
    }
}