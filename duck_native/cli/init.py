"""
The init command.
"""
import click
import shutil

from pathlib import Path

from duck.logging import console

from duck_native.actions import ACTIONS_DIR
from duck_native.cli.common import project_argument
from duck_native.config import Names
from duck_native.storage import NATIVE_STORAGE


TEMPLATE_CONFIG = NATIVE_STORAGE / "etc" / "configs" / "user_config.py"

EXAMPLE_ACTION = '''package com.example.myapp

import com.duckframework.bridge.DuckAction
import com.duckframework.bridge.NativeAction
import com.duckframework.bridge.NativeCallback
import com.duckframework.bridge.NativeHost
import org.json.JSONObject

// To use this action, remove the two slashes in front of @DuckAction.
// It must stay on its own line. Then call it from JavaScript:
//     await DuckNative.call("example.hello")

// @DuckAction("example.hello")
class ExampleAction : NativeAction {

    override fun execute(
        host: NativeHost,
        payload: JSONObject,
        callback: NativeCallback,
    ) {
        callback.success(JSONObject().put("message", "Hello from Kotlin"))
    }
}
'''


@click.command(help="Create the native/ folder with a starter config.py in a project.")
@project_argument
@click.option("--force", is_flag=True, help="Overwrite an existing native/config.py.")
def init(project: Path, force: bool) -> None:
    """
    Create native/config.py in a project.

    Args:
        project: Folder to create the native/ directory in.
        force: Overwrite an existing config.py.

    Raises:
        click.ClickException: If config.py exists and force is not set.
    """
    target = project / Names.CONFIG_PATH

    if target.exists() and not force:
        raise click.ClickException(f"{target} already exists. Use --force to overwrite it.")

    # Create the folder and write the starter config.
    console.log(f"Creating {target.parent}", level=console.DEBUG)
    target.parent.mkdir(parents=True, exist_ok=True)

    console.log(f"Writing {target}", level=console.DEBUG)
    shutil.copyfile(TEMPLATE_CONFIG, target)

    # Add an example custom action, inactive until its annotation is uncommented.
    example = project / ACTIONS_DIR / "ExampleAction.kt"
    
    if not example.exists():
        console.log(f"Writing {example}", level=console.DEBUG)
        example.parent.mkdir(parents=True, exist_ok=True)
        example.write_text(EXAMPLE_ACTION, encoding="utf-8")

    console.log(f"Created {target}", level=console.SUCCESS)
    console.log("Next: edit native/config.py, then run duck native build")
