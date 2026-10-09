import os
import socket
import threading
import time

import pytest
import uvicorn


@pytest.fixture(scope="session")
def base_url():
    """One real HTTP server for all tests: the FastMCP session manager can only start once per process."""
    # Imported here, not at conftest import time: FastMCP() calls logging.basicConfig(INFO), which
    # must run after pytest installed its log handlers (otherwise httpx INFO logs get captured)
    from jellyfin_mcp import server
    from test_server import SECRET

    os.environ["MCP_SECRET"] = SECRET
    sock = socket.socket()
    sock.bind(("127.0.0.1", 0))
    port = sock.getsockname()[1]
    sock.close()
    config = uvicorn.Config(server.create_app(), host="127.0.0.1", port=port, log_level="warning")
    srv = uvicorn.Server(config)
    t = threading.Thread(target=srv.run, daemon=True)
    t.start()
    for _ in range(100):
        if srv.started:
            break
        time.sleep(0.05)
    yield f"http://127.0.0.1:{port}"
    srv.should_exit = True
    t.join(timeout=5)
