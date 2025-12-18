export interface ExoPlayerSignagePlugin {
  createPlayer(options: { type: 'video' | 'audio' }): Promise<{ playerId: string }>;
  play(options: { playerId: string; url: string; visible?: boolean }): Promise<{ status: string }>;
  stop(options: { playerId: string }): Promise<void>;
  pause(options: { playerId: string }): Promise<void>;
  setVolume(options: { playerId: string; volume: number }): Promise<void>;
  hide(options: { playerId: string }): Promise<void>;
  show(options: { playerId: string }): Promise<void>;
  releasePlayer(options: { playerId: string }): Promise<void>;
  addListener(eventName: 'audioPlaybackEnded', listenerFunc: (data: { playerId: string }) => void): Promise<any>;
  removeAllListeners(): Promise<void>;
}
