FROM python:3.12-slim

WORKDIR /app
COPY pyproject.toml ./
COPY jellyfin_mcp ./jellyfin_mcp
RUN pip install --no-cache-dir . \
    && useradd --system --uid 10001 mcp
USER mcp

ENV PORT=8765
EXPOSE 8765
HEALTHCHECK --interval=60s --timeout=5s \
    CMD python -c "import urllib.request; urllib.request.urlopen('http://127.0.0.1:8765/health', timeout=4)"
CMD ["jellyfin-mcp"]
