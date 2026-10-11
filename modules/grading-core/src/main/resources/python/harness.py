# ITtools Python 评测脚本：在沙箱（容器或本地子进程）内运行。
# 标准输入：{"code": str, "inputs": [str], "timeout": 秒}
# 标准输出：{"results": [{"status": OK|RE|TLE, "stdout": str, "stderr": str, "ms": int}]}
import json, os, subprocess, sys, tempfile, time

LIMIT = 64 * 1024


def clip(s):
    return s if len(s) <= LIMIT else s[:LIMIT] + "\n...（输出过长已截断）"


def main():
    req = json.loads(sys.stdin.buffer.read().decode("utf-8"))
    work = tempfile.mkdtemp(prefix="py_")
    path = os.path.join(work, "main.py")
    with open(path, "w", encoding="utf-8") as f:
        f.write(req["code"])
    env = {"PYTHONIOENCODING": "utf-8", "PYTHONDONTWRITEBYTECODE": "1", "PATH": os.environ.get("PATH", "")}
    if os.name == "nt":
        env["SYSTEMROOT"] = os.environ.get("SYSTEMROOT", "")
    results = []
    for data in req["inputs"]:
        start = time.time()
        try:
            p = subprocess.run([sys.executable, "-I", path], input=data.encode("utf-8"), capture_output=True,
                               timeout=req["timeout"], cwd=work, env=env)
            results.append({
                "status": "OK" if p.returncode == 0 else "RE",
                "stdout": clip(p.stdout.decode("utf-8", "replace")),
                "stderr": clip(p.stderr.decode("utf-8", "replace").replace(path, "main.py")),
                "ms": int((time.time() - start) * 1000),
            })
        except subprocess.TimeoutExpired as e:
            out = e.stdout.decode("utf-8", "replace") if e.stdout else ""
            results.append({"status": "TLE", "stdout": clip(out), "stderr": "", "ms": int(req["timeout"] * 1000)})
    sys.stdout.buffer.write(json.dumps({"results": results}, ensure_ascii=False).encode("utf-8"))


main()
