# Duck Bridge

Duck Bridge is a thin Android shell around a Duck/Lively web application. It
hosts a WebView, loads a Duck server (local or remote), and exposes a small,
explicit native API to JavaScript as `window.DuckNative`.

The Duck server owns the application. The WebView renders it. Duck Bridge only
answers one question: what can this device do?

## Architecture

```
            Duck server (local or remote)
                       |  HTTP / WebSocket
                       v
+---------------------------------------------+
| Duck Bridge APK                             |
|                                             |
|  WebView --- Duck/Lively HTML + JS          |
|     |                                       |
|     |  DuckNative.call(action, payload)     |
|     v                                       |
|  AndroidDuckBridge.request(json)            |
|     |                                       |
|  DuckNativeBridge -> TrustPolicy            |
|     |                                       |
|  NativeActionRegistry -> NativeAction       |
|     |                                       |
|  Android APIs (toast, vibrate, clipboard)   |
+---------------------------------------------+
```

The two paths are independent: WebSockets go to the Duck server, native calls
stay on the device.

## Protocol

Request, sent by `DuckNative.call()`:

```json
{ "id": "42", "action": "toast", "payload": { "message": "Hello" } }
```

Success and failure responses:

```json
{ "id": "42", "ok": true, "result": null }
{ "id": "42", "ok": false,
  "error": { "code": "UNKNOWN_ACTION", "message": "Unknown native action" } }
```

Every request has its own id and its own callback. Nothing is shared between
requests, so many can be in flight and complete in any order.

Error codes: `INVALID_REQUEST`, `INVALID_PAYLOAD`, `UNKNOWN_ACTION`,
`PERMISSION_DENIED`, `NOT_SUPPORTED`, `OPERATION_CANCELLED`, `NATIVE_ERROR`,
`TIMEOUT` (raised by the JS client when `timeoutMs` is exceeded).

## JavaScript API

```js
if (window.DuckNative) {
  await DuckNative.call("toast", { message: "Hello from Duck" });
  const { text } = await DuckNative.call("clipboard.read");
  const caps = await DuckNative.call("bridge.capabilities");

  DuckNative.on("networkChanged", ({ online }) => console.log(online));
}
```

`call(action, payload, { timeoutMs })` returns a Promise. Errors have a `code`.
In a normal browser `window.DuckNative` does not exist, so feature-detect it.
The script is injected at page start; if you need it earlier, wait for the
`DuckNativeReady` window event.

## Actions

| Action                | Payload                          | Result                              |
|-----------------------|----------------------------------|-------------------------------------|
| `toast`               | `{ message }`                    | `null`                              |
| `vibrate`             | `{ duration? }` 1-5000 ms        | `null`                              |
| `clipboard.write`     | `{ text }`                       | `null`                              |
| `clipboard.read`      | none                             | `{ text }`                          |
| `share`               | `{ text, title? }`               | `null` (resolves when sheet opens)  |
| `device.info`         | none                             | `{ platform, version, sdk, architecture }` |
| `bridge.info`         | none                             | `{ version, platform }`             |
| `bridge.capabilities` | none                             | `{ version, capabilities[] }`       |
| `debug.delay`         | `{ ms?, label? }`                | `{ waited, label }` (debug builds only) |

## Events (native to JavaScript)

| Event            | Payload            | When                                   |
|------------------|--------------------|----------------------------------------|
| `resume`         | `{}`               | Activity resumed                       |
| `pause`          | `{}`               | Activity paused                        |
| `networkChanged` | `{ online: bool }` | Connectivity changed (changes only)    |

Events are delivered only to trusted pages.

## Configuration

`app/src/main/assets/duck-bridge.json`:

```json
{
  "server_url": "https://example.com",
  "trusted_origins": [],
  "external_links": "browser"
}
```

- `server_url`: the Duck server to load. `null` loads the built-in test page.
- `trusted_origins`: extra origins allowed to use the native API. The origin of
  `server_url` is always trusted.
- `external_links`: what to do with http(s) links outside the trusted origins:
  `browser` (system browser), `webview` (load without native API), or `block`.

Debug builds also accept a launch extra, so you can switch servers without
rebuilding (`-S` restarts the app so the extra is read):

```
am start -S -n com.duckframework.bridge/.MainActivity \
    --es server_url http://127.0.0.1:8000
```

Release builds ignore the extra, so another app cannot point Duck Bridge at an
arbitrary server.

Cleartext HTTP is allowed only for `127.0.0.1`, `localhost` and `10.0.2.2`
(emulator host). See `res/xml/network_security_config.xml`.

## Security model

- Only `AndroidDuckBridge.request(String)` is exposed. No Activity, no
  reflection, no generic executor. Only registered actions can run.
- Every request is checked against the trusted origins in Kotlin, using the
  WebView's current URL, before any action code runs. Untrusted pages get no
  `DuckNative` object and their raw calls are dropped without a reply.
- Responses and events are delivered only while the page is still trusted, so
  a result cannot leak to a page that navigated in afterwards.
- Origins are parsed with `java.net.URI`, which rejects credentials in the URL
  and malformed hosts. Cleartext trust is limited by the network security config.
- Payloads are validated per field; requests over 256 KB are dropped.
- Navigation: `tel:` and `mailto:` open the dialer and mail app; `intent:`,
  `file:`, `content:`, `javascript:` and other schemes are blocked.
- Errors sent to JavaScript never contain stack traces. Payloads are never logged.

Known limits:

- `addJavascriptInterface` exposes the object to every frame, and Kotlin can
  only see the top-level URL. If a trusted page embeds a third-party iframe,
  that iframe could call the bridge. Do not embed untrusted iframes in Duck
  pages that use `DuckNative`. The stricter fix is `androidx.webkit`
  `WebViewCompat.addWebMessageListener` with per-origin rules, which needs one
  small dependency.
- Navigation rules apply to the main frame only.

## Lifecycle and WebSockets

The Activity handles configuration changes itself, so rotation does not
recreate the WebView or drop the WebSocket. `onPause`/`onResume` pause and
resume the WebView and emit `pause`/`resume`. Android can freeze or kill the
network while the app is backgrounded, so Lively should reconnect its
WebSocket when it receives `resume` or `networkChanged` with `online: true`.

## Adding a native capability

1. Write a `NativeAction` in `actions/`. Validate the payload with `Payload`,
   and finish through the callback (now or later).
2. Register it in `NativeActionRegistry.createDefault()` under a namespaced
   name such as `camera.capture`.
3. If it needs a runtime permission, wrap the work in
   `host.requirePermission(Manifest.permission.CAMERA, callback) { ... }` and
   declare the permission in the manifest.

Nothing else changes: the protocol, JS wrapper and trust checks already apply.
Return large data as a URI, never as Base64 inside the JSON response.

## Testing

With no `server_url`, the app shows a test page with one button per check. Run
a debug build so `debug.delay` and the launch extra are available.

| Check                     | How                                                        |
|---------------------------|------------------------------------------------------------|
| Toast through RPC         | Tap **Toast**: a toast appears and the log shows PASS      |
| Unknown action            | **Unknown action** must PASS with `UNKNOWN_ACTION`         |
| Bad payload               | **Bad payload** must PASS with `INVALID_PAYLOAD`           |
| Concurrent requests       | **Concurrent** must log `finished b,c,a`                   |
| Asynchronous operation    | **Async 1.5s** resolves after about 1.5 seconds            |
| Events                    | Background and reopen the app; `EVENT resume` appears      |
| Local server              | `am start -S ... --es server_url http://127.0.0.1:8000`    |
| Remote server             | Set `server_url` to your `https://` Duck app and rebuild   |
| Untrusted origin          | Load a page from another origin with `external_links: webview`; `window.DuckNative` must be undefined |

Use `chrome://inspect` on a debug build to see the WebView console.

## Build

Normally you build through Duck Native, which copies this project into the
app's build folder, writes the settings from `native/config.py` and runs Gradle
on the copy:

```
duck-native build
```

To build this template on its own, you need JDK 17, the Android SDK (via
`ANDROID_HOME` or `local.properties`) and Gradle 8.9 or newer:

```
gradle assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

On Termux/ARM64, see `NOTES.md`.

## Not built yet

- The Python side (`native.toast("Hello")` in Duck/Lively).
- Camera, location, notifications, file picker.
- iOS and desktop runtimes.
