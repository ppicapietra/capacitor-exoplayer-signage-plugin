
# @ppicapietra/capacitor-exoplayersignage-plugin

Plugin for ExoPlayer in Digital Signage with eternal cache

## Install

This plugin is not available in the public npm repository. To install it, use one of the following options:

### Option 1: Install from Git

```bash
npm install git+https://github.com/ppicapietra/capacitor-exoplayer-signage-plugin.git
npx cap sync
```

### Option 2: Local Installation

If you have the plugin locally:

```bash
npm install /path/to/plugin
npx cap sync
```

## API

<docgen-index>

* [`createPlayer(...)`](#createplayer)
* [`play(...)`](#play)
* [`stop(...)`](#stop)
* [`pause(...)`](#pause)
* [`setVolume(...)`](#setvolume)
* [`hide(...)`](#hide)
* [`show(...)`](#show)
* [`releasePlayer(...)`](#releaseplayer)
* [`setVideoBounds(...)`](#setvideobounds)
* [`setVideoSurfaceVisibility(...)`](#setvideosurfacevisibility)
* [`addListener('audioPlaybackEnded', ...)`](#addlisteneraudioplaybackended-)
* [`removeAllListeners()`](#removealllisteners)

</docgen-index>

<docgen-api>
<!--Update the source file JSDoc comments and rerun docgen to update the docs below-->

### createPlayer(...)

```typescript
createPlayer(options: { type: 'video' | 'audio'; volume?: number; }) => Promise<{ playerId: string; }>
```

| Param         | Type                                                        |
| ------------- | ----------------------------------------------------------- |
| **`options`** | <code>{ type: 'video' \| 'audio'; volume?: number; }</code> |

**Returns:** <code>Promise&lt;{ playerId: string; }&gt;</code>

--------------------


### play(...)

```typescript
play(options: { playerId: string; url: string; visible?: boolean; authToken?: string; }) => Promise<{ status: string; }>
```

| Param         | Type                                                                                   |
| ------------- | -------------------------------------------------------------------------------------- |
| **`options`** | <code>{ playerId: string; url: string; visible?: boolean; authToken?: string; }</code> |

**Returns:** <code>Promise&lt;{ status: string; }&gt;</code>

--------------------


### stop(...)

```typescript
stop(options: { playerId: string; }) => Promise<void>
```

| Param         | Type                               |
| ------------- | ---------------------------------- |
| **`options`** | <code>{ playerId: string; }</code> |

--------------------


### pause(...)

```typescript
pause(options: { playerId: string; }) => Promise<void>
```

| Param         | Type                               |
| ------------- | ---------------------------------- |
| **`options`** | <code>{ playerId: string; }</code> |

--------------------


### setVolume(...)

```typescript
setVolume(options: { playerId: string; volume: number; }) => Promise<void>
```

| Param         | Type                                               |
| ------------- | -------------------------------------------------- |
| **`options`** | <code>{ playerId: string; volume: number; }</code> |

--------------------


### hide(...)

```typescript
hide(options: { playerId: string; }) => Promise<void>
```

| Param         | Type                               |
| ------------- | ---------------------------------- |
| **`options`** | <code>{ playerId: string; }</code> |

--------------------


### show(...)

```typescript
show(options: { playerId: string; }) => Promise<void>
```

| Param         | Type                               |
| ------------- | ---------------------------------- |
| **`options`** | <code>{ playerId: string; }</code> |

--------------------


### releasePlayer(...)

```typescript
releasePlayer(options: { playerId: string; }) => Promise<void>
```

| Param         | Type                               |
| ------------- | ---------------------------------- |
| **`options`** | <code>{ playerId: string; }</code> |

--------------------


### setVideoBounds(...)

```typescript
setVideoBounds(options: { x: number; y: number; width: number; height: number; resizeMode?: 'fill' | 'fit'; }) => Promise<void>
```

Position/size the shared video SurfaceView within the WebView viewport.
Coordinates are normalized fractions (0..1) of the WebView size.
resizeMode: 'fill' stretches to the rect; 'fit' letterboxes within the rect.

| Param         | Type                                                                                                |
| ------------- | --------------------------------------------------------------------------------------------------- |
| **`options`** | <code>{ x: number; y: number; width: number; height: number; resizeMode?: 'fill' \| 'fit'; }</code> |

--------------------


### setVideoSurfaceVisibility(...)

```typescript
setVideoSurfaceVisibility(options: { visible: boolean; }) => Promise<void>
```

| Param         | Type                               |
| ------------- | ---------------------------------- |
| **`options`** | <code>{ visible: boolean; }</code> |

--------------------


### addListener('audioPlaybackEnded', ...)

```typescript
addListener(eventName: 'audioPlaybackEnded', listenerFunc: (data: { playerId: string; }) => void) => Promise<any>
```

| Param              | Type                                                  |
| ------------------ | ----------------------------------------------------- |
| **`eventName`**    | <code>'audioPlaybackEnded'</code>                     |
| **`listenerFunc`** | <code>(data: { playerId: string; }) =&gt; void</code> |

**Returns:** <code>Promise&lt;any&gt;</code>

--------------------


### removeAllListeners()

```typescript
removeAllListeners() => Promise<void>
```

--------------------

</docgen-api>