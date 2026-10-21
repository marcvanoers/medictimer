"""Workspace-local Android tools, preserving the managed proxy and TLS trust."""
import os
from pathlib import Path
import subprocess
from urllib.parse import urlparse

ROOT = Path(__file__).resolve().parents[1]
TOOLS = Path('/workspace/tooling')
SDK = TOOLS / 'android-sdk'


def environment():
    TOOLS.mkdir(parents=True, exist_ok=True)
    env = os.environ.copy()
    env['JAVA_HOME'] = str(TOOLS / 'jdk')
    env['PATH'] = str(TOOLS / 'jdk/bin') + os.pathsep + env['PATH']
    env['ANDROID_HOME'] = str(SDK)
    env['ANDROID_USER_HOME'] = str(TOOLS / 'android-user')
    env['GRADLE_USER_HOME'] = str(TOOLS / 'gradle-home')
    Path(env['ANDROID_USER_HOME']).mkdir(exist_ok=True)
    options = ['-Duser.home=' + str(TOOLS / 'home')]
    # Java does not automatically honor HTTPS_PROXY; retain the supported proxy route.
    proxy = urlparse(env.get('HTTPS_PROXY', env.get('https_proxy', '')))
    if proxy.hostname:
        for protocol in ('http', 'https'):
            options += [f'-D{protocol}.proxyHost={proxy.hostname}', f'-D{protocol}.proxyPort={proxy.port or 8080}']
        options += ['-Dhttp.nonProxyHosts=localhost|127.*|[::1]']
    if (TOOLS / 'cacerts').exists():
        options += ['-Djavax.net.ssl.trustStore=' + str(TOOLS / 'cacerts'), '-Djavax.net.ssl.trustStorePassword=changeit']
    env['JAVA_TOOL_OPTIONS'] = ' '.join(filter(None, [env.get('JAVA_TOOL_OPTIONS', ''), *options]))
    return env


def gradle(arguments):
    return subprocess.call([str(ROOT / 'gradlew'), '--no-daemon', *arguments], cwd=ROOT, env=environment())
