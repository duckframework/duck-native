"""
The init command.
"""

import shutil

from pathlib import Path

import click

from duck.logging import console

from duck_native.cli.common import project_argument
from duck_native.config import Names


TEMPLATE_CONFIG = Path(__file__).resolve().parent.parent / "etc" / "configs" / "user_config.py"


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

    console.log(f"Created {target}", level=console.SUCCESS)
    console.log("Next: edit native/config.py, then run duck-native build")
