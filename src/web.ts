import { WebPlugin } from '@capacitor/core';

import type { ExoPlayerSignagePlugin } from './definitions';

export class ExoPlayerSignageWeb extends WebPlugin implements ExoPlayerSignagePlugin {
  async createPlayer(_options: { type: 'video' | 'audio' }): Promise<{ playerId: string }> {
    console.warn('ExoPlayerSignage: createPlayer() no está disponible en web. Solo funciona en Android.');
    throw this.unimplemented('createPlayer() no está implementado en web. Usa Android.');
  }

  async play(_options: { playerId: string; url: string; visible?: boolean }): Promise<{ status: string }> {
    console.warn('ExoPlayerSignage: play() no está disponible en web. Solo funciona en Android.');
    throw this.unimplemented('play() no está implementado en web. Usa Android.');
  }

  async stop(_options: { playerId: string }): Promise<void> {
    console.warn('ExoPlayerSignage: stop() no está disponible en web.');
    throw this.unimplemented('stop() no está implementado en web.');
  }

  async pause(_options: { playerId: string }): Promise<void> {
    console.warn('ExoPlayerSignage: pause() no está disponible en web.');
    throw this.unimplemented('pause() no está implementado en web.');
  }

  async setVolume(_options: { playerId: string; volume: number }): Promise<void> {
    console.warn('ExoPlayerSignage: setVolume() no está disponible en web.');
    throw this.unimplemented('setVolume() no está implementado en web.');
  }

  async hide(_options: { playerId: string }): Promise<void> {
    console.warn('ExoPlayerSignage: hide() no está disponible en web.');
    throw this.unimplemented('hide() no está implementado en web.');
  }

  async show(_options: { playerId: string }): Promise<void> {
    console.warn('ExoPlayerSignage: show() no está disponible en web.');
    throw this.unimplemented('show() no está implementado en web.');
  }

  async releasePlayer(_options: { playerId: string }): Promise<void> {
    console.warn('ExoPlayerSignage: releasePlayer() no está disponible en web.');
    throw this.unimplemented('releasePlayer() no está implementado en web.');
  }

  async addListener(_eventName: 'audioPlaybackEnded', _listenerFunc: (data: { playerId: string }) => void): Promise<any> {
    console.warn('ExoPlayerSignage: addListener() no está disponible en web.');
    throw this.unimplemented('addListener() no está implementado en web.');
  }
}
