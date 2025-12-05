
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

* [`play(...)`](#play)
* [`stop()`](#stop)
* [`pause()`](#pause)
* [`setVolume(...)`](#setvolume)

</docgen-index>

<docgen-api>
<!--Update the source file JSDoc comments and rerun docgen to update the docs below-->

### play(...)

```typescript
play(options: { url: string; }) => Promise<{ status: string; }>
```

| Param         | Type                          |
| ------------- | ----------------------------- |
| **`options`** | <code>{ url: string; }</code> |

**Returns:** <code>Promise&lt;{ status: string; }&gt;</code>

--------------------


### stop()

```typescript
stop() => Promise<void>
```

--------------------


### pause()

```typescript
pause() => Promise<void>
```

--------------------


### setVolume(...)

```typescript
setVolume(options: { volume: number; }) => Promise<void>
```

| Param         | Type                             |
| ------------- | -------------------------------- |
| **`options`** | <code>{ volume: number; }</code> |

--------------------

</docgen-api>