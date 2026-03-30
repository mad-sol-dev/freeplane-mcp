# TASK 01: Implement get_subtree and bulk_create_from_outline

## Context

This is a Freeplane MCP server with two components:
- `groovy/FreeplaneHttpBridge.groovy` — HTTP bridge running inside Freeplane's JVM (Groovy script)
- `python/server.py` — Python MCP server communicating with the bridge via HTTP

The bridge has a command dispatch in `executeCommand(String command, Map params)` using a switch statement.
The Python server defines MCP tools in `list_tools()` and maps tool names to bridge commands in `call_tool()`.

All Freeplane API calls in the Groovy bridge MUST run on the EDT (Event Dispatch Thread) — this is already handled by the `/execute` endpoint wrapper using `SwingUtilities.invokeAndWait`. You do NOT need to add EDT handling in your command implementations.

## What to implement

### 1. `get_subtree` — Recursive tree serialization

**Why:** Currently the MCP can only see the single selected node. Without tree context, an AI assistant is blind to the map structure. This is the single most impactful missing feature.

**Groovy bridge — add command `get_subtree`:**

Parameters:
- `node_id` (String, optional) — node to start from; defaults to current selection
- `max_depth` (int, optional) — maximum recursion depth; defaults to -1 (unlimited)
- `include_details` (boolean, optional) — include node details text; defaults to false
- `include_notes` (boolean, optional) — include node notes; defaults to false
- `include_attributes` (boolean, optional) — include node attributes; defaults to false

Returns a recursive JSON structure:
```json
{
  "id": "ID_123",
  "text": "Root",
  "children": [
    {
      "id": "ID_456",
      "text": "Child 1",
      "children": [],
      "child_count": 0
    }
  ],
  "child_count": 1,
  "is_folded": false
}
```

Each node object always includes: `id`, `text`, `children` (array), `child_count`, `is_folded`.
When the respective `include_*` flags are true, also include: `details`, `note`, `attributes`, `icons`, `link` (URL string or null), `style`, `text_color`, `background_color`.

When `max_depth` is reached, omit the `children` array and only show `child_count` so the caller knows there's more below.

Implementation approach:
```groovy
def getSubtree(nodeId, maxDepth, includeDetails, includeNotes, includeAttributes) {
    def targetNode = nodeId ? findNodeById(nodeId) : node
    if (!targetNode) return [error: "Node not found: ${nodeId}"]
    return buildSubtree(targetNode, maxDepth, 0, includeDetails, includeNotes, includeAttributes)
}

def buildSubtree(n, maxDepth, currentDepth, includeDetails, includeNotes, includeAttributes) {
    def result = [
        id: n.id,
        text: n.text,
        child_count: n.children.size(),
        is_folded: n.folded
    ]
    if (includeDetails) result.details = n.details?.text ?: ""
    if (includeNotes) result.note = n.note?.text ?: ""
    if (includeAttributes) {
        def attrs = [:]
        n.attributes.each { attrs[it.name] = it.value }
        result.attributes = attrs
    }
    // Always include icons and link — they're lightweight
    result.icons = n.icons.icons*.toString()
    result.link = n.link.text

    if (maxDepth < 0 || currentDepth < maxDepth) {
        result.children = n.children.collect {
            buildSubtree(it, maxDepth, currentDepth + 1, includeDetails, includeNotes, includeAttributes)
        }
    }
    return result
}
```

Add `"get_subtree"` to the switch in `executeCommand` and to `getAvailableCommands()`.

**Python server — add tool `get_subtree`:**

```python
Tool(
    name="get_subtree",
    description="Get the full subtree structure starting from a node as nested JSON. Essential for understanding map structure. Returns id, text, children recursively. Use max_depth to limit depth for large maps.",
    inputSchema={
        "type": "object",
        "properties": {
            "node_id": {"type": "string", "description": "Starting node ID (omit for current selection)"},
            "max_depth": {"type": "integer", "description": "Max recursion depth (-1 for unlimited)", "default": -1},
            "include_details": {"type": "boolean", "description": "Include node details text", "default": False},
            "include_notes": {"type": "boolean", "description": "Include node notes", "default": False},
            "include_attributes": {"type": "boolean", "description": "Include node attributes", "default": False}
        }
    }
)
```

Add `"get_subtree": "get_subtree"` to `command_map` in `call_tool()`.

### 2. `bulk_create_from_outline` — Create entire branch from text outline

**Why:** Creating nodes one-by-one through individual MCP tool calls is extremely slow (each call = HTTP roundtrip + EDT dispatch). Freeplane's `appendTextOutlineAsBranch(String)` can create an entire branch hierarchy from a tab-indented text outline in a single call.

**Groovy bridge — add command `bulk_create`:**

Parameters:
- `node_id` (String, optional) — parent node; defaults to current selection
- `outline` (String, required) — tab-indented text outline where each level of indentation creates a deeper child node

Example outline:
```
Phase 1
\tRequirements
\t\tFunctional
\t\tNon-functional
\tDesign
Phase 2
\tImplementation
```

Implementation:
```groovy
def bulkCreate(nodeId, outline) {
    def targetNode = nodeId ? findNodeById(nodeId) : node
    if (!targetNode) return [error: "Node not found: ${nodeId}"]
    if (!outline?.trim()) return [error: "Outline text is required"]

    targetNode.appendTextOutlineAsBranch(outline)
    return [
        success: true,
        parent_id: targetNode.id,
        child_count: targetNode.children.size()
    ]
}
```

Add `"bulk_create"` to the switch in `executeCommand` and to `getAvailableCommands()`.

**Python server — add tool `bulk_create_from_outline`:**

```python
Tool(
    name="bulk_create_from_outline",
    description="Create an entire branch hierarchy from a tab-indented text outline in a single operation. Much faster than creating nodes one by one. Each line becomes a node; tab indentation sets depth.",
    inputSchema={
        "type": "object",
        "properties": {
            "node_id": {"type": "string", "description": "Parent node ID (omit for current selection)"},
            "outline": {"type": "string", "description": "Tab-indented text outline. Each line = node, tabs = depth level."}
        },
        "required": ["outline"]
    }
)
```

Add `"bulk_create_from_outline": "bulk_create"` to `command_map`.

## Files to modify

1. `groovy/FreeplaneHttpBridge.groovy` — add switch cases + implementation functions
2. `python/server.py` — add Tool definitions in `list_tools()` + command_map entries in `call_tool()`

## Testing

After implementation, verify:
- `get_subtree` with no arguments returns the full map from the selected node
- `get_subtree` with `max_depth=1` returns only direct children
- `get_subtree` with `include_notes=true` includes notes
- `bulk_create_from_outline` with a 3-level outline creates the correct hierarchy
- Both commands handle missing node_id gracefully (fall back to selected node)
- Both commands return proper error JSON for invalid node IDs

## Important constraints

- Do NOT modify the EDT handling in the `/execute` endpoint — it already wraps all commands
- Do NOT add new HTTP endpoints — use the existing `/execute` command dispatch
- Keep the Groovy code style consistent with existing functions (same patterns, same error handling)
- The Python server uses `httpx` (async) — all bridge calls go through `freeplane.execute(command, params)`
