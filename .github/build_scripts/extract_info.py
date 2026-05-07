import os
import re
import shutil

mainDir = os.getcwd()
workDir = os.path.join(mainDir, "app", "build", "outputs", "apk")

extension = ".apk"


def setEnvValue(key, value):
    print(f"Setting env varaible: {key}={value}")
    os.system(f"echo \"{key}={value}\" >> $GITHUB_ENV ")


def getAPKs():
    list = []
    for root, dirs, files in os.walk(workDir):
        for file in files:
            if file.endswith(extension):
                list.append([root, file])
    return list


def processAPK(path, fileName):
    fileNamePath = os.path.join(path, fileName)
    print(f"Processing APK: {fileName}")

    # Flexible regex to match variations like:
    # NoveLA_v1.2.7-release.apk
    # NoveLA_v1.2.7-arm64-v8a-release.apk
    # app-release.apk
    match = re.match(r"^(.+)_v(\d+\.\d+\.\d+)(?:-(.+))?-(?:release|debug).*\.apk$", fileName)
    
    if match:
        name, version, extra = match.groups()
        flavour = extra if extra else "universal"
    else:
        # Fallback for unexpected names like app-release.apk
        print(f"Warning: Filename '{fileName}' did not match expected pattern. Using fallbacks.")
        name = "Novela"
        version = "unknown"
        
        # Try to at least get version from somewhere if possible, 
        # but for now we'll rely on the build system to provide it if this fails.
        # Often version is in the path or we can extract it simply:
        v_match = re.search(r"v(\d+\.\d+\.\d+)", fileName)
        if v_match:
            version = v_match.group(1)

    newFileName = f"Novela_v{version}.apk"
    newFileNamePath = os.path.join(path, newFileName)

    try:
        shutil.move(fileNamePath, newFileNamePath)
        print(f"Moved {fileName} to {newFileName}")
    except Exception as e:
        print(f"Error moving file: {e}")
        newFileNamePath = fileNamePath # Fallback to original path if move fails

    setEnvValue("APP_VERSION", version)
    # We use a sanitized flavour name for the env variable key
    # safe_flavour = re.sub(r"[^a-zA-Z0-9_]", "_", flavour)
    # setEnvValue(f"APK_FILE_PATH_{safe_flavour}", newFileNamePath)
    
    # Also set a generic path if it's the only one or a preferred one
    setEnvValue("APK_FILE_PATH", newFileNamePath)


for [path, fileName] in getAPKs():
    processAPK(path, fileName)
