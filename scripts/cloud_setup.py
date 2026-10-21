#!/usr/bin/env python3
"""Repeatable Android cloud setup. Does not edit application files or lockfiles."""
import hashlib
from pathlib import Path
import shutil
import subprocess
import tarfile
import urllib.request
import zipfile

from cloud_common import TOOLS, SDK, environment, gradle

TOOLS.mkdir(parents=True, exist_ok=True)
if not (TOOLS / 'jdk/bin/javac').exists():
    archive = TOOLS / 'jdk.tar.gz'
    if not archive.exists():
        urllib.request.urlretrieve('https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.11%2B10/OpenJDK21U-jdk_x64_linux_hotspot_21.0.11_10.tar.gz', archive)
    if hashlib.sha256(archive.read_bytes()).hexdigest() != '4b2220e232a97997b436ca6ab15cbf70171ecff52958a46159dfa5a8c44ca4de':
        raise RuntimeError('JDK checksum mismatch')
    with tarfile.open(archive) as source:
        name = source.getmembers()[0].name.split('/')[0]
        source.extractall(TOOLS, filter='data')
    (TOOLS / 'jdk').symlink_to(TOOLS / name, target_is_directory=True)
sdkmanager = SDK / 'cmdline-tools/19.0/bin/sdkmanager'
if not sdkmanager.exists():
    archive = TOOLS / 'cmdline19.zip'
    if not archive.exists():
        urllib.request.urlretrieve('https://dl.google.com/android/repository/commandlinetools-linux-13114758_latest.zip', archive)
    if hashlib.sha256(archive.read_bytes()).hexdigest() != '7ec965280a073311c339e571cd5de778b9975026cfcbe79f2b1cdcb1e15317ee':
        raise RuntimeError('Android command-line tools checksum mismatch')
    with zipfile.ZipFile(archive) as source:
        for item in source.infolist():
            relative = Path(item.filename).relative_to('cmdline-tools')
            if '..' in relative.parts:
                raise RuntimeError('Unsafe SDK archive path')
            target = SDK / 'cmdline-tools/19.0' / relative
            if item.is_dir():
                target.mkdir(parents=True, exist_ok=True)
            else:
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_bytes(source.read(item))
                target.chmod(0o755 if 'bin' in relative.parts else 0o644)

# Trust the environment's public proxy CA in addition to the standard Java CAs.
# Certificates are public trust material; credential files are never read.
proxy_ca = Path('/usr/local/share/ca-certificates/environment-proxy-ca.crt')
if proxy_ca.exists():
    java = Path(shutil.which('java')).resolve()
    cacerts = java.parents[1] / 'lib/security/cacerts'
    shutil.copyfile(cacerts, TOOLS / 'cacerts')
    subprocess.run(['keytool', '-importcert', '-noprompt', '-alias', 'medictimer-environment-proxy',
                    '-file', str(proxy_ca), '-keystore', str(TOOLS / 'cacerts'), '-storepass', 'changeit'], check=True)

env = environment()
licenses = subprocess.run([str(sdkmanager), '--sdk_root=' + str(SDK), '--licenses'],
                          input='y\n' * 100, text=True, env=env, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
if licenses.returncode:
    print(licenses.stdout[-2000:])
    raise SystemExit(licenses.returncode)
subprocess.run([str(sdkmanager), '--sdk_root=' + str(SDK), 'platforms;android-35',
                'build-tools;35.0.0', 'platform-tools'], env=env, check=True)
raise SystemExit(gradle(['assembleDebug']))
