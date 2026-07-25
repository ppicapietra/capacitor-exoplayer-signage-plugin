export interface ExoPlayerSignagePlugin {
  createPlayer(options: { type: 'video' | 'audio'; volume?: number }): Promise<{ playerId: string }>;
  play(options: { playerId: string; url: string; visible?: boolean; authToken?: string }): Promise<{ status: string }>;
  stop(options: { playerId: string }): Promise<void>;
  pause(options: { playerId: string }): Promise<void>;
  setVolume(options: { playerId: string; volume: number }): Promise<void>;
  hide(options: { playerId: string }): Promise<void>;
  show(options: { playerId: string }): Promise<void>;
  releasePlayer(options: { playerId: string }): Promise<void>;
  /**
   * Position/size the shared video SurfaceView within the WebView viewport.
   * Coordinates are normalized fractions (0..1) of the WebView size.
   * resizeMode: 'fill' stretches to the rect; 'fit' letterboxes within the rect.
   */
  setVideoBounds(options: {
    x: number;
    y: number;
    width: number;
    height: number;
    resizeMode?: 'fill' | 'fit';
  }): Promise<void>;
  setVideoSurfaceVisibility(options: { visible: boolean }): Promise<void>;
  addListener(eventName: 'audioPlaybackEnded', listenerFunc: (data: { playerId: string }) => void): Promise<any>;
  removeAllListeners(): Promise<void>;
}
