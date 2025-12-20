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
    
    // Shared SurfaceView for video playback - added directly to rootView at index 0
    // This ensures videos stay below the WebView (which contains the modal HTML)
    private SurfaceView videoSurfaceView;
    
    // Pending player to associate with SurfaceView when SurfaceHolder is ready
    private ExoPlayer pendingPlayer;
    
    // Helper class to manage a single player instance
    private static class PlayerInstance {
        ExoPlayer player;
        SurfaceView surfaceView; // SurfaceView for video playback
        String type; // "video" or "audio"
        String id;
        
        PlayerInstance(ExoPlayer player, SurfaceView surfaceView, String type, String id) {
            this.player = player;
            this.surfaceView = surfaceView;
            this.type = type;
            this.id = id;
        }
    }
    
    /**
     * Get or create the SurfaceView for video playback.
     * SurfaceView is added directly to rootView at index 0 to ensure it stays below WebView.
     * Note: This method should only be called from the UI thread (runOnUiThread).
     */
    private SurfaceView getOrCreateVideoSurfaceView() {
        if (videoSurfaceView != null) {
            return videoSurfaceView;
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
        
        // Get the decorView (the root of the window)
        // This ensures the SurfaceView is added at the very bottom of the view hierarchy
        android.view.ViewGroup decorView = (android.view.ViewGroup) activity.getWindow().getDecorView();
        if (decorView == null) {
            return null;
        }
        
        // Create SurfaceView
        videoSurfaceView = new SurfaceView(getContext());
        
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        );
        videoSurfaceView.setLayoutParams(params);
        
        // Set background color to TRANSPARENT so video content is visible
        // Note: SurfaceView background is drawn ABOVE the Surface content, so it must be transparent
        videoSurfaceView.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        android.util.Log.d("ExoPlayerSignage", "🎨 DEBUG: SurfaceView background set to TRANSPARENT (video will be visible)");
        
        // Set up SurfaceHolder callback to ensure SurfaceView is ready before associating with player
        videoSurfaceView.getHolder().addCallback(new android.view.SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(android.view.SurfaceHolder holder) {
                android.util.Log.d("ExoPlayerSignage", "✅ SurfaceHolder created - SurfaceView is ready");
                android.util.Log.d("ExoPlayerSignage", "🔍 DEBUG: SurfaceHolder.isValid: " + (holder.getSurface() != null && holder.getSurface().isValid()));
                if (holder.getSurface() != null) {
                    android.util.Log.d("ExoPlayerSignage", "🔍 DEBUG: Surface size: " + holder.getSurfaceFrame().width() + "x" + holder.getSurfaceFrame().height());
                }
                android.util.Log.d("ExoPlayerSignage", "🔍 DEBUG: SurfaceView visibility: " + 
                    (videoSurfaceView.getVisibility() == android.view.View.VISIBLE ? "VISIBLE" : 
                     videoSurfaceView.getVisibility() == android.view.View.INVISIBLE ? "INVISIBLE" : "GONE"));
                android.util.Log.d("ExoPlayerSignage", "🔍 DEBUG: SurfaceView dimensions: " + 
                    videoSurfaceView.getWidth() + "x" + videoSurfaceView.getHeight());
                android.util.Log.d("ExoPlayerSignage", "🔍 DEBUG: SurfaceView isShown: " + videoSurfaceView.isShown());
                android.util.Log.d("ExoPlayerSignage", "🔍 DEBUG: pendingPlayer is " + (pendingPlayer != null ? "NOT null" : "null"));
                
                // If there's a pending player, associate it now
                if (pendingPlayer != null) {
                    android.util.Log.d("ExoPlayerSignage", "🎬 Associating pending player with SurfaceView");
                    android.util.Log.d("ExoPlayerSignage", "🔍 DEBUG: Player state BEFORE association: " + 
                        getPlaybackStateString(pendingPlayer.getPlaybackState()));
                    android.util.Log.d("ExoPlayerSignage", "🔍 DEBUG: Player isPlaying BEFORE: " + pendingPlayer.isPlaying());
                    
                    pendingPlayer.setVideoSurfaceView(videoSurfaceView);
                    
                    android.util.Log.d("ExoPlayerSignage", "🔍 DEBUG: Player state AFTER association: " + 
                        getPlaybackStateString(pendingPlayer.getPlaybackState()));
                    android.util.Log.d("ExoPlayerSignage", "🔍 DEBUG: Player isPlaying AFTER: " + pendingPlayer.isPlaying());
                    
                    // Ensure SurfaceView is visible when player is associated
                    videoSurfaceView.setVisibility(android.view.View.VISIBLE);
                    android.util.Log.d("ExoPlayerSignage", "✅ SurfaceView visibility set to VISIBLE (player associated)");
                    android.util.Log.d("ExoPlayerSignage", "🔍 DEBUG: SurfaceView isShown after setting VISIBLE: " + videoSurfaceView.isShown());
                    
                    pendingPlayer = null; // Clear pending player
                } else {
                    android.util.Log.d("ExoPlayerSignage", "⚠️ DEBUG: No pending player to associate");
                }
            }
            
            @Override
            public void surfaceChanged(android.view.SurfaceHolder holder, int format, int width, int height) {
                android.util.Log.d("ExoPlayerSignage", "SurfaceHolder changed: " + width + "x" + height);
                android.util.Log.d("ExoPlayerSignage", "🔍 DEBUG: SurfaceHolder format: " + format);
                android.util.Log.d("ExoPlayerSignage", "🔍 DEBUG: SurfaceView dimensions: " + 
                    videoSurfaceView.getWidth() + "x" + videoSurfaceView.getHeight());
                android.util.Log.d("ExoPlayerSignage", "🔍 DEBUG: SurfaceView visibility: " + 
                    (videoSurfaceView.getVisibility() == android.view.View.VISIBLE ? "VISIBLE" : 
                     videoSurfaceView.getVisibility() == android.view.View.INVISIBLE ? "INVISIBLE" : "GONE"));
                android.util.Log.d("ExoPlayerSignage", "🔍 DEBUG: SurfaceView isShown: " + videoSurfaceView.isShown());
                android.util.Log.d("ExoPlayerSignage", "🔍 DEBUG: SurfaceView parent: " + 
                    (videoSurfaceView.getParent() != null ? videoSurfaceView.getParent().getClass().getName() : "null"));
                
                // Ensure SurfaceView is visible if there's a player associated with it
                // Check if any video player is currently playing
                boolean foundPlayer = false;
                for (PlayerInstance instance : players.values()) {
                    if ("video".equals(instance.type) && instance.surfaceView == videoSurfaceView) {
                        foundPlayer = true;
                        ExoPlayer player = instance.player;
                        android.util.Log.d("ExoPlayerSignage", "🔍 DEBUG: Found video player instance: " + instance.id);
                        if (player != null) {
                            android.util.Log.d("ExoPlayerSignage", "🔍 DEBUG: Player state: " + getPlaybackStateString(player.getPlaybackState()));
                            android.util.Log.d("ExoPlayerSignage", "🔍 DEBUG: Player isPlaying: " + player.isPlaying());
                            android.util.Log.d("ExoPlayerSignage", "🔍 DEBUG: Player currentPosition: " + player.getCurrentPosition() + "ms");
                            android.util.Log.d("ExoPlayerSignage", "🔍 DEBUG: Player duration: " + player.getDuration() + "ms");
                            
                            if (player.getPlaybackState() != Player.STATE_IDLE && 
                                player.getPlaybackState() != Player.STATE_ENDED) {
                                // Player is playing or buffering - ensure SurfaceView is visible
                                if (videoSurfaceView.getVisibility() != android.view.View.VISIBLE) {
                                    videoSurfaceView.setVisibility(android.view.View.VISIBLE);
                                    android.util.Log.d("ExoPlayerSignage", "✅ SurfaceView visibility set to VISIBLE (player is playing)");
                                } else {
                                    android.util.Log.d("ExoPlayerSignage", "✅ SurfaceView already VISIBLE");
                                }
                            } else {
                                android.util.Log.d("ExoPlayerSignage", "⚠️ DEBUG: Player is IDLE or ENDED, not ensuring visibility");
                            }
                        } else {
                            android.util.Log.w("ExoPlayerSignage", "⚠️ DEBUG: Player instance has null player!");
                        }
                        break;
                    }
                }
                if (!foundPlayer) {
                    android.util.Log.w("ExoPlayerSignage", "⚠️ DEBUG: No video player instance found for this SurfaceView!");
                }
            }
            
            @Override
            public void surfaceDestroyed(android.view.SurfaceHolder holder) {
                android.util.Log.d("ExoPlayerSignage", "⚠️ SurfaceHolder destroyed");
                // Clear any pending player association
                pendingPlayer = null;
            }
        });
        
        // Add SurfaceView directly to DecorView at index 0 (background layer)
        // This ensures it's below everything else (WebView, LinearLayout, etc.)
        decorView.addView(videoSurfaceView, 0);
        
        // IMPORTANT: Try to make LinearLayout and its children (especially FrameLayout with WebView) transparent
        // so SurfaceView is visible
        for (int i = 0; i < decorView.getChildCount(); i++) {
            android.view.View child = decorView.getChildAt(i);
            if (child != videoSurfaceView && child.getClass().getName().contains("LinearLayout")) {
                android.util.Log.d("ExoPlayerSignage", "🔍 DEBUG: Found LinearLayout at index " + i);
                if (child.getBackground() != null) {
                    android.util.Log.w("ExoPlayerSignage", "⚠️ LinearLayout has background - attempting to make transparent");
                    child.setBackgroundColor(android.graphics.Color.TRANSPARENT);
                    android.util.Log.d("ExoPlayerSignage", "✅ Set LinearLayout background to TRANSPARENT");
                } else {
                    android.util.Log.d("ExoPlayerSignage", "✅ LinearLayout already has no background (transparent)");
                }
                
                // Also make FrameLayout children transparent (this contains the WebView)
                if (child instanceof ViewGroup) {
                    ViewGroup linearLayout = (ViewGroup) child;
                    for (int j = 0; j < linearLayout.getChildCount(); j++) {
                        android.view.View grandchild = linearLayout.getChildAt(j);
                        if (grandchild instanceof ViewGroup) {
                            ViewGroup frameLayout = (ViewGroup) grandchild;
                            android.util.Log.d("ExoPlayerSignage", "🔍 DEBUG: Found ViewGroup child in LinearLayout: " + frameLayout.getClass().getName());
                            android.util.Log.d("ExoPlayerSignage", "🔍 DEBUG: FrameLayout dimensions: " + frameLayout.getWidth() + "x" + frameLayout.getHeight());
                            
                            if (frameLayout.getBackground() != null) {
                                android.util.Log.w("ExoPlayerSignage", "⚠️ FrameLayout has background - attempting to make transparent");
                                frameLayout.setBackgroundColor(android.graphics.Color.TRANSPARENT);
                                android.util.Log.d("ExoPlayerSignage", "✅ Set FrameLayout background to TRANSPARENT");
                            } else {
                                android.util.Log.d("ExoPlayerSignage", "✅ FrameLayout already has no background (transparent)");
                            }
                            
                            // If FrameLayout covers the entire screen, it's likely covering the SurfaceView
                            if (frameLayout.getWidth() > 0 && frameLayout.getHeight() > 0 && 
                                frameLayout.getWidth() == videoSurfaceView.getWidth() && 
                                frameLayout.getHeight() == videoSurfaceView.getHeight()) {
                                android.util.Log.w("ExoPlayerSignage", "⚠️ FrameLayout tiene las mismas dimensiones que SurfaceView (1280x720)");
                                android.util.Log.w("ExoPlayerSignage", "⚠️ El contenido HTML dentro del WebView probablemente tiene fondo opaco");
                                android.util.Log.w("ExoPlayerSignage", "⚠️ SOLUCIÓN: El HTML debe tener fondo transparente cuando no hay contenido modal");
                                
                                // Try to find WebView inside FrameLayout
                                for (int k = 0; k < frameLayout.getChildCount(); k++) {
                                    android.view.View webViewCandidate = frameLayout.getChildAt(k);
                                    android.util.Log.d("ExoPlayerSignage", "🔍 DEBUG: FrameLayout child " + k + ": " + webViewCandidate.getClass().getName());
                                    if (webViewCandidate.getClass().getName().contains("WebView")) {
                                        android.util.Log.d("ExoPlayerSignage", "🌐 Found WebView inside FrameLayout!");
                                        try {
                                            webViewCandidate.setBackgroundColor(android.graphics.Color.TRANSPARENT);
                                            android.util.Log.d("ExoPlayerSignage", "✅ Set WebView background to TRANSPARENT");
                                        } catch (Exception e) {
                                            android.util.Log.w("ExoPlayerSignage", "⚠️ Could not set WebView background: " + e.getMessage());
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                break;
            }
        }
        
        // Set SurfaceView z-order to ensure it's visible
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            videoSurfaceView.setZ(0f);
            videoSurfaceView.setElevation(0f);
            android.util.Log.d("ExoPlayerSignage", "🔍 DEBUG: Set SurfaceView z-order to 0, elevation to 0");
        }
        android.util.Log.d("ExoPlayerSignage", "✅ Added SurfaceView to DecorView at index 0 (background layer)");
        android.util.Log.d("ExoPlayerSignage", "📊 DEBUG: DecorView child count after adding SurfaceView: " + decorView.getChildCount());
        android.util.Log.d("ExoPlayerSignage", "📏 DEBUG: SurfaceView dimensions after adding: " + 
            videoSurfaceView.getWidth() + "x" + videoSurfaceView.getHeight());
        android.util.Log.d("ExoPlayerSignage", "👁️ DEBUG: SurfaceView isShown after adding: " + videoSurfaceView.isShown());
        
        // Log all children of DecorView to understand the view hierarchy
        android.util.Log.d("ExoPlayerSignage", "🔍 DEBUG: Analyzing DecorView children:");
        for (int i = 0; i < decorView.getChildCount(); i++) {
            android.view.View child = decorView.getChildAt(i);
            android.util.Log.d("ExoPlayerSignage", "  Child " + i + ": " + child.getClass().getName());
            android.util.Log.d("ExoPlayerSignage", "    Visibility: " + 
                (child.getVisibility() == android.view.View.VISIBLE ? "VISIBLE" : 
                 child.getVisibility() == android.view.View.INVISIBLE ? "INVISIBLE" : "GONE"));
            android.util.Log.d("ExoPlayerSignage", "    Dimensions: " + child.getWidth() + "x" + child.getHeight());
            android.util.Log.d("ExoPlayerSignage", "    Alpha: " + child.getAlpha());
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                android.util.Log.d("ExoPlayerSignage", "    Z-order: " + child.getZ() + ", Elevation: " + child.getElevation());
            }
            if (child.getBackground() != null) {
                android.util.Log.d("ExoPlayerSignage", "    Has background: " + child.getBackground().getClass().getName());
            } else {
                android.util.Log.d("ExoPlayerSignage", "    No background (transparent)");
            }
            
            // If it's a ViewGroup, check its children too (recursively to find WebView)
            if (child instanceof ViewGroup) {
                ViewGroup vg = (ViewGroup) child;
                android.util.Log.d("ExoPlayerSignage", "    Is ViewGroup with " + vg.getChildCount() + " children");
                for (int j = 0; j < Math.min(vg.getChildCount(), 10); j++) { // Check up to 10 children
                    android.view.View grandchild = vg.getChildAt(j);
                    android.util.Log.d("ExoPlayerSignage", "      Grandchild " + j + ": " + grandchild.getClass().getName());
                    android.util.Log.d("ExoPlayerSignage", "        Dimensions: " + grandchild.getWidth() + "x" + grandchild.getHeight());
                    android.util.Log.d("ExoPlayerSignage", "        Visibility: " + 
                        (grandchild.getVisibility() == android.view.View.VISIBLE ? "VISIBLE" : 
                         grandchild.getVisibility() == android.view.View.INVISIBLE ? "INVISIBLE" : "GONE"));
                    if (grandchild.getBackground() != null) {
                        android.util.Log.d("ExoPlayerSignage", "        ⚠️ Has background: " + grandchild.getBackground().getClass().getName());
                        // Try to make it transparent if it's a FrameLayout (contains WebView)
                        if (grandchild.getClass().getName().contains("FrameLayout")) {
                            android.util.Log.w("ExoPlayerSignage", "        🔧 Making FrameLayout background transparent");
                            grandchild.setBackgroundColor(android.graphics.Color.TRANSPARENT);
                        }
                    } else {
                        android.util.Log.d("ExoPlayerSignage", "        ✅ No background (transparent)");
                    }
                    
                    // If it's a WebView, try to make it transparent
                    if (grandchild.getClass().getName().contains("WebView")) {
                        android.util.Log.d("ExoPlayerSignage", "        🌐 Found WebView!");
                        try {
                            grandchild.setBackgroundColor(android.graphics.Color.TRANSPARENT);
                            android.util.Log.d("ExoPlayerSignage", "        ✅ Set WebView background to TRANSPARENT");
                        } catch (Exception e) {
                            android.util.Log.w("ExoPlayerSignage", "        ⚠️ Could not set WebView background: " + e.getMessage());
                        }
                    }
                    
                    // Recursively search for WebView in nested ViewGroups
                    if (grandchild instanceof ViewGroup) {
                        ViewGroup nestedVg = (ViewGroup) grandchild;
                        android.util.Log.d("ExoPlayerSignage", "        Is nested ViewGroup with " + nestedVg.getChildCount() + " children");
                        for (int k = 0; k < Math.min(nestedVg.getChildCount(), 10); k++) {
                            android.view.View greatGrandchild = nestedVg.getChildAt(k);
                            android.util.Log.d("ExoPlayerSignage", "          Great-grandchild " + k + ": " + greatGrandchild.getClass().getName());
                            if (greatGrandchild.getClass().getName().contains("WebView")) {
                                android.util.Log.d("ExoPlayerSignage", "          🌐 Found WebView in nested ViewGroup!");
                                try {
                                    greatGrandchild.setBackgroundColor(android.graphics.Color.TRANSPARENT);
                                    android.util.Log.d("ExoPlayerSignage", "          ✅ Set nested WebView background to TRANSPARENT");
                                } catch (Exception e) {
                                    android.util.Log.w("ExoPlayerSignage", "          ⚠️ Could not set nested WebView background: " + e.getMessage());
                                }
                            }
                        }
                    }
                    
                    // Check if this grandchild covers the entire screen (same dimensions as SurfaceView)
                    if (grandchild.getWidth() > 0 && grandchild.getHeight() > 0 && 
                        grandchild.getWidth() == videoSurfaceView.getWidth() && 
                        grandchild.getHeight() == videoSurfaceView.getHeight()) {
                        android.util.Log.w("ExoPlayerSignage", "        ⚠️ Grandchild " + j + " tiene las mismas dimensiones que SurfaceView (1280x720) - podría estar cubriéndolo!");
                        android.util.Log.w("ExoPlayerSignage", "        ⚠️ Esto significa que el contenido HTML probablemente está cubriendo el video");
                    }
                }
            }
        }
        
        // Log view hierarchy for debugging
        ViewGroup parent = (ViewGroup) videoSurfaceView.getParent();
        if (parent != null) {
            android.util.Log.d("ExoPlayerSignage", "✅ Created SurfaceView and added to: " + parent.getClass().getName());
            android.util.Log.d("ExoPlayerSignage", "Parent child count: " + parent.getChildCount());
            
            // Verify SurfaceView has no z-order set (should be 0 or default)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                float surfaceZ = videoSurfaceView.getZ();
                if (surfaceZ != 0f) {
                    android.util.Log.w("ExoPlayerSignage", "⚠️ SurfaceView has z-order " + surfaceZ + " (should be 0)");
                    videoSurfaceView.setZ(0f);
                    videoSurfaceView.setElevation(0f);
                    android.util.Log.d("ExoPlayerSignage", "✅ Reset SurfaceView z-order to 0");
                }
            }
            
            for (int i = 0; i < parent.getChildCount(); i++) {
                android.view.View child = parent.getChildAt(i);
                String zInfo = "";
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                    zInfo = " (z: " + child.getZ() + ", elevation: " + child.getElevation() + ")";
                }
                android.util.Log.d("ExoPlayerSignage", "  Parent Child " + i + ": " + child.getClass().getName() + 
                    " (visibility: " + (child.getVisibility() == android.view.View.VISIBLE ? "VISIBLE" : 
                    child.getVisibility() == android.view.View.INVISIBLE ? "INVISIBLE" : "GONE") + ")" + zInfo);
                if (child == videoSurfaceView) {
                    android.util.Log.d("ExoPlayerSignage", "    ✅ This is our SurfaceView");
                } else {
                    // Check if this child has an opaque background that might cover SurfaceView
                    if (child.getBackground() != null) {
                        android.graphics.drawable.Drawable bg = child.getBackground();
                        android.util.Log.d("ExoPlayerSignage", "    ⚠️ Child " + i + " has background: " + bg.getClass().getName());
                        if (bg instanceof android.graphics.drawable.ColorDrawable) {
                            android.graphics.drawable.ColorDrawable colorBg = (android.graphics.drawable.ColorDrawable) bg;
                            int color = colorBg.getColor();
                            int alpha = (color >> 24) & 0xFF;
                            android.util.Log.d("ExoPlayerSignage", "    ⚠️ Background color alpha: " + alpha + " (255 = fully opaque, 0 = transparent)");
                            if (alpha == 255) {
                                android.util.Log.w("ExoPlayerSignage", "    ❌ PROBLEMA: Child " + i + " tiene fondo completamente opaco que cubre el SurfaceView!");
                            }
                        }
                    } else {
                        android.util.Log.d("ExoPlayerSignage", "    ✅ Child " + i + " no tiene fondo (transparente)");
                    }
                    
                    // If it's a ViewGroup, check if it covers the entire screen
                    if (child instanceof ViewGroup) {
                        ViewGroup vg = (ViewGroup) child;
                        android.util.Log.d("ExoPlayerSignage", "    Child " + i + " es ViewGroup con " + vg.getChildCount() + " hijos");
                        android.util.Log.d("ExoPlayerSignage", "    Dimensiones: " + vg.getWidth() + "x" + vg.getHeight());
                        if (vg.getWidth() > 0 && vg.getHeight() > 0 && 
                            vg.getWidth() == videoSurfaceView.getWidth() && 
                            vg.getHeight() == videoSurfaceView.getHeight()) {
                            android.util.Log.w("ExoPlayerSignage", "    ⚠️ Child " + i + " tiene las mismas dimensiones que SurfaceView - podría estar cubriéndolo!");
                        }
                    }
                }
            }
        }
        
        // Initially hidden - visibility will be controlled by the app
        videoSurfaceView.setVisibility(android.view.View.INVISIBLE);
        
        return videoSurfaceView;
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
                
                SurfaceView surfaceView = null;
                
                if ("video".equals(type)) {
                    // Get or create shared SurfaceView for video playback
                    surfaceView = getOrCreateVideoSurfaceView();
                    
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
        
        // Visibility is controlled by the app, not by the plugin
        android.app.Activity activity = getBridge().getActivity();
        if (activity == null) {
            call.reject("Activity not available");
            return;
        }
        
        activity.runOnUiThread(() -> {
            try {
                ExoPlayer player = instance.player;
                
                // Stop any current playback before setting new media item
                if (player.getPlaybackState() != Player.STATE_IDLE) {
                    player.stop();
                }
                
                if ("video".equals(instance.type)) {
                    // Video playback - ensure SurfaceView exists and is associated
                    // Visibility is controlled by the app, not by the plugin
                    if (instance.surfaceView == null) {
                        instance.surfaceView = getOrCreateVideoSurfaceView();
                    }
                    
                    // Associate SurfaceView with player AFTER MediaItem is set but BEFORE prepare()
                    // This ensures the SurfaceView is ready when the player prepares
                } else {
                    // Audio playback - ensure NO SurfaceView is associated
                    // Audio players should NEVER have a SurfaceView
                    if (instance.surfaceView != null) {
                        try {
                            player.clearVideoSurface();
                        } catch (Exception e) {
                            // Ignore errors
                        }
                        // Don't modify visibility - that's controlled by the app
                    }
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
                
                // For video players, associate SurfaceView AFTER MediaItem is set but BEFORE prepare()
                // Wait for SurfaceHolder to be ready before associating
                if ("video".equals(instance.type) && instance.surfaceView != null) {
                    android.util.Log.d("ExoPlayerSignage", "🎬 Preparing to associate SurfaceView with player");
                    android.util.Log.d("ExoPlayerSignage", "🔍 DEBUG: Player state BEFORE association: " + getPlaybackStateString(player.getPlaybackState()));
                    android.util.Log.d("ExoPlayerSignage", "🔍 DEBUG: URL: " + url);
                    android.util.Log.d("ExoPlayerSignage", "SurfaceView parent: " + 
                        (instance.surfaceView.getParent() != null ? instance.surfaceView.getParent().getClass().getName() : "null"));
                    android.util.Log.d("ExoPlayerSignage", "SurfaceView visibility BEFORE: " + 
                        (instance.surfaceView.getVisibility() == android.view.View.VISIBLE ? "VISIBLE" : 
                        instance.surfaceView.getVisibility() == android.view.View.INVISIBLE ? "INVISIBLE" : "GONE"));
                    android.util.Log.d("ExoPlayerSignage", "🔍 DEBUG: SurfaceView dimensions BEFORE: " + 
                        instance.surfaceView.getWidth() + "x" + instance.surfaceView.getHeight());
                    android.util.Log.d("ExoPlayerSignage", "🔍 DEBUG: SurfaceView isShown BEFORE: " + instance.surfaceView.isShown());
                    
                    // Check if SurfaceHolder is already ready
                    android.view.SurfaceHolder holder = instance.surfaceView.getHolder();
                    android.util.Log.d("ExoPlayerSignage", "🔍 DEBUG: SurfaceHolder check - surface != null: " + 
                        (holder.getSurface() != null));
                    if (holder.getSurface() != null) {
                        android.util.Log.d("ExoPlayerSignage", "🔍 DEBUG: SurfaceHolder.isValid: " + holder.getSurface().isValid());
                        android.util.Log.d("ExoPlayerSignage", "🔍 DEBUG: SurfaceHolder frame: " + holder.getSurfaceFrame().width() + "x" + holder.getSurfaceFrame().height());
                    }
                    
                    if (holder.getSurface() != null && holder.getSurface().isValid()) {
                        // SurfaceHolder is ready - associate immediately
                        android.util.Log.d("ExoPlayerSignage", "✅ SurfaceHolder is ready - associating immediately");
                        player.setVideoSurfaceView(instance.surfaceView);
                        android.util.Log.d("ExoPlayerSignage", "🔍 DEBUG: Player state AFTER setVideoSurfaceView: " + getPlaybackStateString(player.getPlaybackState()));
                        pendingPlayer = null; // Clear any pending player
                    } else {
                        // SurfaceHolder not ready yet - store player for callback
                        android.util.Log.d("ExoPlayerSignage", "⏳ SurfaceHolder not ready - will associate when surfaceCreated() is called");
                        pendingPlayer = player;
                    }
                    
                    // Make SurfaceView visible when playing video
                    instance.surfaceView.setVisibility(android.view.View.VISIBLE);
                    
                    android.util.Log.d("ExoPlayerSignage", "✅ SurfaceView visibility AFTER: VISIBLE");
                    android.util.Log.d("ExoPlayerSignage", "SurfaceView isShown: " + instance.surfaceView.isShown());
                    android.util.Log.d("ExoPlayerSignage", "SurfaceView width: " + instance.surfaceView.getWidth() + ", height: " + instance.surfaceView.getHeight());
                    
                    // Verify SurfaceView is still in DecorView
                    ViewGroup parent = (ViewGroup) instance.surfaceView.getParent();
                    if (parent != null) {
                        android.util.Log.d("ExoPlayerSignage", "✅ SurfaceView parent verified: " + parent.getClass().getName());
                        android.util.Log.d("ExoPlayerSignage", "Parent child count: " + parent.getChildCount());
                        
                        // Verify SurfaceView has no z-order (should be 0)
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                            float surfaceZ = instance.surfaceView.getZ();
                            if (surfaceZ != 0f) {
                                android.util.Log.w("ExoPlayerSignage", "⚠️ SurfaceView has z-order " + surfaceZ + " (should be 0) - resetting");
                                instance.surfaceView.setZ(0f);
                                instance.surfaceView.setElevation(0f);
                            }
                            android.util.Log.d("ExoPlayerSignage", "SurfaceView z-order: " + instance.surfaceView.getZ());
                        }
                        
                        for (int i = 0; i < parent.getChildCount(); i++) {
                            android.view.View child = parent.getChildAt(i);
                            if (child == instance.surfaceView) {
                                android.util.Log.d("ExoPlayerSignage", "  ✅ Found SurfaceView at index " + i);
                            } else {
                                String zInfo = "";
                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                                    zInfo = " (z: " + child.getZ() + ", elevation: " + child.getElevation() + ")";
                                }
                                android.util.Log.d("ExoPlayerSignage", "  Child " + i + ": " + child.getClass().getName() + 
                                    " (visibility: " + (child.getVisibility() == android.view.View.VISIBLE ? "VISIBLE" : 
                                    child.getVisibility() == android.view.View.INVISIBLE ? "INVISIBLE" : "GONE") + ")" + zInfo);
                            }
                        }
                    } else {
                        android.util.Log.e("ExoPlayerSignage", "❌ ERROR: SurfaceView has no parent!");
                    }
                }
                
                // Add player event listener for debugging
                if ("video".equals(instance.type)) {
                    player.addListener(new Player.Listener() {
                        @Override
                        public void onPlaybackStateChanged(int playbackState) {
                            android.util.Log.d("ExoPlayerSignage", "🔄 DEBUG: Player playbackState changed to: " + getPlaybackStateString(playbackState));
                            android.util.Log.d("ExoPlayerSignage", "🔄 DEBUG: Player isPlaying: " + player.isPlaying());
                            android.util.Log.d("ExoPlayerSignage", "🔄 DEBUG: Player currentPosition: " + player.getCurrentPosition() + "ms");
                            if (instance.surfaceView != null) {
                                android.util.Log.d("ExoPlayerSignage", "🔄 DEBUG: SurfaceView visibility: " + 
                                    (instance.surfaceView.getVisibility() == android.view.View.VISIBLE ? "VISIBLE" : 
                                     instance.surfaceView.getVisibility() == android.view.View.INVISIBLE ? "INVISIBLE" : "GONE"));
                                android.util.Log.d("ExoPlayerSignage", "🔄 DEBUG: SurfaceView isShown: " + instance.surfaceView.isShown());
                                android.util.Log.d("ExoPlayerSignage", "🔄 DEBUG: SurfaceView dimensions: " + 
                                    instance.surfaceView.getWidth() + "x" + instance.surfaceView.getHeight());
                            }
                        }
                        
                        @Override
                        public void onIsPlayingChanged(boolean isPlaying) {
                            android.util.Log.d("ExoPlayerSignage", "▶️ DEBUG: Player isPlaying changed to: " + isPlaying);
                            android.util.Log.d("ExoPlayerSignage", "▶️ DEBUG: Player playbackState: " + getPlaybackStateString(player.getPlaybackState()));
                            if (instance.surfaceView != null) {
                                android.util.Log.d("ExoPlayerSignage", "▶️ DEBUG: SurfaceView visibility: " + 
                                    (instance.surfaceView.getVisibility() == android.view.View.VISIBLE ? "VISIBLE" : 
                                     instance.surfaceView.getVisibility() == android.view.View.INVISIBLE ? "INVISIBLE" : "GONE"));
                            }
                        }
                    });
                }
                
                android.util.Log.d("ExoPlayerSignage", "🔍 DEBUG: About to call player.prepare()");
                android.util.Log.d("ExoPlayerSignage", "🔍 DEBUG: Player state BEFORE prepare: " + getPlaybackStateString(player.getPlaybackState()));
                if ("video".equals(instance.type) && instance.surfaceView != null) {
                    android.util.Log.d("ExoPlayerSignage", "🔍 DEBUG: SurfaceView visibility BEFORE prepare: " + 
                        (instance.surfaceView.getVisibility() == android.view.View.VISIBLE ? "VISIBLE" : 
                         instance.surfaceView.getVisibility() == android.view.View.INVISIBLE ? "INVISIBLE" : "GONE"));
                }
                
                player.prepare();
                
                android.util.Log.d("ExoPlayerSignage", "🔍 DEBUG: Player state AFTER prepare: " + getPlaybackStateString(player.getPlaybackState()));
                if ("video".equals(instance.type) && instance.surfaceView != null) {
                    android.util.Log.d("ExoPlayerSignage", "🔍 DEBUG: SurfaceView visibility AFTER prepare: " + 
                        (instance.surfaceView.getVisibility() == android.view.View.VISIBLE ? "VISIBLE" : 
                         instance.surfaceView.getVisibility() == android.view.View.INVISIBLE ? "INVISIBLE" : "GONE"));
                    android.util.Log.d("ExoPlayerSignage", "🔍 DEBUG: SurfaceView isShown AFTER prepare: " + instance.surfaceView.isShown());
                }
                
                // Ensure volume is set correctly (especially for audio after video)
                if ("audio".equals(instance.type)) {
                    player.setVolume(1.0f);
                } else if ("video".equals(instance.type)) {
                    player.setVolume(0.0f);
                }
                
                // Start playback
                android.util.Log.d("ExoPlayerSignage", "🔍 DEBUG: About to call player.play()");
                android.util.Log.d("ExoPlayerSignage", "🔍 DEBUG: Player state BEFORE play: " + getPlaybackStateString(player.getPlaybackState()));
                if ("video".equals(instance.type) && instance.surfaceView != null) {
                    android.util.Log.d("ExoPlayerSignage", "🔍 DEBUG: SurfaceView visibility BEFORE play: " + 
                        (instance.surfaceView.getVisibility() == android.view.View.VISIBLE ? "VISIBLE" : 
                         instance.surfaceView.getVisibility() == android.view.View.INVISIBLE ? "INVISIBLE" : "GONE"));
                }
                
                player.play();
                
                android.util.Log.d("ExoPlayerSignage", "🔍 DEBUG: Player state AFTER play: " + getPlaybackStateString(player.getPlaybackState()));
                android.util.Log.d("ExoPlayerSignage", "🔍 DEBUG: Player isPlaying AFTER play: " + player.isPlaying());
                if ("video".equals(instance.type) && instance.surfaceView != null) {
                    android.util.Log.d("ExoPlayerSignage", "🔍 DEBUG: SurfaceView visibility AFTER play: " + 
                        (instance.surfaceView.getVisibility() == android.view.View.VISIBLE ? "VISIBLE" : 
                         instance.surfaceView.getVisibility() == android.view.View.INVISIBLE ? "INVISIBLE" : "GONE"));
                    android.util.Log.d("ExoPlayerSignage", "🔍 DEBUG: SurfaceView isShown AFTER play: " + instance.surfaceView.isShown());
                    android.util.Log.d("ExoPlayerSignage", "🔍 DEBUG: SurfaceView dimensions AFTER play: " + 
                        instance.surfaceView.getWidth() + "x" + instance.surfaceView.getHeight());
                }
                
                // Schedule periodic status checks for video players
                if ("video".equals(instance.type) && instance.surfaceView != null) {
                    schedulePeriodicStatusCheck(instance);
                }
                
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
                // For video players, keep SurfaceView associated - don't clear it
                // Visibility is controlled by the app, not by the plugin
                // Audio players should NEVER have a SurfaceView
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
    public void setVideoSurfaceVisibility(PluginCall call) {
        Boolean visibleValue = call.getBoolean("visible", true);
        boolean visible = visibleValue != null ? visibleValue : true;
        
        android.app.Activity activity = getBridge().getActivity();
        if (activity == null) {
            call.reject("Activity not available");
            return;
        }
        
        activity.runOnUiThread(() -> {
            try {
                if (videoSurfaceView != null) {
                    android.util.Log.d("ExoPlayerSignage", "🔧 setVideoSurfaceVisibility called: " + visible);
                    android.util.Log.d("ExoPlayerSignage", "SurfaceView visibility BEFORE: " + 
                        (videoSurfaceView.getVisibility() == android.view.View.VISIBLE ? "VISIBLE" : 
                        videoSurfaceView.getVisibility() == android.view.View.INVISIBLE ? "INVISIBLE" : "GONE"));
                    android.util.Log.d("ExoPlayerSignage", "SurfaceView parent: " + 
                        (videoSurfaceView.getParent() != null ? videoSurfaceView.getParent().getClass().getName() : "null"));
                    android.util.Log.d("ExoPlayerSignage", "SurfaceView isShown: " + videoSurfaceView.isShown());
                    
                    videoSurfaceView.setVisibility(visible ? android.view.View.VISIBLE : android.view.View.INVISIBLE);
                    
                    android.util.Log.d("ExoPlayerSignage", "✅ SurfaceView visibility AFTER: " + 
                        (visible ? "VISIBLE" : "INVISIBLE"));
                    android.util.Log.d("ExoPlayerSignage", "SurfaceView isShown AFTER: " + videoSurfaceView.isShown());
                    
                    call.resolve();
                } else {
                    android.util.Log.e("ExoPlayerSignage", "❌ Video SurfaceView not created yet");
                    call.reject("Video SurfaceView not created yet");
                }
            } catch (Exception e) {
                android.util.Log.e("ExoPlayerSignage", "❌ Error setting SurfaceView visibility: " + e.getMessage(), e);
                call.reject("Error setting SurfaceView visibility: " + e.getMessage(), e);
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
                // Pause playback
                if (instance.player != null) {
                    instance.player.pause();
                }
                
                // Visibility is controlled by the app, not by the plugin
                // Don't modify SurfaceView visibility here
                // Audio players should NEVER have a SurfaceView
                
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
                    // Ensure no SurfaceView is associated for audio
                    if (instance.surfaceView != null) {
                        try {
                            instance.player.clearVideoSurface();
                        } catch (Exception e) {
                            // Ignore
                        }
                    }
                    call.resolve();
                    return;
                }
                
                // For video players, ensure SurfaceView is associated and resume playback if needed
                if ("video".equals(instance.type) && instance.player != null) {
                    // Ensure SurfaceView exists
                    if (instance.surfaceView == null) {
                        instance.surfaceView = getOrCreateVideoSurfaceView();
                    }
                    
                    // Associate SurfaceView with player (wait for SurfaceHolder if needed)
                    android.view.SurfaceHolder holder = instance.surfaceView.getHolder();
                    if (holder.getSurface() != null && holder.getSurface().isValid()) {
                        // SurfaceHolder is ready - associate immediately
                        android.util.Log.d("ExoPlayerSignage", "✅ SurfaceHolder ready in show() - associating immediately");
                        instance.player.setVideoSurfaceView(instance.surfaceView);
                        pendingPlayer = null; // Clear any pending player
                    } else {
                        // SurfaceHolder not ready yet - store player for callback
                        android.util.Log.d("ExoPlayerSignage", "⏳ SurfaceHolder not ready in show() - will associate when surfaceCreated() is called");
                        pendingPlayer = instance.player;
                    }
                    
                    // Check player state
                    int playbackState = instance.player.getPlaybackState();
                    boolean isPlaying = instance.player.isPlaying();
                    
                    // If player is in STATE_IDLE (after stop()), prepare and play
                    if (playbackState == Player.STATE_IDLE) {
                        instance.player.prepare();
                        instance.player.play();
                    } else if (!isPlaying) {
                        // Player is paused - resume playback
                        instance.player.play();
                    }
                    
                    // Visibility is controlled by the app, not by the plugin
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
                    // Clear pending player if this is the one waiting
                    if (pendingPlayer == instance.player) {
                        pendingPlayer = null;
                    }
                    if (instance.surfaceView != null && "video".equals(instance.type)) {
                        instance.player.clearVideoSurface();
                    }
                    instance.player.release();
                }
                // Don't remove SurfaceView - it's shared and reused
                // Visibility is controlled by the app
                players.remove(playerId);
                call.resolve();
            });
        } else {
            if (instance.player != null) {
                // Clear pending player if this is the one waiting
                if (pendingPlayer == instance.player) {
                    pendingPlayer = null;
                }
                if (instance.surfaceView != null && "video".equals(instance.type)) {
                    instance.player.clearVideoSurface();
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
                        // Clear pending player if this is the one waiting
                        if (pendingPlayer == instance.player) {
                            pendingPlayer = null;
                        }
                        if (instance.surfaceView != null && "video".equals(instance.type)) {
                            instance.player.clearVideoSurface();
                        }
                        instance.player.release();
                    }
                }
                players.clear();
                
                // Clear pending player reference
                pendingPlayer = null;
                
                // Don't remove SurfaceView - it's shared and will be cleaned up by Android
                // Visibility is controlled by the app
                
                if (cache != null) {
                    cache.release();
                    cache = null;
                }
            });
        } else {
            for (PlayerInstance instance : players.values()) {
                if (instance.player != null) {
                    if (instance.surfaceView != null && "video".equals(instance.type)) {
                        instance.player.clearVideoSurface();
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
    
    /**
     * Helper method to convert playback state to readable string
     */
    private String getPlaybackStateString(int state) {
        switch (state) {
            case Player.STATE_IDLE:
                return "STATE_IDLE";
            case Player.STATE_BUFFERING:
                return "STATE_BUFFERING";
            case Player.STATE_READY:
                return "STATE_READY";
            case Player.STATE_ENDED:
                return "STATE_ENDED";
            default:
                return "UNKNOWN(" + state + ")";
        }
    }
    
    /**
     * Schedule periodic status checks for video player debugging
     */
    private void schedulePeriodicStatusCheck(PlayerInstance instance) {
        android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
        handler.postDelayed(new Runnable() {
            private int checkCount = 0;
            private final int maxChecks = 10; // Check 10 times (every 2 seconds = 20 seconds total)
            
            @Override
            public void run() {
                if (checkCount >= maxChecks) {
                    android.util.Log.d("ExoPlayerSignage", "⏱️ DEBUG: Stopping periodic status checks (max reached)");
                    return;
                }
                
                checkCount++;
                android.util.Log.d("ExoPlayerSignage", "⏱️ DEBUG: Periodic check #" + checkCount);
                
                if (instance.player != null && "video".equals(instance.type)) {
                    android.util.Log.d("ExoPlayerSignage", "⏱️ DEBUG: Player state: " + getPlaybackStateString(instance.player.getPlaybackState()));
                    android.util.Log.d("ExoPlayerSignage", "⏱️ DEBUG: Player isPlaying: " + instance.player.isPlaying());
                    android.util.Log.d("ExoPlayerSignage", "⏱️ DEBUG: Player currentPosition: " + instance.player.getCurrentPosition() + "ms");
                    android.util.Log.d("ExoPlayerSignage", "⏱️ DEBUG: Player duration: " + instance.player.getDuration() + "ms");
                    
                    if (instance.surfaceView != null) {
                        android.util.Log.d("ExoPlayerSignage", "⏱️ DEBUG: SurfaceView visibility: " + 
                            (instance.surfaceView.getVisibility() == android.view.View.VISIBLE ? "VISIBLE" : 
                             instance.surfaceView.getVisibility() == android.view.View.INVISIBLE ? "INVISIBLE" : "GONE"));
                        android.util.Log.d("ExoPlayerSignage", "⏱️ DEBUG: SurfaceView isShown: " + instance.surfaceView.isShown());
                        android.util.Log.d("ExoPlayerSignage", "⏱️ DEBUG: SurfaceView dimensions: " + 
                            instance.surfaceView.getWidth() + "x" + instance.surfaceView.getHeight());
                        android.util.Log.d("ExoPlayerSignage", "⏱️ DEBUG: SurfaceView parent: " + 
                            (instance.surfaceView.getParent() != null ? instance.surfaceView.getParent().getClass().getName() : "null"));
                        
                        // Check SurfaceHolder
                        android.view.SurfaceHolder holder = instance.surfaceView.getHolder();
                        if (holder != null && holder.getSurface() != null) {
                            android.util.Log.d("ExoPlayerSignage", "⏱️ DEBUG: SurfaceHolder isValid: " + holder.getSurface().isValid());
                            android.util.Log.d("ExoPlayerSignage", "⏱️ DEBUG: SurfaceHolder frame: " + 
                                holder.getSurfaceFrame().width() + "x" + holder.getSurfaceFrame().height());
                        } else {
                            android.util.Log.w("ExoPlayerSignage", "⏱️ DEBUG: SurfaceHolder is null or has no surface!");
                        }
                    } else {
                        android.util.Log.w("ExoPlayerSignage", "⏱️ DEBUG: SurfaceView is null!");
                    }
                    
                    // Schedule next check (every 2 seconds)
                    handler.postDelayed(this, 2000);
                } else {
                    android.util.Log.d("ExoPlayerSignage", "⏱️ DEBUG: Player instance is null or not video, stopping checks");
                }
            }
        }, 2000); // First check after 2 seconds
    }
}
