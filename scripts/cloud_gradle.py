#!/usr/bin/env python3
"""Run Gradle using the workspace-local SDK and the managed network environment."""
import sys
from cloud_common import gradle

if __name__ == '__main__':
    sys.exit(gradle(sys.argv[1:]))
