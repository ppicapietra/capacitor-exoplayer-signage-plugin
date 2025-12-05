import { WebPlugin } from '@capacitor/core';

import type { ExoPlayerSignagePlugin } from './definitions';

export class ExoPlayerSignageWeb extends WebPlugin implements ExoPlayerSignagePlugin {
  async echo(options: { value: string }): Promise<{ value: string }> {
    console.log('ECHO', options);
    return options;
  }
}
