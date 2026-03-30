# Repository Guidelines

## Project Structure & Module Organization
- `groovy/FreeplaneHttpBridge.groovy` is the Freeplane-side HTTP bridge script (runs inside Freeplane).
- `python/server.py` is the MCP server that talks to the bridge over HTTP.
- `python/requirements.txt` lists Python dependencies.
- `README.md` and `QUICKSTART.md` contain user-facing setup and usage docs.

## Build, Test, and Development Commands
- `cd python && pip install -r requirements.txt` installs the Python runtime dependencies.
- `python python/server.py` starts the MCP server locally (expects Freeplane + bridge running).
- `cp groovy/FreeplaneHttpBridge.groovy /path/to/freeplane/scripts/` installs the bridge script into Freeplane.
- `curl http://localhost:8765/status` verifies the bridge is reachable.

There is no separate build step; development is run-from-source.

## Coding Style & Naming Conventions
- Python uses 4-space indentation and type hints (see `python/server.py`).
- Groovy follows standard Groovy/Java conventions; keep methods small and descriptive.
- Prefer explicit names for commands and parameters to match the tool API (e.g., `set_font_formatting`).
- No formatter/linter is configured; keep changes consistent with surrounding code style.

## Testing Guidelines
- There is no automated test suite in this repository.
- When changing behavior, validate manually: start Freeplane and run the bridge script, run `python python/server.py`, then call `curl http://localhost:8765/status` and at least one `/execute` command.
- If you add tests, document how to run them in this file and `README.md`.

## Commit & Pull Request Guidelines
- Commit messages are short and descriptive; the history uses both plain messages and type prefixes like `fix:`.
- Use the format `type: summary` when applicable (e.g., `docs: update README`), otherwise a concise imperative summary is fine.
- PRs should include a brief description of the change and affected area (`groovy/` or `python/`), manual test steps run (or note if not applicable), and any config or environment changes (e.g., `FREEPLANE_BRIDGE_PORT`).

## Security & Configuration Notes
- The bridge listens on `localhost` only; keep it that way unless you add authentication.
- Environment variables are `FREEPLANE_BRIDGE_HOST` (default `localhost`) and `FREEPLANE_BRIDGE_PORT` (default `8765`).
