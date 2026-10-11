#!/usr/bin/env python3
"""PreToolUse hook: stop an AI agent from reading or writing files that hold secrets.

Claude Code runs this before every Read, Edit, Write and Bash call (see "hooks" in
.claude/settings.json) and passes the call as JSON on stdin. Exit code 2 blocks the
call and shows the message on stderr to the agent; exit code 0 lets it through.

Blocked: .env files (except .env.example / .env.sample / .env.template) and private
key material (*.pem, *.key, *.p12, *.pfx, *.jks, id_rsa, id_ed25519).

Why: this project simulates a regulated system. A real API_TOKEN or sign-in password
lives only in the environment or in a local .env file, and must not end up in a
conversation, a log or a commit.

The Bash check is best effort: it blocks a sensitive file passed as an argument to a
program that prints or copies files (cat, head, source, cp, ...), and nothing else.
The file tools (Read, Edit, Write) are checked exactly. Unreadable input lets the call through, so a hook bug never blocks all work.
"""
import json
import re
import sys

SAFE_ENV_SUFFIXES = (".example", ".sample", ".template")
KEY_FILE = re.compile(r"(\.(pem|key|p12|pfx|jks)|(^|/)id_(rsa|ed25519))$")
ENV_FILE = re.compile(r"(^|/)\.env(\.[\w.-]+)?$")

# In a shell command: where one simple command ends, and the programs that print or copy a file.
COMMAND_SEPARATOR = re.compile(r"[;&|\n()]+")
READS_A_FILE = {"cat", "less", "more", "head", "tail", "source", ".", "base64", "xxd", "strings", "cp", "scp"}


def is_sensitive(path: str) -> bool:
    path = path.strip().strip("\"'")
    if ENV_FILE.search(path):
        return not path.endswith(SAFE_ENV_SUFFIXES)
    return bool(KEY_FILE.search(path))


def blocked_path(call: dict):
    tool = call.get("tool_name", "")
    tool_input = call.get("tool_input") or {}
    if tool in ("Read", "Edit", "Write"):
        path = str(tool_input.get("file_path", ""))
        return path if is_sensitive(path) else None
    if tool == "Bash":
        # Only a sensitive file passed to a file-reading program counts, so a command that
        # merely mentions ".env" in text (a commit message, a heredoc) is not blocked.
        for simple_command in COMMAND_SEPARATOR.split(str(tool_input.get("command", ""))):
            words = simple_command.split()
            if words and words[0] in READS_A_FILE:
                for argument in words[1:]:
                    if is_sensitive(argument):
                        return argument
    return None


def main() -> int:
    try:
        call = json.load(sys.stdin)
    except (ValueError, OSError):
        return 0
    if not isinstance(call, dict):
        return 0
    path = blocked_path(call)
    if path is None:
        return 0
    print(
        f"Blocked by .claude/hooks/block_sensitive_files.py: '{path}' may hold a secret "
        "(.env file or private key). Do not read, copy or edit it. Ask the user for the "
        "value's name instead, or use the documented defaults in README.md.",
        file=sys.stderr,
    )
    return 2


if __name__ == "__main__":
    sys.exit(main())
