export interface ExoPlayerSignagePlugin {
  play(options: { url: string }): Promise<{ status: string }>;
  stop(): Promise<void>;
  pause(): Promise<void>;
  setVolume(options: { volume: number }): Promise<void>;
}