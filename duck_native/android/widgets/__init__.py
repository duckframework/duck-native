"""
Native Android UI blocks.

Every UI block that maps directly to a real Android platform class (for example View) lives here, including blocks with no HTML equivalent.

Widgets are thin wrappers: they expose a Python API over the native class and contain no Duck theming or composition logic.
"""

from duck_native.android.widgets.base import NativeWidget
from duck_native.android.widgets.bottom_sheet import BottomSheet
from duck_native.android.widgets.date_picker import DatePicker
from duck_native.android.widgets.switch import NativeSwitch


__all__ = ["NativeWidget", "BottomSheet", "DatePicker", "NativeSwitch"]
