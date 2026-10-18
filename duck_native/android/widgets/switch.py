"""
A native Android switch drawn inside the page.
"""

from typing import Any, Callable, Optional

from duck_native.android.widgets.base import NativeWidget


class NativeSwitch(NativeWidget):
    """
    A real Android Switch placed over this element.

    Size the element to the switch, about 52 by 32 pixels, which is the default.

    Args:
        id: Stable unique id.
        checked: Initial state.
        enabled: False greys the switch out.
        on_change: Handler called with the new state, `True` or `False`.
        **kwargs: Passed to the underlying container.

    Example:
        NativeSwitch(id="notify", checked=True, on_change=self.handle_toggle)
    """

    NAME = "switch"
    INLINE = True
    DEFAULT_WIDTH = "52px"
    DEFAULT_HEIGHT = "32px"

    def __init__(
        self,
        id: str,
        checked: bool = False,
        enabled: bool = True,
        on_change: Optional[Callable] = None,
        **kwargs: Any,
    ) -> None:
        kwargs.setdefault(
            "style",
            {"width": self.DEFAULT_WIDTH, "height": self.DEFAULT_HEIGHT, "display": "inline-block"},
        )
        super().__init__(
            id=id,
            native_props={"checked": checked, "enabled": enabled},
            handlers={"change": on_change},
            **kwargs,
        )

    async def async_set_checked(self, ws: Any, checked: bool) -> None:
        """
        Turn the switch on or off.

        Args:
            ws: The websocket passed to the event handler.
            checked: The new state.
        """
        await self.async_update_native(ws, checked=checked)
