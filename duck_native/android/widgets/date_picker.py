"""
A native Android date picker dialog.
"""

from datetime import date
from typing import Any, Callable, Optional, Union

from duck_native.android.widgets.base import NativeWidget


DateLike = Union[str, date, None]


def to_iso(value: DateLike) -> str:
    """
    Convert a date to the YYYY-MM-DD text the native widget reads.

    Args:
        value: A date, a datetime, an ISO string, or None.

    Returns:
        str: The date as YYYY-MM-DD, or an empty string for None.
    """
    if value is None:
        return ""
    if isinstance(value, str):
        return value
    return value.strftime("%Y-%m-%d")


class DatePicker(NativeWidget):
    """
    Opens the Android date picker dialog on demand.

    Args:
        id: Stable unique id.
        value: Date the dialog starts on, today when omitted.
        min_date: Earliest date that can be picked.
        max_date: Latest date that can be picked.
        title: Optional dialog title.
        on_pick: Handler called with the picked date as `YYYY-MM-DD`.
        on_dismiss: Handler called when the dialog closes without a pick.
        **kwargs: Passed to the underlying container.

    Example:
        picker = DatePicker(id="dob", max_date=date.today(), on_pick=self.handle_date)
        await picker.async_show(ws)
    """

    NAME = "date_picker"

    def __init__(
        self,
        id: str,
        value: DateLike = None,
        min_date: DateLike = None,
        max_date: DateLike = None,
        title: str = "",
        on_pick: Optional[Callable] = None,
        on_dismiss: Optional[Callable] = None,
        **kwargs: Any,
    ) -> None:
        super().__init__(
            id=id,
            native_props={
                "value": to_iso(value),
                "min": to_iso(min_date),
                "max": to_iso(max_date),
                "title": title,
                "show": 0,
            },
            handlers={"pick": on_pick, "dismiss": on_dismiss},
            **kwargs,
        )

    async def async_show(self, ws: Any, value: DateLike = None) -> None:
        """
        Open the dialog.

        Args:
            ws: The websocket passed to the event handler.
            value: Date to start on, replacing the one given at creation.
        """
        if value is not None:
            self.native_props["value"] = to_iso(value)
        await self.async_trigger(ws, "show")
