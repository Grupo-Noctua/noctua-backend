"""Validate the packaged image without AWS, a database, env files or bind mounts.

Usage: python scripts/verify_image.py noctua-backend:local-20261014
Requires Python 3.10+ and Docker with Linux containers.
"""

import argparse
import io
import json
from pathlib import Path, PurePosixPath
import subprocess
import tarfile
import zipfile


SQL_NAMES = (
    "funcionarios-total.sql",
    "funcionarios-tecnologia.sql",
    "funcionarios-senioridade.sql",
    "funcionarios-area.sql",
)


def docker(*args):
    return subprocess.check_output(["docker", *args], text=True).strip()


def is_env_file(name):
    basename = PurePosixPath(name).name
    return basename == ".env" or basename.startswith(".env.") or basename.endswith(".env")


def verify(image):
    # Resolve once: checks must all inspect the same local image even if its tag moves.
    metadata = json.loads(docker("image", "inspect", image))[0]
    image_id = metadata["Id"]
    if metadata["Config"]["User"] != "noctua":
        raise RuntimeError("The image must run as noctua by default")
    if docker("run", "--rm", "--entrypoint", "id", image_id, "-u") == "0":
        raise RuntimeError("The runtime user must not be root")

    source = Path(__file__).resolve().parents[1] / "database" / "athena"
    for name in SQL_NAMES:
        contents = subprocess.check_output(
            ["docker", "run", "--rm", "--entrypoint", "cat", image_id, f"/app/config/{name}"]
        )
        if not contents or contents != (source / name).read_bytes():
            raise RuntimeError(f"Packaged SQL differs from source: {name}")

    # Export an unstarted, unconfigured container: no application connections are made.
    container = docker("create", image_id)
    try:
        if json.loads(docker("inspect", container))[0]["Mounts"]:
            raise RuntimeError("Validation requires an image without external volumes")
        jar_found = False
        with subprocess.Popen(["docker", "export", container], stdout=subprocess.PIPE) as export:
            try:
                with tarfile.open(fileobj=export.stdout, mode="r|*") as filesystem:
                    for member in filesystem:
                        if is_env_file(member.name):
                            raise RuntimeError(f"Environment file found: {member.name}")
                        if member.name.lstrip("./") == "app/app.jar" and member.isfile():
                            jar_found = True
                            with zipfile.ZipFile(io.BytesIO(filesystem.extractfile(member).read())) as jar:
                                if any(is_env_file(name) for name in jar.namelist()):
                                    raise RuntimeError("Environment file found inside the JAR")
                                if "BOOT-INF/classes/application.yml" not in jar.namelist():
                                    raise RuntimeError("Expected Spring Boot application missing from JAR")
                # Drain trailing tar padding before waiting for the Docker process.
                while export.stdout.read(1024 * 1024):
                    pass
                if export.wait() != 0:
                    raise RuntimeError("Docker export failed")
            finally:
                export.stdout.close()
                if export.poll() is None:
                    export.terminate()
        if not jar_found:
            raise RuntimeError("Application JAR missing")
    finally:
        docker("rm", container)

    print(f"PASS: {image_id}")
    print("JAR present; four matching SQLs readable by noctua without mounts; no env files in filesystem/JAR.")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("image", help="Local image tag or ID to validate")
    verify(parser.parse_args().image)
