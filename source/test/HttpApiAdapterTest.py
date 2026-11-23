"""Build Java server and exercise the Postman collection against isolated local data.
Run from repository root: python -I source/test/HttpApiAdapterTest.py
Requires JDK 17+ and Python 3.9+. No third-party Python packages.
"""
import concurrent.futures
import json
import pathlib
import re
import shutil
import socket
import subprocess
import sys

import tempfile
import time
import urllib.error
import urllib.request

sys.stdout.reconfigure(encoding="utf-8")
# Oracle javapath can be a launcher shim: terminate() would kill the shim only,
# leaving its real JVM child alive. Resolve java.home and spawn the JDK binary.
java_settings = subprocess.run([shutil.which("java"), "-XshowSettings:properties", "-version"],
                               capture_output=True, text=True, check=True)
java_home = next(line.split("=", 1)[1].strip() for line in java_settings.stderr.splitlines()
                 if line.strip().startswith("java.home ="))
JAVA = str(pathlib.Path(java_home) / "bin/java.exe")
JAVAC = str(pathlib.Path(java_home) / "bin/javac.exe")

ROOT = pathlib.Path(__file__).resolve().parents[2]
COLLECTION = ROOT / "ChatNest.postman_collection.json"


def requests(items):
    for item in items:
        if "item" in item:
            yield from requests(item["item"])
        else:
            yield item


def run():
    # Never attach tests to a live user's database/server.
    probe = socket.socket()
    try:
        probe.bind(("127.0.0.1", 8888))
    except OSError as exc:
        raise RuntimeError("Port 8888 is busy. Stop the TCP server before this test.") from exc
    finally:
        probe.close()
    with socket.socket() as probe:
        probe.bind(("127.0.0.1", 0))
        http_port = probe.getsockname()[1]

    collection = json.loads(COLLECTION.read_text(encoding="utf-8"))
    items = list(requests(collection["item"]))
    suffix = str(time.time_ns())
    variables = {var["key"]: var["value"] for var in collection["variable"]}
    variables.update(baseUrl="http://127.0.0.1:" + str(http_port),
                     aliceUsername="test_alice_" + suffix,
                     bobUsername="test_bob_" + suffix,
                     charlieUsername="test_charlie_" + suffix)

    def substitute(text):
        return re.sub(r"\{\{(\w+)\}\}", lambda match: str(variables[match[1]]), text)

    def call(method, path, payload=None, session=None, raw=None, content_type="application/json"):
        headers = {}
        if session:
            headers["X-Session-Id"] = session
        if raw is not None:
            body = raw
        elif payload is not None:
            body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        else:
            body = None
        if body is not None:
            headers["Content-Type"] = content_type
        request = urllib.request.Request(variables["baseUrl"] + path, data=body, headers=headers, method=method)
        try:
            with urllib.request.urlopen(request, timeout=25) as response:
                return response.status, json.load(response)
        except urllib.error.HTTPError as response:
            return response.code, json.load(response)

    with tempfile.TemporaryDirectory(prefix="chatnest-http-test-") as temporary:
        temporary = pathlib.Path(temporary)
        classes = temporary / "classes"
        classes.mkdir()
        database = temporary / "data"
        database.mkdir()
        sources = sorted((ROOT / "source/server").rglob("*.java"))
        libraries = str(ROOT / "source/lib/*")
        subprocess.run([JAVAC, "-encoding", "UTF-8", "-cp", libraries, "-d", str(classes)]
                       + [str(path) for path in sources], check=True)
        classpath = str(classes) + ";" + libraries
        processes = []
        with (temporary / "server.log").open("w", encoding="utf-8") as server_log, \
                (temporary / "adapter.log").open("w", encoding="utf-8") as adapter_log:
            try:
                processes.append(subprocess.Popen([
                    JAVA, "-Dfile.encoding=UTF-8", "-Dchatnest.db.dir=" + str(database),
                    "-cp", classpath, "ServerMain"], stdout=server_log, stderr=subprocess.STDOUT))
                deadline = time.monotonic() + 15
                while True:
                    try:
                        with socket.create_connection(("127.0.0.1", 8888), timeout=0.5):
                            break
                    except OSError:
                        if time.monotonic() >= deadline or processes[0].poll() is not None:
                            raise RuntimeError("TCP server did not start")
                        time.sleep(0.1)
                processes.append(subprocess.Popen([
                    JAVA, "-Dfile.encoding=UTF-8", "-Dchatnest.http.port=" + str(http_port),
                    "-cp", classpath, "HttpApiAdapter"], stdout=adapter_log, stderr=subprocess.STDOUT))
                deadline = time.monotonic() + 15
                while True:
                    try:
                        if call("GET", "/api/health")[0] == 200:
                            break
                    except OSError:
                        pass
                    if time.monotonic() >= deadline or processes[1].poll() is not None:
                        raise RuntimeError("HTTP adapter did not start")
                    time.sleep(0.1)

                seen_actions = set()
                checks = 0
                for item in items:
                    request = item["request"]
                    method = request["method"]
                    path = substitute(request["url"]).replace(variables["baseUrl"], "", 1)
                    headers = {h["key"]: substitute(h["value"]) for h in request.get("header", [])}
                    payload = json.loads(substitute(request["body"]["raw"])) if "body" in request else None
                    session = headers.get("X-Session-Id")
                    status, result = call(method, path, payload, session)
                    expected = 201 if method == "POST" and path == "/api/sessions" else 200
                    assert status == expected and result["status"] == "SUCCESS", (item["name"], status, result)
                    if path == "/api/sessions" and method == "POST":
                        key = "sessionAlice" if "Alice" in item["name"] else "sessionBob"
                        variables[key] = result["data"]["sessionId"]
                    if payload:
                        action = payload["action"]
                        seen_actions.add(action)
                        assert result["action"] == action + "_RESPONSE", result
                        data = result.get("data", {})
                        if action == "SEND_PRIVATE_MESSAGE":
                            variables["conversationId"] = data["conversationId"]
                        if action == "SEND_FILE":
                            variables["fileId"] = data["fileId"]
                        if action == "CREATE_GROUP":
                            variables["groupId"] = data["groupId"]
                        if action == "DOWNLOAD_FILE":
                            assert data["base64Data"] == "SGVsbG8=", result
                        if action == "GET_CHAT_HISTORY":
                            assert any("Postman" in m["content"] for m in data["messages"]), result
                        if action == "GET_GROUP_HISTORY":
                            assert data["messages"], result
                    if path == "/api/events" and "Bob" in item["name"]:
                        event_actions = {event["action"] for event in result["data"]["events"]}
                        assert {"RECEIVE_PRIVATE_MESSAGE", "RECEIVE_NOTIFICATION", "RECEIVE_GROUP_MESSAGE", "GROUP_UPDATED"} <= event_actions, result
                        assert result["data"]["droppedCount"] == 0, result
                    if path == "/api/sessions" and method == "DELETE":
                        assert call("GET", "/api/events", session=session)[0] == 401
                    checks += 1
                    print("PASS", item["name"])
                assert len(seen_actions) == 18, seen_actions

                # Adapter error cases and correlation under concurrent HTTP requests.
                assert call("POST", "/api/actions", {"action": "GET_MY_GROUPS"})[0] == 401
                status, result = call("POST", "/api/sessions")
                sid = result["data"]["sessionId"]
                try:
                    status, result = call("POST", "/api/actions", {"action": "GET_MY_GROUPS"}, sid)
                    assert status == 200 and result["action"] == "ERROR" and result["status"] == "FAILED"
                    cases = [
                        ({"action": "MARK_MESSAGES_READ", "data": {}}, 400),
                        ({"action": "get_my_groups"}, 400),
                        ({"action": "NO_SUCH_ACTION"}, 400),
                        ({"action": "GET_CHAT_HISTORY", "data": {"targetUser": "bob", "limit": 1.5}}, 400),
                        ({"action": "LOGIN", "data": {"username": None, "password": "p"}}, 400),
                        ({"action": "GET_USER_LIST", "data": []}, 400),
                    ]
                    for payload, expected in cases:
                        assert call("POST", "/api/actions", payload, sid)[0] == expected
                    assert call("POST", "/api/actions", session=sid, raw=b"{broken")[0] == 400
                    assert call("POST", "/api/actions", session=sid, raw=b"{}", content_type="text/plain")[0] == 415
                    assert call("GET", "/api/actions", session=sid)[0] == 405
                    assert call("GET", "/api/missing")[0] == 404
                    status, result = call("POST", "/api/actions", {"action": "LOGIN", "data": {
                        "username": variables["aliceUsername"], "password": variables["password"]}}, sid)
                    assert result["status"] == "SUCCESS", result
                    assert call("POST", "/api/actions", {"action": "LOGIN", "data": {
                        "username": variables["bobUsername"], "password": variables["password"]}}, sid)[0] == 409
                    def concurrent_request(action):
                        code, result = call("POST", "/api/actions", {"action": action}, sid)
                        assert code == 200 and result["action"] == action + "_RESPONSE", result
                    with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:
                        list(pool.map(concurrent_request, ["GET_USER_LIST", "GET_MY_GROUPS"] * 4))
                    # Reject browser-origin traffic even though the service binds only loopback.
                    req = urllib.request.Request(variables["baseUrl"] + "/api/health", headers={"Origin": "https://example.invalid"})
                    try:
                        urllib.request.urlopen(req)
                        raise AssertionError("Browser request should be blocked")
                    except urllib.error.HTTPError as exc:
                        assert exc.code == 403
                finally:
                    call("DELETE", "/api/sessions", session=sid)
                print("PASS adapter errors, closed sessions, concurrent request correlation, browser-origin rejection")
                print("PASS collection requests:", checks, "unique TCP actions:", len(seen_actions))
                print("NOTE: request bodies/order executed from collection; Postman JS scripts require separate validation.")
            finally:
                for process in reversed(processes):
                    process.terminate()
                for process in reversed(processes):
                    try:
                        process.wait(timeout=5)
                    except subprocess.TimeoutExpired:
                        process.kill()
                        process.wait()


if __name__ == "__main__":
    run()
