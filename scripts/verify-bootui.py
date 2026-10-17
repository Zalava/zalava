#!/usr/bin/env python3
"""Smoke-test an already running, disposable :app:bootuiRun instance."""

import argparse
import json
from urllib.error import HTTPError
from urllib.parse import urlsplit
from urllib.request import ProxyHandler, Request, build_opener


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--url", default="http://127.0.0.1:18080")
    parser.add_argument("--management-url", default="http://127.0.0.1:18082")
    args = parser.parse_args()
    for url in (args.url, args.management_url):
        if urlsplit(url).hostname not in ("localhost", "127.0.0.1", "::1"):
            parser.error("use a loopback URL for the disposable development host")
    opener = build_opener(ProxyHandler({}))

    def read(base, path, headers=None):
        with opener.open(Request(base.rstrip("/") + path, headers=headers or {}), timeout=15) as response:
            if response.status != 200:
                raise RuntimeError(f"{path}: expected HTTP 200, got {response.status}")
            return response.read().decode()

    shell = read(args.url, "/bootui")
    if "<script" not in shell:
        raise RuntimeError("BootUI did not return its application shell")
    overview = json.loads(read(args.url, "/bootui/api/overview"))
    if overview["bootUiVersion"] != "2.1.0":
        raise RuntimeError("unexpected BootUI version")
    if "local" not in overview["activeProfiles"] or not overview["activation"]["localhostOnly"]:
        raise RuntimeError("BootUI must use the local profile and localhost-only policy")
    health = json.loads(read(args.url, "/bootui/api/health"))
    database = next(component for component in health["components"] if component["name"] == "db")
    if health["status"] != "UP" or database["status"] != "UP":
        raise RuntimeError("development host/database is not healthy")
    login = read(args.url, "/login")
    # Exercises real JTE rendering: missing generated templates previously caused a redirect loop.
    if 'name="username"' not in login or 'name="password"' not in login:
        raise RuntimeError("Zalava's login form did not render")
    management = json.loads(read(args.management_url, "/actuator/health"))
    if management["status"] != "UP":
        raise RuntimeError("management health is not UP")
    try:
        read(args.url, "/bootui/api/overview", {"Host": "non-local.example"})
    except HTTPError as error:
        if error.code != 403:
            raise RuntimeError(f"unexpected non-local Host response: {error.code}") from error
    else:
        raise RuntimeError("BootUI accepted a non-local Host header")
    print("PASS: BootUI 2.1.0, local activation, host/database health, JTE login and Host rejection")


if __name__ == "__main__":
    main()
