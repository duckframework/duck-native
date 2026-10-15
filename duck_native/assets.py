"""
Generates Android resources and the manifest overlay from a project config.
"""

import shutil

from pathlib import Path
from xml.sax.saxutils import escape

from duck.logging import console
from PIL import Image, ImageDraw

from duck_native.config import ProjectConfig


class Sizes:
    """
    Pixel sizes used when generating images.

    Attributes:
        LAUNCHER: Launcher icon size for each screen density.
        SPLASH_MAX: Longest side of the splash image.
        MASK_SCALE: Oversampling used to keep the round icon edge smooth.
    """

    LAUNCHER = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
    SPLASH_MAX = 512
    MASK_SCALE = 4


def android_string(value: str) -> str:
    """
    Escape text for an Android string resource.

    Args:
        value: Raw text.

    Returns:
        str: Text that is safe between <string> tags.
    """
    # XML first, then the characters Android treats specially.
    text = escape(value).replace("'", "\\'").replace('"', '\\"').replace("\n", "\\n")
    return "\\" + text if text.startswith(("@", "?")) else text


def write_text(path: Path, text: str) -> None:
    """
    Write a text file, creating its folders.

    Args:
        path: File to write.
        text: Contents.
    """
    path.parent.mkdir(parents=True, exist_ok=True)
    console.log(f"Writing {path}", level=console.DEBUG)
    path.write_text(text, encoding="utf-8")


def square_icon(source: Image.Image, size: int) -> Image.Image:
    """
    Fit an image inside a transparent square.

    Args:
        source: The original icon.
        size: Side length in pixels.

    Returns:
        Image.Image: The resized RGBA icon.
    """
    fitted = source.copy()
    fitted.thumbnail((size, size), Image.Resampling.LANCZOS)

    # Centre it so non-square artwork is not stretched.
    canvas = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    canvas.paste(fitted, ((size - fitted.width) // 2, (size - fitted.height) // 2))
    return canvas


def round_icon(square: Image.Image) -> Image.Image:
    """
    Crop an icon to a circle.

    Args:
        square: A square RGBA icon.

    Returns:
        Image.Image: The icon with transparent corners.
    """
    # Draw the mask large, then shrink it for a smooth edge.
    big = square.width * Sizes.MASK_SCALE
    mask = Image.new("L", (big, big), 0)
    
    # Draw image
    ImageDraw.Draw(mask).ellipse((0, 0, big, big), fill=255)
    
    # Resize mask
    mask = mask.resize(square.size, Image.Resampling.LANCZOS)
    
    # Copy square
    rounded = square.copy()
    
    # Add mask
    rounded.putalpha(mask)
    
    # Return the image
    return rounded


def write_icons(icon: Path, res_dir: Path) -> None:
    """
    Write launcher icons for every screen density.

    Args:
        icon: Source image.
        res_dir: Generated res folder.
    """
    source = Image.open(icon).convert("RGBA")
    
    for density, size in Sizes.LAUNCHER.items():
        folder = res_dir / f"mipmap-{density}"
        folder.mkdir(parents=True, exist_ok=True)
        
        # Log something
        console.log(f"Writing launcher icons to {folder}", level=console.DEBUG)
        
        # Create square
        square = square_icon(source, size)
        
        # Save icon
        square.save(folder / "ic_launcher.png")
        
        # Save rounded icon
        round_icon(square).save(folder / "ic_launcher_round.png")


def write_splash(config: ProjectConfig, res_dir: Path) -> None:
    """
    Write the launch background: a colour with an optional centred image.

    Args:
        config: The validated project config.
        res_dir: Generated res folder.
    """
    # The colour is always defined, because the theme refers to it.
    write_text(
        res_dir / "values" / "duck_generated.xml",
        "<resources>\n"
        f'    <string name="app_name">{android_string(config.app_name)}</string>\n'
        f'    <color name="duck_splash_color">{config.splash_background}</color>\n'
        "</resources>\n",
    )

    # Add the image layer only when SPLASH_IMAGE is set.
    image_layer = ""
    
    if config.splash_image is not None:
        image = Image.open(config.splash_image).convert("RGBA")
        
        # Create thumbnail
        image.thumbnail((Sizes.SPLASH_MAX, Sizes.SPLASH_MAX), Image.Resampling.LANCZOS)
        
        # Generate target
        target = res_dir / "drawable-nodpi" / "duck_splash_image.png"
        
        # Make target dir
        target.parent.mkdir(parents=True, exist_ok=True)
        
        # Log something
        console.log(f"Writing {target}", level=console.DEBUG)
        
        # Save image
        image.save(target)
        
        # Create image layer
        image_layer = (
            '    <item>\n        <bitmap android:src="@drawable/duck_splash_image"'
            ' android:gravity="center" />\n    </item>\n'
        )

    write_text(
        res_dir / "drawable" / "duck_splash.xml",
        '<layer-list xmlns:android="http://schemas.android.com/apk/res/android">\n'
        '    <item android:drawable="@color/duck_splash_color" />\n'
        f"{image_layer}</layer-list>\n",
    )


def write_manifest(config: ProjectConfig, output_dir: Path) -> None:
    """
    Write the manifest overlay: permissions, orientation and launcher icon.

    Args:
        config: The validated project config.
        output_dir: Generated folder.
    """
    lines = "".join(
        f'    <uses-permission android:name="{escape(name)}" />\n'
        for name in config.permissions
    )

    # Application attributes only when they are set.
    application = ""
    
    if config.icon is not None:
        application += (
            ' android:icon="@mipmap/ic_launcher"'
            ' android:roundIcon="@mipmap/ic_launcher_round"'
        )

    activity = ""
    
    if config.orientation != "unspecified":
        activity = (
            '        <activity android:name="com.duckframework.bridge.MainActivity"'
            f' android:screenOrientation="{config.orientation}" />\n'
        )

    if application or activity:
        lines += f"    <application{application}>\n{activity}    </application>\n"

    write_text(
        output_dir / "AndroidManifest.xml",
        '<manifest xmlns:android="http://schemas.android.com/apk/res/android">\n'
        f"{lines}</manifest>\n",
    )


def generate(config: ProjectConfig, output_dir: Path) -> None:
    """
    Generate everything Gradle needs from the config, replacing old output.

    Args:
        config: The validated project config.
        output_dir: Folder for the generated manifest and res directory.
    """
    # Start clean so a removed ICON does not leave a stale icon behind.
    console.log("Cleaning old generated files", level=console.DEBUG)
    
    # Clean output directory
    shutil.rmtree(output_dir, ignore_errors=True)
    
    # Create resources dir
    res_dir = output_dir / "res"

    # Write each generated piece.
    write_manifest(config, output_dir)
    write_splash(config, res_dir)
    
    if config.icon is not None:
        write_icons(config.icon, res_dir)
