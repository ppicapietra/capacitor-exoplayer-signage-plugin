export interface ExoPlayerSignagePlugin {
  echo(options: { value: string }): Promise<{ value: string }>;
}
