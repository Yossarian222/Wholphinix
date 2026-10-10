"""Claude agent shared by the chat front-ends (WhatsApp bot, phone app).

A text message goes to the Anthropic Messages API with the MCP tools as Claude tools (a manual
tool-use loop, max MAX_TOOL_ROUNDS model calls); tool calls go through FastMCP.call_tool, i.e.
exactly the code the MCP clients run. Per-conversation history (plain text only) lives in
process memory.

Logging: never message text, replies or API keys at INFO.
"""

from __future__ import annotations

import asyncio
import json
import logging
import time
from collections import deque
from dataclasses import dataclass, field
from typing import Any

log = logging.getLogger("jellyfin_mcp.agent")

DEFAULT_MODEL = "claude-sonnet-5-5"
DEFAULT_EFFORT = "low"  # chat with short tool sequences; raise via CLAUDE_EFFORT if needed
MAX_TOOL_ROUNDS = 6
MAX_TOKENS = 4096
HISTORY_EXCHANGES = 10
HISTORY_TTL_SECONDS = 30 * 60
# Server-side refusal fallback (Claude API, beta): only these models accept fallbacks="default"
FALLBACK_MODELS = {"claude-sonnet-5-5", "claude-opus-5-5", "claude-opus-5", "claude-fable-5-1"}
FALLBACK_BETA = "server-side-fallback-2026-07-01"

ERROR_REPLY = "Prepáč, niečo sa pokazilo a nepodarilo sa mi to vybaviť 😕 Skús to o chvíľu znova."
REFUSAL_REPLY = "Prepáč, s týmto ti nepomôžem."
TOO_LONG_REPLY = "Prepáč, zamotal som sa v tom 😅 Skús to napísať inak alebo po kúskoch."


@dataclass
class Conversation:
    exchanges: deque[tuple[str, str]] = field(default_factory=lambda: deque(maxlen=HISTORY_EXCHANGES))
    last_active: float = 0.0
    lock: asyncio.Lock = field(default_factory=asyncio.Lock)


class Memory:
    """Per-conversation history of (user text, final assistant text), in process memory.

    Only plain text is kept between turns (no tool calls / thinking blocks), so dropping the
    oldest exchange never edits a replayed thinking block.
    """

    def __init__(self, ttl: float = HISTORY_TTL_SECONDS, clock=time.monotonic) -> None:
        self.ttl = ttl
        self.clock = clock
        self._convs: dict[str, Conversation] = {}

    def get(self, key: str) -> Conversation:
        now = self.clock()
        # Drop expired conversations (also of other keys) so memory doesn't grow
        for k in [k for k, c in self._convs.items() if now - c.last_active > self.ttl and not c.lock.locked()]:
            del self._convs[k]
        conv = self._convs.get(key)
        if conv is None:
            conv = self._convs[key] = Conversation(last_active=now)
        return conv

    def touch(self, conv: Conversation) -> None:
        conv.last_active = self.clock()

    def forget(self, key: str) -> None:
        conv = self._convs.get(key)
        if conv is not None and not conv.lock.locked():
            del self._convs[key]


def _block_get(block: Any, name: str, default: Any = None) -> Any:
    if isinstance(block, dict):
        return block.get(name, default)
    return getattr(block, name, default)


def tool_result_text(result: Any) -> str:
    """FastMCP.call_tool result -> text for a tool_result block."""
    if isinstance(result, tuple) and len(result) == 2 and result[1] is not None:
        return json.dumps(result[1], ensure_ascii=False)
    if isinstance(result, dict):
        return json.dumps(result, ensure_ascii=False)
    blocks = result[0] if isinstance(result, tuple) else result
    return "\n".join(str(_block_get(b, "text", "")) for b in blocks or []) or "{}"


class ChatAgent:
    """Claude with the MCP tools; `answer(key, text)` keeps a short history per key."""

    label = "Agent"  # log prefix of the front-end

    def __init__(
        self,
        mcp: Any,
        system_prompt: str,
        *,
        api_key: str,
        model: str = DEFAULT_MODEL,
        effort: str = DEFAULT_EFFORT,
        anthropic_client: Any | None = None,
    ) -> None:
        self.mcp = mcp
        self.system_prompt = system_prompt
        self.model = model
        self.effort = effort
        if anthropic_client is None:
            from anthropic import AsyncAnthropic

            anthropic_client = AsyncAnthropic(api_key=api_key)
        self.anthropic = anthropic_client
        self.memory = Memory()
        self._tools: list[dict[str, Any]] | None = None

    async def tool_definitions(self) -> list[dict[str, Any]]:
        """The MCP tools as Claude tool definitions (stable order, so the cached prefix stays the same)."""
        if self._tools is None:
            tools = sorted(await self.mcp.list_tools(), key=lambda t: t.name)
            self._tools = [
                {"name": t.name, "description": t.description or "", "input_schema": t.inputSchema}
                for t in tools
            ]
        return self._tools

    def _request_params(self, tools: list[dict[str, Any]], messages: list[dict[str, Any]]) -> dict[str, Any]:
        params: dict[str, Any] = {
            "model": self.model,
            "max_tokens": MAX_TOKENS,
            # Render order is tools -> system -> messages: the breakpoint on the system block
            # caches tools + system; the top-level one caches the growing tool-loop messages.
            "system": [{"type": "text", "text": self.system_prompt, "cache_control": {"type": "ephemeral"}}],
            "tools": tools,
            "messages": messages,
            "cache_control": {"type": "ephemeral"},
            "output_config": {"effort": self.effort},
        }
        if self.model in FALLBACK_MODELS:
            params["betas"] = [FALLBACK_BETA]
            params["fallbacks"] = "default"
        return params

    async def _run_tool(self, name: str, args: Any) -> tuple[str, bool]:
        try:
            result = await self.mcp.call_tool(name, args if isinstance(args, dict) else {})
            return tool_result_text(result), False
        except Exception as ex:  # tool errors go back to Claude, which explains them
            log.info("%s: tool %s failed: %s", self.label, name, type(ex).__name__)
            return f"Error: {ex}", True

    async def run_agent(self, history: list[tuple[str, str]], text: str) -> str:
        import anthropic

        tools = await self.tool_definitions()
        messages: list[dict[str, Any]] = []
        for user_text, assistant_text in history:
            messages.append({"role": "user", "content": user_text})
            messages.append({"role": "assistant", "content": assistant_text})
        messages.append({"role": "user", "content": text})

        for _ in range(MAX_TOOL_ROUNDS):
            try:
                response = await self.anthropic.beta.messages.create(**self._request_params(tools, messages))
            except anthropic.RateLimitError:
                log.warning("%s: Claude rate limited", self.label)
                return ERROR_REPLY
            except anthropic.APIStatusError as ex:
                # The API's message says what is wrong (no secrets in it), e.g. low credit or a bad parameter
                log.error("%s: Claude API error %s: %s", self.label, ex.status_code, str(ex.message)[:300])
                return ERROR_REPLY
            except anthropic.APIConnectionError:
                log.error("%s: cannot reach the Claude API", self.label)
                return ERROR_REPLY

            stop = response.stop_reason
            if stop == "refusal":
                return REFUSAL_REPLY
            if stop in ("tool_use", "pause_turn"):
                # Append the whole content unchanged (thinking blocks included): append-only
                messages.append({"role": "assistant", "content": response.content})
                if stop == "pause_turn":
                    continue
                results = []
                # Sequentially: TV commands depend on order (play, then set_audio)
                for block in response.content:
                    if _block_get(block, "type") != "tool_use":
                        continue
                    name = _block_get(block, "name")
                    log.info("%s: tool %s", self.label, name)
                    content, is_error = await self._run_tool(name, _block_get(block, "input"))
                    result = {"type": "tool_result", "tool_use_id": _block_get(block, "id"), "content": content}
                    if is_error:
                        result["is_error"] = True
                    results.append(result)
                messages.append({"role": "user", "content": results})
                continue
            reply = "\n".join(
                _block_get(b, "text") for b in response.content if _block_get(b, "type") == "text"
            ).strip()
            return reply or "👍"
        log.warning("%s: gave up after %d tool rounds", self.label, MAX_TOOL_ROUNDS)
        return TOO_LONG_REPLY

    async def answer(self, key: str, text: str) -> str:
        conv = self.memory.get(key)
        async with conv.lock:  # one turn at a time per conversation, in order
            try:
                reply = await self.run_agent(list(conv.exchanges), text)
            except Exception:
                log.exception("%s: agent failed", self.label)
                return ERROR_REPLY
            if reply not in (ERROR_REPLY, TOO_LONG_REPLY):
                conv.exchanges.append((text, reply))
            self.memory.touch(conv)
            return reply
