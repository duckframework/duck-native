"""
Options and helpers shared by every command.
"""
import click

from pathlib import Path

from duck.logging import console

from duck_native.config import ConfigError, ProjectConfig, load_config


project_argument = click.argument(
    "project",
    default=".",
    type=click.Path(exists=True, file_okay=False, path_type=Path),
)


def load_project(project: Path) -> ProjectConfig:
    """
    Load a project's config, reporting problems the way click expects.

    Args:
        project: Folder that contains the project's native/ directory.

    Returns:
        ProjectConfig: The validated settings.

    Raises:
        click.ClickException: If the config is missing or invalid.
    """
    try:
        config = load_config(project)
        
    except ConfigError as error:
        raise click.ClickException(str(error)) from error

    # Warnings never stop a build.
    for warning in config.warnings():
        console.log(warning, level=console.WARNING)
    
    # Returns the final config.
    return config
