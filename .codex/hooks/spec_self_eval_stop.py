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

UTC = dt.timezone.utc


def main() -> int:
    try:
        payload = read_payload()
        cwd = pathlib.Path(payload.get("cwd") or os.getcwd()).resolve()
        turn_started_at = find_turn_started_at(payload)
        features = touched_spec_features(cwd, turn_started_at)
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


def find_turn_started_at(payload: dict) -> float:
    transcript_path = payload.get("transcript_path")
    turn_id = payload.get("turn_id")
    if transcript_path and turn_id:
        path = pathlib.Path(transcript_path)
        if path.is_file():
            for event in reversed(read_jsonl(path)):
                event_payload = event.get("payload") or {}
                matching_task_started = (
                    event.get("type") == "event_msg"
                    and event_payload.get("type") == "task_started"
                    and event_payload.get("turn_id") == turn_id
                )
                matching_turn_context = (
                    event.get("type") == "turn_context"
                    and event_payload.get("turn_id") == turn_id
                )
                if matching_task_started or matching_turn_context:
                    return parse_timestamp(event.get("timestamp"))

    return dt.datetime.now(UTC).timestamp()


def read_jsonl(path: pathlib.Path) -> list[dict]:
    events: list[dict] = []
    for line in path.read_text(encoding="utf-8").splitlines():
        if not line.strip():
            continue
        try:
            events.append(json.loads(line))
        except json.JSONDecodeError:
            continue
    return events


def parse_timestamp(value: str | None) -> float:
    if not value:
        return dt.datetime.now(UTC).timestamp()
    normalized = value.replace("Z", "+00:00")
    return dt.datetime.fromisoformat(normalized).timestamp()


def touched_spec_features(cwd: pathlib.Path, turn_started_at: float) -> set[str]:
    specs_dir = cwd / ".specs"
    if not specs_dir.is_dir():
        return set()

    threshold = turn_started_at - 1.0
    features: set[str] = set()
    for path in specs_dir.rglob("*"):
        relative = path.relative_to(cwd)
        parts = relative.parts
        if len(parts) < 2:
            continue
        if parts[0] != ".specs" or not parts[1]:
            continue
        if len(parts) == 2 and not path.is_dir():
            continue
        try:
            stat = path.stat()
        except OSError:
            continue
        if max(stat.st_mtime, stat.st_ctime) >= threshold:
            features.add(parts[1])

    return features


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
            timeout=int(env.get("SPEC_SELF_EVAL_TIMEOUT_SEC", "840")),
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
            timeout=int(env.get("SPEC_SELF_EVAL_TIMEOUT_SEC", "840")),
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
