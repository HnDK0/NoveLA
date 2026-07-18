import os
import re
import shutil

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
REPO_ROOT = os.path.abspath(os.path.join(SCRIPT_DIR, "..", ".."))
APK_WORK_DIR = os.path.join(REPO_ROOT, "app", "build", "outputs", "apk")

extension = ".apk"


def setEnvValue(key, value):
    print(f"Setting env varaible: {key}={value}")
    os.system(f"echo \"{key}={value}\" >> $GITHUB_ENV ")


def get_apks(work_dir):
    apks = []
    for root, dirs, files in os.walk(work_dir):
        for file in files:
            if file.endswith(extension):
                apks.append([root, file])
    return apks


def process_apk(path, fileName):
    fileNamePath = os.path.join(path, fileName)
    match = re.match(r"^(.+)_v(\d+\.\d+\.\d+)-(.+)-.*\.apk$", fileName)
    if not match:
        print(f"Skipping APK with unexpected name format: {fileName}")
        return
    name, version, flavour = match.groups()
    newFileName = f"NovelDokusha_v{version}_{flavour}.apk"
    newFileNamePath = os.path.join(path, newFileName)

    shutil.move(fileNamePath, newFileNamePath)

    print(f"{name=} {version=} {newFileName=}")

    setEnvValue("APP_VERSION", version)
    setEnvValue(f"APK_FILE_PATH_{flavour}", newFileNamePath)


if not os.path.isdir(APK_WORK_DIR):
    print(f"APK output directory not found: {APK_WORK_DIR}")
    raise SystemExit(1)

apk_entries = get_apks(APK_WORK_DIR)
if not apk_entries:
    print(f"No APK files found under: {APK_WORK_DIR}")
    raise SystemExit(1)

for [path, fileName] in apk_entries:
    process_apk(path, fileName)
