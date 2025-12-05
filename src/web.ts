import { WebPlugin } from '@capacitor/core';

import type { ExoPlayerSignagePlugin } from './definitions';

export class ExoPlayerSignageWeb extends WebPlugin implements ExoPlayerSignagePlugin {
  // Implementación mínima para web (solo para que compile y funcione en iOS/PWA si llegas a usarlos)
  // En Android TV nunca se va a ejecutar este código, así que podemos lanzar un error claro

  async play(_options: { url: string }): Promise<{ status: string }> {
    console.warn('ExoPlayerSignage: play() no está disponible en web. Solo funciona en Android.');
    throw this.unimplemented('play() no está implementado en web. Usa Android.');
  }

  async stop(): Promise<void> {
    console.warn('ExoPlayerSignage: stop() no está disponible en web.');
    throw this.unimplemented('stop() no está implementado en web.');
  }

  async pause(): Promise<void> {
    console.warn('ExoPlayerSignage: pause() no está disponible en web.');
    throw this.unimplemented('pause() no está implementado en web.');
  }

  async setVolume(_options: { volume: number }): Promise<void> {
    console.warn('ExoPlayerSignage: setVolume() no está disponible en web.');
    throw this.unimplemented('setVolume() no está implementado en web.');
  }
}