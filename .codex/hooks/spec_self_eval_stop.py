#!/usr/bin/env python3
"""Stop hook that gates touched .specs/<feature>/ folders on spec-self-eval."""

from __future__ import annotations

import datetime as dt
import json
import os
import pathlib
import shutil
import subprocess
import sys
import tempfile
from typing import Iterable


def main() -> int:
    try:
        payload = read_payload()
        cwd = pathlib.Path(payload.get("cwd") or os.getcwd()).resolve()
        features = changed_spec_features(cwd)
        if not features:
            return 0

        failures_by_feature: dict[str, list[str]] = {}
        eval_errors: list[str] = []
        report_date = dt.datetime.now().date().isoformat()
        for feature in sorted(features):
            feature_dir = cwd / ".specs" / feature
            if not feature_dir.is_dir():
                eval_errors.append(
                    f".specs/{feature}/ was touched but is no longer a directory."
                )
                continue

            report = eval_report_path(feature_dir, report_date)
            if needs_eval(feature_dir, report):
                result = run_spec_self_eval(cwd, feature_dir, report, payload)
                if result.returncode != 0:
                    eval_errors.append(format_eval_error(feature, result))
                    continue

            failures = extract_failures(report)
            if failures:
                failures_by_feature[feature] = failures

        if eval_errors or failures_by_feature:
            print_block(build_block_reason(eval_errors, failures_by_feature))

        return 0
    except Exception as exc:  # pragma: no cover - defensive hook behavior
        print_block(
            "spec-self-eval Stop hook failed before it could verify touched specs.\n"
            f"Hook error: {exc}\n\n"
            "Fix the hook failure or run spec-self-eval manually before closing the turn."
        )
        return 0


def read_payload() -> dict:
    raw = sys.stdin.read().strip()
    if not raw:
        return {}
    return json.loads(raw)


def changed_spec_features(cwd: pathlib.Path) -> set[str]:
    """Return .specs/<feature> folders with current working-tree changes."""
    features = changed_spec_features_from_git(cwd)
    if features is not None:
        return features

    return set()


def changed_spec_features_from_git(cwd: pathlib.Path) -> set[str] | None:
    result = subprocess.run(
        ["git", "status", "--porcelain=v1", "-z", "--", ".specs"],
        cwd=cwd,
        text=True,
        capture_output=True,
    )
    if result.returncode != 0:
        return None

    features: set[str] = set()
    entries = [entry for entry in result.stdout.split("\0") if entry]
    index = 0
    while index < len(entries):
        entry = entries[index]
        status = entry[:2]
        path_text = entry[3:] if len(entry) > 3 else ""
        add_feature_from_git_status_path(features, path_text)

        if "R" in status or "C" in status:
            index += 1
            if index < len(entries):
                add_feature_from_git_status_path(features, entries[index])

        index += 1

    return features


def add_feature_from_git_status_path(features: set[str], path_text: str) -> None:
    if not path_text:
        return

    feature = feature_from_spec_path(pathlib.PurePath(path_text))
    if feature is not None:
        features.add(feature)


def feature_from_spec_path(
    relative: pathlib.PurePath, include_feature_dir: bool = False
) -> str | None:
    parts = relative.parts
    if len(parts) < 2 or parts[0] != ".specs" or not parts[1]:
        return None
    if len(parts) == 2 and not include_feature_dir:
        return None
    return parts[1]


def run_spec_self_eval(
    cwd: pathlib.Path,
    feature_dir: pathlib.Path,
    report_path: pathlib.Path,
    payload: dict,
) -> subprocess.CompletedProcess[str]:
    env = os.environ.copy()
    env.update(
        {
            "SPEC_FEATURE_DIR": str(feature_dir),
            "SPEC_REPORT_PATH": str(report_path),
            "SPEC_REPORT_DATE": report_path.stem.removeprefix("eval-report-"),
            "CODEX_STOP_HOOK_PAYLOAD": json.dumps(payload),
        }
    )

    override = env.get("SPEC_SELF_EVAL_COMMAND")
    if override:
        return subprocess.run(
            override,
            cwd=cwd,
            env=env,
            shell=True,
            text=True,
            capture_output=True,
            timeout=int(env.get("SPEC_SELF_EVAL_TIMEOUT_SEC", "180")),
        )

    codex = codex_binary(env)
    prompt = build_eval_prompt(cwd, feature_dir, report_path)
    with tempfile.NamedTemporaryFile(prefix="spec-self-eval-", suffix=".txt") as out:
        return subprocess.run(
            [
                codex,
                "--ask-for-approval",
                "never",
                "exec",
                "--disable",
                "hooks",
                "--disable",
                "codex_hooks",
                "-C",
                str(cwd),
                "--sandbox",
                "workspace-write",
                "--output-last-message",
                out.name,
                prompt,
            ],
            cwd=cwd,
            env=env,
            text=True,
            capture_output=True,
            timeout=int(env.get("SPEC_SELF_EVAL_TIMEOUT_SEC", "180")),
        )


def build_eval_prompt(
    cwd: pathlib.Path, feature_dir: pathlib.Path, report_path: pathlib.Path
) -> str:
    relative_feature = feature_dir.relative_to(cwd)
    relative_report = report_path.relative_to(cwd)
    fallback_skill = cwd / ".codex" / "skills" / "spec-self-eval" / "SKILL.md"
    return "\n".join(
        [
            "Use the `$spec-self-eval` skill on this feature folder:",
            f"`{relative_feature}`",
            "",
            "The skill must write or overwrite:",
            f"`{relative_report}`",
            "",
            "If `$spec-self-eval` is not registered in this Codex runtime, read and follow "
            f"`{fallback_skill.relative_to(cwd)}` as the project-local equivalent. "
            "Do not edit any file except the dated eval report.",
        ]
    )


def eval_report_path(feature_dir: pathlib.Path, report_date: str) -> pathlib.Path:
    return feature_dir / f"eval-report-{report_date}.md"


def needs_eval(feature_dir: pathlib.Path, report_path: pathlib.Path) -> bool:
    if not report_path.is_file():
        return True

    try:
        report_mtime = report_path.stat().st_mtime
    except OSError:
        return True

    for path in feature_dir.rglob("*"):
        if not path.is_file():
            continue
        if path == report_path or path.name.startswith("eval-report-"):
            continue
        try:
            if path.stat().st_mtime > report_mtime:
                return True
        except OSError:
            return True

    return False


def codex_binary(env: dict[str, str]) -> str:
    configured = env.get("CODEX_BIN")
    if configured:
        return configured

    from_path = shutil.which("codex")
    if from_path:
        return from_path

    app_binary = pathlib.Path("/Applications/Codex.app/Contents/Resources/codex")
    if app_binary.is_file():
        return str(app_binary)

    return "codex"


def extract_failures(report_path: pathlib.Path) -> list[str]:
    if not report_path.is_file():
        return [f"[FAIL] missing report: `{display_path(report_path)}`"]

    failures: list[str] = []
    for line in report_path.read_text(encoding="utf-8").splitlines():
        stripped = line.strip()
        if "[FAIL]" in stripped:
            failures.append(stripped)
            continue

        if not stripped.startswith("|") or stripped.startswith("|---"):
            continue
        columns = [column.strip() for column in stripped.strip("|").split("|")]
        if len(columns) < 2 or columns[0].lower() == "check":
            continue
        result = columns[1].upper().replace("`", "")
        if result == "FAIL" or "[FAIL]" in result:
            evidence = columns[2] if len(columns) > 2 else ""
            failures.append(f"[FAIL] {columns[0]}: {evidence}".rstrip(": "))

    return failures


def format_eval_error(
    feature: str, result: subprocess.CompletedProcess[str]
) -> str:
    details = "\n".join(
        chunk
        for chunk in [
            tail(result.stderr.strip()),
            tail(result.stdout.strip()),
        ]
        if chunk
    )
    if not details:
        details = "spec-self-eval exited without output."
    return f".specs/{feature}/: spec-self-eval exited {result.returncode}\n{details}"


def tail(text: str, max_lines: int = 20) -> str:
    lines = text.splitlines()
    return "\n".join(lines[-max_lines:])


def build_block_reason(
    eval_errors: Iterable[str], failures_by_feature: dict[str, list[str]]
) -> str:
    sections: list[str] = []
    errors = list(eval_errors)
    if errors:
        sections.append(
            "spec-self-eval could not complete for touched spec folder(s):\n"
            + "\n\n".join(errors)
        )

    for feature, failures in failures_by_feature.items():
        sections.append(
            f"spec-self-eval found [FAIL] items in `.specs/{feature}/`:\n"
            + "\n".join(f"- {failure}" for failure in failures)
        )

    sections.append(
        "Fix the spec issues above, then let the turn stop again. "
        "This Stop hook will re-run spec-self-eval before allowing closure."
    )
    return "\n\n".join(sections)


def print_block(reason: str) -> None:
    print(json.dumps({"decision": "block", "reason": reason}))


def display_path(path: pathlib.Path) -> str:
    try:
        return str(path.relative_to(path.cwd()))
    except ValueError:
        return str(path)


if __name__ == "__main__":
    raise SystemExit(main())
