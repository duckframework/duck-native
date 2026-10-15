"""
Copies the Android project, generates resources and runs Gradle for a validated Duck Native project.
"""

import os
import shutil
import subprocess
import sys

from pathlib import Path
from typing import Optional

from duck.logging import console

from duck_native.actions import ActionError, install_actions
from duck_native.hints import suggest
from duck_native.config import ProjectConfig, escape_property
from duck_native.storage import NATIVE_STORAGE


TEMPLATE_DIR = NATIVE_STORAGE / "android" / "kotlin"


class BuildError(Exception):
    """
    Raised when a build cannot start.
    """


class Tasks:
    """
    Gradle tasks for each kind of build.

    Attributes:
        DEBUG: Installable debug APK.
        RELEASE_BUNDLE: Signed bundle for Google Play.
        RELEASE_APK: Signed APK for direct distribution.
    """

    DEBUG = "assembleDebug"
    RELEASE_BUNDLE = "bundleRelease"
    RELEASE_APK = "assembleRelease"


def pick_task(config: ProjectConfig, apk: bool) -> str:
    """
    Choose the Gradle task for a config.

    Args:
        config: The validated project config.
        apk: True to build a release APK instead of a bundle.

    Returns:
        str: The Gradle task name.
    """
    if config.debug:
        return Tasks.DEBUG

    # Return the release task
    return Tasks.RELEASE_APK if apk else Tasks.RELEASE_BUNDLE


def find_aapt2() -> Optional[str]:
    """
    Find a native aapt2 to use instead of the x86_64 one bundled with Gradle.

    Set DUCK_AAPT2 to choose one. On Termux, the installed aapt2 is used.

    Returns:
        Optional[str]: Path to aapt2, or None to keep Gradle's own.
    """
    if os.environ.get("DUCK_AAPT2"):
        return os.environ["DUCK_AAPT2"]

    if "com.termux" in os.environ.get("PREFIX", ""):
        return shutil.which("aapt2")
        
    return None


def prepare_project(config: ProjectConfig, generated_dir: Path) -> Path:
    """
    Copy the Kotlin project into the build folder and write its Gradle properties.

    Gradle builds from this copy, never from the installed package.

    Args:
        config: The validated project config.
        generated_dir: Folder holding the generated manifest and resources.

    Returns:
        Path: The copied project folder.
    """
    target = config.build_dir / "android"

    # Drop old sources so files removed from the template do not linger.
    console.log(f"Copying Android project to {target}", level=console.DEBUG)
    
    # Remove old data
    shutil.rmtree(target / "app" / "src", ignore_errors=True)
    
    # Copy template dir
    shutil.copytree(
        TEMPLATE_DIR,
        target,
        dirs_exist_ok=True,
        ignore=shutil.ignore_patterns(".gradle", "build", "local.properties", "gradle.properties"),
    )

    # Add the project's own Kotlin actions.
    try:
        install_actions(config, target)
    except ActionError as error:
        raise BuildError(str(error)) from error

    # Template properties first, then this project's values.
    console.log("Writing Gradle properties", level=console.DEBUG)
    
    # Get props
    properties = config.gradle_values()
    
    # Edit generatedDir
    properties["duck.generatedDir"] = generated_dir.as_posix()
    
    # Find aapt2
    aapt2 = find_aapt2()
    
    if aapt2:
        properties["android.aapt2FromMavenOverride"] = Path(aapt2).as_posix()
        console.log(f"Using aapt2 at {aapt2}", level=console.DEBUG)

    base = (TEMPLATE_DIR / "gradle.properties").read_text(encoding="utf-8").rstrip()
    lines = "\n".join(f"{key}={escape_property(value)}" for key, value in properties.items())
    (target / "gradle.properties").write_text(f"{base}\n\n{lines}\n", encoding="utf-8")
    
    # Return final target
    return target


def gradle_command(project_dir: Path, task: str) -> list[str]:
    """
    Build the Gradle command line, preferring the project's wrapper.

    Args:
        project_dir: The copied Android project.
        task: The Gradle task to run.

    Returns:
        list[str]: Arguments ready for subprocess.

    Raises:
        BuildError: If neither a wrapper nor gradle is available.
    """
    wrapper = project_dir / ("gradlew.bat" if os.name == "nt" else "gradlew")
    
    if wrapper.is_file():
        return [str(wrapper), task]

    # Find gradle
    gradle = shutil.which("gradle")
    
    if gradle is None:
        raise BuildError("Gradle was not found. Install Gradle 8.9 or newer and try again.")
    
    return [gradle, task]


def find_artifact(config: ProjectConfig, task: str) -> Optional[Path]:
    """
    Find the newest file a Gradle task produced.

    Args:
        config: The validated project config.
        task: The Gradle task that ran.

    Returns:
        Optional[Path]: The APK or AAB, or None if it was not found.
    """
    outputs = config.build_dir / "gradle" / "outputs"
    pattern = {
        Tasks.DEBUG: "apk/debug/*.apk",
        Tasks.RELEASE_APK: "apk/release/*.apk",
        Tasks.RELEASE_BUNDLE: "bundle/release/*.aab",
    }[task]
    found = sorted(outputs.glob(pattern), key=lambda path: path.stat().st_mtime)
    return found[-1] if found else None


def copy_artifact(config: ProjectConfig, task: str) -> None:
    """
    Copy the built APK or AAB into the build folder.

    Args:
        config: The validated project config.
        task: The Gradle task that ran.
    """
    artifact = find_artifact(config, task)
    
    if artifact is None:
        console.log("Build finished but no APK or AAB was found.", level=console.WARNING)
        return

    target = config.build_dir / artifact.name
    
    # Log something
    console.log(f"Copying {artifact.name} to {target}", level=console.DEBUG)
    
    # Copy artifact
    shutil.copyfile(artifact, target)

    if "unsigned" in artifact.name:
        console.log(
            "This APK is unsigned and cannot be installed. "
            "Set SIGNING_KEYSTORE and SIGNING_KEY_ALIAS in native/config.py.",
            level=console.WARNING,
        )
        
    console.log(f"Built {artifact.suffix[1:].upper()}: {target}", level=console.SUCCESS)


def run_gradle(command: list[str], project_dir: Path) -> tuple[int, str]:
    """
    Run Gradle, showing its output live while keeping a copy.

    Args:
        command: The Gradle command line.
        project_dir: The copied Android project.

    Returns:
        tuple[int, str]: The exit code and everything Gradle printed.

    Raises:
        BuildError: If Gradle cannot be started.
    """
    lines = []

    try:
        process = subprocess.Popen(
            command,
            cwd=project_dir,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            text=True,
            encoding="utf-8",
            errors="replace",
        )
    except OSError as error:
        raise BuildError(f"Could not run Gradle: {error}") from error

    for line in process.stdout:
        sys.stdout.write(line)
        lines.append(line)
        
    return process.wait(), "".join(lines)


def build(config: ProjectConfig, apk: bool = False) -> int:
    """
    Copy the project, generate resources, run Gradle and collect the result.

    Args:
        config: The validated project config.
        apk: True to build a release APK instead of a bundle.

    Returns:
        int: Gradle's exit code, 0 on success.

    Raises:
        BuildError: If Pillow is missing or Gradle cannot be started.
    """
    # Pillow is only needed from here on, so import it late.
    try:
        from duck_native.assets import generate
    except ModuleNotFoundError as error:
        raise BuildError("Pillow is required: pip install pillow") from error

    # Copy the Kotlin source first, then build from the copy.
    # Log first step
    console.log_raw("[ Step 1/4: Preparing project ]\n", level=console.DEBUG)
    
    # Build generated and project dirs
    generated_dir = config.build_dir / "generated"
    project_dir = prepare_project(config, generated_dir)

    # Generate the manifest overlay and resources.
    console.log_raw("")
    console.log_raw("[ Step 2/4: Generating resources ]\n", level=console.DEBUG)
    
    # Generate resources
    generate(config, generated_dir)

    # Run Gradle in the copied project.
    task = pick_task(config, apk)
    command = gradle_command(project_dir, task)
    
    # Log third step
    console.log_raw("")
    console.log_raw(f"[ Step 3/4: Running {' '.join(command)} ]\n", level=console.DEBUG)
    
    # Run gradle command
    code, output = run_gradle(command, project_dir)

    if code != 0:
        console.log_raw("")
        console.log(f"Gradle failed with exit code {code}.\n", level=console.ERROR)
        
        # Get suggestions for failure
        hints = suggest(output) or ["Read the Gradle error above for the first line that says what went wrong."]
        
        for hint in hints:
            console.log(f"Suggestion: {hint}\n", level=console.WARNING)
        
        return code

    # Copy the result next to the other build files.
    console.log_raw("")
    console.log_raw("[ Step 4/4: Collecting output ]\n", level=console.DEBUG)
    
    # Copy artifact
    copy_artifact(config, task)
    
    # Return final code
    return code
