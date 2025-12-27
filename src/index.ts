import { registerPlugin } from '@capacitor/core';

import type { ExoPlayerSignagePlugin } from './definitions';

const ExoPlayerSignage = registerPlugin<ExoPlayerSignagePlugin>('ExoPlayerSignage', {
  web: () => import('./web').then((m) => new m.ExoPlayerSignageWeb()),
});

export * from './definitions';
export { ExoPlayerSignage };
