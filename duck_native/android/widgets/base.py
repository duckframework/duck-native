"""
Base class shared by every native widget.
"""

import json

from typing import Any, Callable, Optional

from duck.html.components.container import Container


class NativeWidget(Container):
    """
    A placeholder element that Duck Bridge replaces with a real Android widget.

    The element carries the widget's name and props. The bridge mounts the
    native widget, keeps it in sync with `native_props`, and turns its events
    into Lively events named `native-<event>`.

    Outside Duck Bridge, for example in a desktop browser, the placeholder is
    just an empty element.

    Args:
        id: Stable unique id. Required, because the bridge tracks widgets by id.
        native_props: Initial props. Declare every prop you will change later.
        handlers: Maps native event names to Python handlers. None is skipped.
        **kwargs: Passed to the underlying container, e.g. `style` or `props`.

    Attributes:
        NAME: Widget name registered on the Kotlin side.
        INLINE: True when the widget is drawn inside the page, False for
            overlays such as dialogs and sheets.
        EVENT_PREFIX: Prefix of the Lively event names.
        handlers: Native event name mapped to its Python handler.
        native_props: Current props sent to the native widget.
    """

    NAME: str = ""
    INLINE: bool = False
    EVENT_PREFIX = "native-"

    def __init__(
        self,
        id: str,
        native_props: dict[str, Any],
        handlers: Optional[dict[str, Optional[Callable]]] = None,
        **kwargs: Any,
    ) -> None:
        # These must exist before the container builds itself
        self.handlers = {name: fn for name, fn in (handlers or {}).items() if fn is not None}
        self.native_props = dict(native_props)

        # Overlay placeholders take no space
        if not self.INLINE:
            kwargs.setdefault("style", {"display": "none"})

        # Describe the widget to the page script
        props = dict(kwargs.pop("props", None) or {})
        props.update(
            {
                "data-native-widget": self.NAME,
                "data-native-inline": str(self.INLINE).lower(),
                "data-native-props": json.dumps(self.native_props),
            }
        )
        super().__init__(id=id, props=props, **kwargs)

    def on_create(self) -> None:
        """
        Bind the native event handlers.
        """
        super().on_create()

        # Bind every native event
        for name, handler in self.handlers.items():
            self.bind(f"{self.EVENT_PREFIX}{name}", handler)

    async def async_update_native(self, ws: Any, **changes: Any) -> None:
        """
        Change native props and send them to the widget right away.

        Call this from an event handler. The widget must be in that event's
        `update_targets` (or be the component the event is bound to), because
        `ws.sync_now` only accepts those.

        Args:
            ws: The websocket passed to the event handler.
            **changes: Props to set, e.g. `checked=True`.
        """
        self.native_props.update(changes)
        self.props["data-native-props"] = json.dumps(self.native_props)
        await ws.sync_now(self)

    async def async_trigger(self, ws: Any, counter: str) -> None:
        """
        Raise a counter prop by one, which tells the widget to act once.

        Counters avoid open/closed state drifting between Python and the phone.

        Args:
            ws: The websocket passed to the event handler.
            counter: Name of the counter prop, e.g. `show`.
        """
        await self.async_update_native(ws, **{counter: self.native_props.get(counter, 0) + 1})
