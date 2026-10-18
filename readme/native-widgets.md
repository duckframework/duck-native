# Native widgets

Use real Android widgets from Python, as if they were components. You write
Python only; Duck Bridge draws the native widget and sends its events back to
your handlers.

```python
from duck_native.android.widgets import BottomSheet, DatePicker, NativeSwitch

NativeSwitch(id="notify", checked=True, on_change=self.handle_toggle)

picker = DatePicker(id="dob", max_date=date.today(), on_pick=self.handle_date)
await picker.async_show(ws)
```

Widgets only work inside the Duck Bridge app. In a normal browser the widget
is an empty placeholder, so check `window.DuckNative` if the page also runs on
the web.

## Built-in widgets

Every widget needs a stable, unique `id`. Handlers use Lively's usual
signature: `async def handle_x(self, widget, event, value, ws)`.

| Widget | Kind | Handlers (`value`) | Methods |
|---|---|---|---|
| `NativeSwitch(id, checked, enabled, on_change)` | Inline | `on_change`: `True` / `False` | `async_set_checked(ws, bool)` |
| `DatePicker(id, value, min_date, max_date, title, on_pick, on_dismiss)` | Overlay | `on_pick`: `"YYYY-MM-DD"`; `on_dismiss`: `None` | `async_show(ws, value=None)` |
| `BottomSheet(id, title, message, actions, dismissible, on_action, on_dismiss)` | Overlay | `on_action`: `{"id": ...}`; `on_dismiss`: `None` | `async_show(ws, **changes)`, `async_hide(ws)` |

`BottomSheet` actions can be labels, `(id, label)` pairs or `{"id", "label"}`
dicts. The first is highlighted, at most six are shown, and with none a Close
button appears.

Change any prop later with `await widget.async_update_native(ws, enabled=False)`.

Widget methods take the `ws` your event handler receives and sync with
`ws.sync_now`. That call only accepts components in the event's
`update_targets`, so list the widget on whatever triggers it:

```python
open_btn.bind("click", self.handle_open, update_targets=[picker])

async def handle_open(self, btn, event, value, ws):
    await picker.async_show(ws)
```

## Inline and overlay widgets

- **Overlay** widgets (dialogs, sheets, pickers) are not part of the page
  layout. Their placeholder is hidden, and they open when you call
  `async_show()`.
- **Inline** widgets (the switch) are drawn over their placeholder element.
  Size the element like the widget; the bridge follows it while the page
  scrolls or resizes and hides the widget when the element is hidden or
  off-screen.

Native views always sit above the HTML. An HTML modal or dropdown cannot cover
an inline widget, and during fast scrolling it can trail the page by a frame.
Use inline widgets for self-contained controls, not for ordinary text and
layout.

## Write your own

Native widgets need a build (`duck native build`); there is no hot-loading.

1. Write the Kotlin class in `native/widgets/`:

```kotlin
package com.example.myapp

import com.duckframework.bridge.DuckWidget
import com.duckframework.bridge.NativeWidget
import com.duckframework.bridge.WidgetContext
import org.json.JSONObject

@DuckWidget("rating")
class RatingWidget : NativeWidget {

    private lateinit var context: WidgetContext

    override fun mount(context: WidgetContext, props: JSONObject) {
        this.context = context
        // Create views and add inline ones to context.overlay
    }

    override fun update(props: JSONObject) {
        // props holds every prop, so apply only what changed
    }

    override fun unmount() {
        // Remove views and close anything you opened
    }
}
```

   The annotation must be on its own line, directly above a `class` with a
   no-argument constructor, and the file needs a `package` line. Inline
   widgets also override `layout(x, y, width, height, visible)`.

2. Send events to Python with `context.emit("changed", value)`. Python
   receives them as `native-changed`.

3. Write the Python side:

```python
from duck_native.android.widgets import NativeWidget


class Rating(NativeWidget):
    """
    A star rating.

    Args:
        id: Stable unique id.
        value: Initial stars.
        on_changed: Handler called with the new number of stars.
    """

    NAME = "rating"
    INLINE = True

    def __init__(self, id, value=0, on_changed=None, **kwargs):
        super().__init__(
            id=id,
            native_props={"value": value},
            handlers={"changed": on_changed},
            **kwargs,
        )
```

Names must be lowercase and cannot reuse a built-in widget name. Declare every
prop in `native_props` at creation, because Lively only tracks what was
rendered initially.

## Triggering overlays

Overlays use counters (`show`, `hide`) instead of an open flag, so Python and
the phone can never disagree about whether a dialog is open. `async_trigger(ws, "show")`
raises the counter, and the widget acts each time it goes up. Mounting a widget
never opens it.

## How it works

1. The Python widget renders an element with `data-native-widget`,
   `data-native-props` and `data-native-inline`.
2. `duck-widgets.js` (injected after `duck-native.js`) watches for those
   elements and calls `widget.mount`, `widget.update`, `widget.layout` and
   `widget.unmount`. Only registered widgets can be mounted.
3. Native events travel back as `widget` events, become DOM events named
   `native-<event>` on the element, and Lively forwards them to your handler.

The script never edits the placeholder, so it does not trigger Lively's
untracked-mutation warning.

Builds ship `duck-native.js` and `duck-widgets.js` as written when
`SETTINGS["LIVELY_DEBUG"]` is on, and their `.min.js` versions otherwise.
