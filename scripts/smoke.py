"""在独立 Compose 环境验证导入、同口径对比、前端代理和重启恢复。"""

import argparse
import hashlib
import json
import time
import uuid
from pathlib import Path
from urllib.error import HTTPError
from urllib.parse import urlencode
from urllib.request import ProxyHandler, Request, build_opener


ROOT = Path(__file__).resolve().parents[1]
FIXTURES = ROOT / "src" / "test" / "resources" / "qa-system"
REPORTS = (
    FIXTURES / "2026-11-17-gpt-6-luna.report.json",
    FIXTURES / "2026-11-18-stage14-regression-luna.report.json",
)
OPENER = build_opener(ProxyHandler({}))


def request(url: str, data: bytes | None = None, content_type: str | None = None):
    headers = {"Content-Type": content_type} if content_type else {}
    req = Request(url, data=data, headers=headers, method="POST" if data is not None else "GET")
    try:
        with OPENER.open(req, timeout=12) as response:
            body = response.read()
            return response.status, json.loads(body) if "json" in response.headers.get("Content-Type", "") else body.decode("utf-8")
    except HTTPError as exc:
        return exc.code, exc.read().decode("utf-8", errors="replace")


def ready(api: str, web: str):
    deadline = time.monotonic() + 150
    while time.monotonic() < deadline:
        try:
            api_status, api_body = request(api + "/health")
            web_status, web_body = request(web + "/health")
            page_status, page_body = request(web + "/")
            if (api_status, web_status, page_status) == (200, 200, 200) and api_body == web_body == {"status": "UP"} and "LLM Eval Hub" in page_body:
                return
        except OSError:
            pass
        time.sleep(2)
    raise RuntimeError("前后端未在 150 秒内就绪；请查看 docker compose logs")


def import_qa(api: str, path: Path):
    boundary = "stage6-" + uuid.uuid4().hex
    fields = (
        f"--{boundary}\r\nContent-Disposition: form-data; name=\"resultSource\"\r\n\r\nLIVE_MODEL\r\n"
        f"--{boundary}\r\nContent-Disposition: form-data; name=\"modelId\"\r\n\r\ngpt-6-luna\r\n"
        f"--{boundary}\r\nContent-Disposition: form-data; name=\"report\"; filename=\"{path.name}\"\r\n"
        "Content-Type: application/json\r\n\r\n"
    ).encode("utf-8")
    body = fields + path.read_bytes() + f"\r\n--{boundary}--\r\n".encode("ascii")
    status, result = request(api + "/api/imports/qa-system", body, f"multipart/form-data; boundary={boundary}")
    assert status in (200, 201), (path.name, status, result)
    return result


def job_document():
    cases = [
        {"id": "stage6-resume", "task": "resume", "passed": True,
         "checks": [{"name": "schema_valid", "passed": True}]},
        {"id": "stage6-match", "task": "match", "passed": False,
         "checks": [{"name": "schema_valid", "passed": True},
                    {"name": "evidence_present", "passed": False}]},
    ]
    dataset = hashlib.sha256(json.dumps(cases, sort_keys=True, ensure_ascii=False).encode("utf-8")).hexdigest()
    return {"sourceRunId": "stage6-smoke-" + uuid.uuid4().hex,
            "datasetSha256": dataset, "runLabel": "stage6-synthetic-rules",
            "resultSource": "OFFLINE_FIXTURE", "modelId": None,
            "startedAt": "2026-11-20T00:00:00Z", "cases": cases}


def check_persisted(web: str, state: dict):
    for name in ("baseline", "candidate"):
        run_id = state[name]
        status, result = request(web + f"/api/runs/{run_id}/results")
        assert status == 200 and result["run"]["sourceProject"] == "qa-system", (name, status, result)
        assert result["provenance"]["sampleCount"] == 5
    status, job = request(web + f"/api/runs/{state['job']}/job-results")
    assert status == 200 and job["sampleCount"] == 2 and job["passedCount"] == 1, (status, job)
    query = urlencode({"baselineId": state["baseline"], "candidateId": state["candidate"]})
    status, comparison = request(web + "/api/comparisons?" + query)
    assert status == 200 and len(comparison["metrics"]) == 9 and len(comparison["cases"]) == 5, (status, comparison)
    assert any(item["delta"] not in (None, 0) for item in comparison["metrics"])
    status, runs = request(web + "/api/runs?limit=100")
    assert status == 200 and {state["baseline"], state["candidate"], state["job"]}.issubset(
        {item["id"] for item in runs}), (status, runs)


def seed(api: str, web: str, state_path: Path):
    baseline, candidate = (import_qa(api, path) for path in REPORTS)
    assert baseline["runId"] != candidate["runId"]
    duplicate = import_qa(api, REPORTS[0])
    assert duplicate["runId"] == baseline["runId"] and duplicate["imported"] is False

    document = job_document()
    payload = json.dumps(document, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
    status, job = request(api + "/api/imports/job-agent", payload, "application/json")
    assert status == 201 and job["imported"] is True, (status, job)
    status, duplicate_job = request(api + "/api/imports/job-agent", payload, "application/json")
    assert status == 200 and duplicate_job["runId"] == job["runId"], (status, duplicate_job)
    changed = dict(document, runLabel="changed-label")
    status, _ = request(api + "/api/imports/job-agent", json.dumps(changed).encode("utf-8"), "application/json")
    assert status == 409, status

    state = {"baseline": baseline["runId"], "candidate": candidate["runId"], "job": job["runId"]}
    check_persisted(web, state)
    state_path.parent.mkdir(parents=True, exist_ok=True)
    state_path.write_text(json.dumps(state, indent=2) + "\n", encoding="utf-8")
    print("隔离环境：两份 QA 报告可对比，求职助手规则报告可查询，重复导入幂等。")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("phase", choices=("seed", "verify"))
    parser.add_argument("--api", default="http://127.0.0.1:18080")
    parser.add_argument("--web", default="http://127.0.0.1:5173")
    parser.add_argument("--state", type=Path, default=ROOT / "target" / "stage6-smoke-state.json")
    args = parser.parse_args()
    ready(args.api, args.web)
    if args.phase == "seed":
        seed(args.api, args.web, args.state)
    else:
        check_persisted(args.web, json.loads(args.state.read_text(encoding="utf-8")))
        print("Compose 重启后：原运行、逐例结果与同口径对比仍可查询。")


if __name__ == "__main__":
    main()
