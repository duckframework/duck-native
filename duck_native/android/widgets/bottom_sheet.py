"""
A native Android bottom sheet.
"""

from typing import Any, Callable, Iterable, Optional, Union

from duck_native.android.widgets.base import NativeWidget


Action = Union[str, tuple[str, str], dict[str, str]]


def normalize_actions(actions: Iterable[Action]) -> list[dict[str, str]]:
    """
    Turn the accepted action shapes into `{id, label}` dicts.

    Args:
        actions: Labels, `(id, label)` pairs, or `{"id", "label"}` dicts.

    Returns:
        list[dict[str, str]]: One dict per action, in order.
    """
    result = []

    for action in actions:
        if isinstance(action, str):
            result.append({"id": action, "label": action})
        elif isinstance(action, dict):
            result.append({"id": action["id"], "label": action["label"]})
        else:
            result.append({"id": action[0], "label": action[1]})

    return result


class BottomSheet(NativeWidget):
    """
    A bottom sheet with a title, a message and up to six action buttons.

    The first action is highlighted. With no actions, a Close button is shown.

    Args:
        id: Stable unique id.
        title: Headline.
        message: Supporting text.
        actions: Labels, `(id, label)` pairs, or `{"id", "label"}` dicts.
        dismissible: False to stop users closing it by tapping outside or going back.
        on_action: Handler called with `{"id": ...}` of the chosen action.
        on_dismiss: Handler called when the sheet closes without a choice.
        **kwargs: Passed to the underlying container.

    Example:
        sheet = BottomSheet(
            id="confirm",
            title="Delete photo?",
            actions=[("delete", "Delete"), ("cancel", "Cancel")],
            on_action=self.handle_choice,
        )
        await sheet.async_show(ws)
    """

    NAME = "bottom_sheet"

    def __init__(
        self,
        id: str,
        title: str = "",
        message: str = "",
        actions: Iterable[Action] = (),
        dismissible: bool = True,
        on_action: Optional[Callable] = None,
        on_dismiss: Optional[Callable] = None,
        **kwargs: Any,
    ) -> None:
        super().__init__(
            id=id,
            native_props={
                "title": title,
                "message": message,
                "actions": normalize_actions(actions),
                "dismissible": dismissible,
                "show": 0,
                "hide": 0,
            },
            handlers={"action": on_action, "dismiss": on_dismiss},
            **kwargs,
        )

    async def async_show(self, ws: Any, **changes: Any) -> None:
        """
        Open the sheet.

        Args:
            ws: The websocket passed to the event handler.
            **changes: Props to change first, e.g. `title="Are you sure?"`.
        """
        if "actions" in changes:
            changes["actions"] = normalize_actions(changes["actions"])
        self.native_props.update(changes)
        await self.async_trigger(ws, "show")

    async def async_hide(self, ws: Any) -> None:
        """
        Close the sheet without raising a dismiss event.

        Args:
            ws: The websocket passed to the event handler.
        """
        await self.async_trigger(ws, "hide")
