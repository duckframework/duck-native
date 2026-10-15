# 📱 Native Actions

Native actions let your web pages use phone features, like showing a toast,
vibrating or sharing text, with one line of JavaScript.

> 💡 Your app keeps working in a normal browser. Native actions only exist
> inside the Android app, so always check that they are available first.

## 🧠 How it works

```
 Your Duck server
        │  (normal web traffic)
        ▼
┌─────────────────────────────────────┐
│ Your Android app                    │
│                                     │
│  Your page ── DuckNative.call() ──┐ │
│                                   ▼ │
│                      Safety check   │
│                  (is this page      │
│                   yours?)           │
│                         │           │
│                         ▼           │
│                 Phone feature       │
│          (toast, vibrate, clipboard)│
└─────────────────────────────────────┘
```

Your page asks, the app checks the page is yours, then it uses the phone
feature and sends the answer back. Pages that are not yours cannot use any of
this.

## 🚀 Quick start

Add this to any page's JavaScript:

```js
if (window.DuckNative) {
  await DuckNative.call("toast", { message: "Hello from Duck!" });
}
```

That's it. No setup or extra configuration is needed. `window.DuckNative` is
missing in a normal browser, so the `if` keeps your page working there too.

If your code runs very early and `DuckNative` isn't ready, wait for the
`DuckNativeReady` event:

```js
window.addEventListener("DuckNativeReady", () => {
  DuckNative.call("toast", { message: "Ready!" });
});
```

## 🧰 Available actions

| Action | What it does | You send | You get back |
| --- | --- | --- | --- |
| `toast` | Shows a small message on screen | `{ message }` | nothing |
| `vibrate` | Vibrates the phone | `{ duration? }` in ms (1 to 5000) | nothing |
| `clipboard.write` | Copies text | `{ text }` | nothing |
| `clipboard.read` | Reads copied text | nothing | `{ text }` |
| `share` | Opens the phone's share sheet | `{ text, title? }` | nothing |
| `device.info` | Tells you about the phone | nothing | `{ platform, version, sdk, architecture }` |
| `bridge.info` | Tells you the app's bridge version | nothing | `{ version, platform }` |
| `bridge.capabilities` | Lists what this app can do | nothing | `{ version, capabilities[] }` |
| `debug.delay` | Waits a bit (for testing) | `{ ms?, label? }` | `{ waited, label }` |

`?` means optional. `debug.delay` only works in debug builds.

### Examples

```js
// Vibrate for half a second
await DuckNative.call("vibrate", { duration: 500 });

// Copy text
await DuckNative.call("clipboard.write", { text: "Copied!" });

// Read it back
const { text } = await DuckNative.call("clipboard.read");

// Share a link
await DuckNative.call("share", { text: "https://duckframework.com", title: "Duck" });

// Ask what this app supports
const { capabilities } = await DuckNative.call("bridge.capabilities");
```

## 🔔 Events

The app can also tell your page when something happens:

| Event | You get | When |
| --- | --- | --- |
| `resume` | `{}` | The app comes back to the screen |
| `pause` | `{}` | The app goes to the background |
| `networkChanged` | `{ online }` | The internet goes on or off |

```js
DuckNative.on("networkChanged", ({ online }) => {
  console.log(online ? "Back online" : "Offline");
});
```

> 🔌 **Tip:** You don't need to reconnect anything. Android can cut the
> connection while the app is in the background, and Duck reconnects your
> WebSocket by itself. Use these events only for your own logic, such as
> refreshing data on `resume` or showing an "Offline" banner.

## ⚠️ Handling errors

Every call returns a Promise. If something goes wrong, it fails with an error
that has a `code`:

```js
try {
  await DuckNative.call("vibrate", { duration: 99999 });
} catch (error) {
  console.log(error.code); // INVALID_PAYLOAD
}
```

| Code | What it means |
| --- | --- |
| `INVALID_PAYLOAD` | What you sent is wrong, for example a missing field or a number out of range |
| `UNKNOWN_ACTION` | That action doesn't exist (check the spelling) |
| `PERMISSION_DENIED` | The user said no to a permission |
| `NOT_SUPPORTED` | This phone can't do that |
| `OPERATION_CANCELLED` | The user cancelled, for example closed the share sheet |
| `TIMEOUT` | It took longer than `timeoutMs` |
| `NATIVE_ERROR` | Something unexpected went wrong on the phone |

To stop waiting after a while:

```js
await DuckNative.call("clipboard.read", {}, { timeoutMs: 3000 });
```

## 🔒 Is it safe?

Yes. Only pages from your own Duck server can use native actions, and only the
actions listed above can run. Pages from other websites get nothing.

One rule to follow: **don't embed untrusted iframes** in pages that use
`DuckNative`.

## 🧩 Write your own action

Need a phone feature that isn't listed? Write it in Kotlin. You don't register
anything by hand.

1. Run `duck native init`. It creates `native/actions/ExampleAction.kt`.
2. Copy that file, rename the class, and put your code in `execute`.
3. Give it a name with `@DuckAction` on its own line:

```kotlin
@DuckAction("flashlight.toggle")
class FlashlightAction : NativeAction {
    override fun execute(host: NativeHost, payload: JSONObject, callback: NativeCallback) {
        // ... your code ...
        callback.success()
    }
}
```

4. Run `duck native build`.
5. Call it like any other action:

```js
await DuckNative.call("flashlight.toggle");
```

Good to know:
- Names are lowercase with dots, like `camera.capture`, and must not match an existing action.
- Each file needs a `package` line.
- If your action needs a permission, add it to `PERMISSIONS` in `native/config.py`.
- Adding or changing an action needs a new `duck native build`. The ready-made `duck native apk` doesn't include your actions.

### 🔐 Asking for a permission

Some features, like the camera, need the user's permission. Two steps:

**1. Declare it** in `native/config.py`:

```python
PERMISSIONS = [
    "android.permission.INTERNET",
    "android.permission.CAMERA",
]
```

**2. Ask for it** in your action with `requirePermission`. Your code runs only
if the user says yes. If they say no, JavaScript gets a `PERMISSION_DENIED`
error automatically:

```kotlin
import android.Manifest
import com.duckframework.bridge.requirePermission

@DuckAction("camera.capture")
class CameraAction : NativeAction {
    override fun execute(host: NativeHost, payload: JSONObject, callback: NativeCallback) {
        host.requirePermission(Manifest.permission.CAMERA, callback) {
            // The user said yes, so use the camera here
            callback.success()
        }
    }
}
```

Handle a "no" in JavaScript like any other error:

```js
try {
  await DuckNative.call("camera.capture");
} catch (error) {
  if (error.code === "PERMISSION_DENIED") alert("Camera access is needed.");
}
```

> 💡 Forgetting step 1 is the most common mistake. Android silently refuses
> permissions that aren't declared.

## 🔭 Coming later

- Calling actions from Python, like `native.toast("Hello")`
- Camera, location, notifications and file picker
- iOS and desktop apps
