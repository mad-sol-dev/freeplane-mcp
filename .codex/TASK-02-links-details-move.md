# TASK 02: Implement Links, Details, and Move Node

## Context

This is a Freeplane MCP server with two components:
- `groovy/FreeplaneHttpBridge.groovy` — HTTP bridge running inside Freeplane's JVM
- `python/server.py` — Python MCP server communicating via HTTP on localhost:8765

Architecture: Python MCP tools → HTTP POST to `/execute` → Groovy `executeCommand` switch → Freeplane API.
All Freeplane API calls already run on EDT via the `/execute` wrapper. Don't add EDT handling yourself.

Read both files fully before making changes. Maintain existing code style.

## What to implement

### 1. Links — get, set, remove

Freeplane's `node.link` API supports URLs, file paths, and inter-node links.
Reference: https://docs.freeplane.org/api/org/freeplane/api/Link.html

**Groovy — add 3 commands:**

```groovy
case "get_link":
    return getLink(params.node_id)
case "set_link":
    return setLink(params.node_id, params.target)
case "remove_link":
    return removeLink(params.node_id)
```

Implementations:
```groovy
def getLink(nodeId) {
    def targetNode = nodeId ? findNodeById(nodeId) : node
    if (!targetNode) return [error: "Node not found: ${nodeId}"]
    return [
        text: targetNode.link.text,     // the URI as string, or null
        uri: targetNode.link.uri?.toString(),
        node_id: targetNode.link.node?.id  // linked node ID if it's a node-link
    ]
}

def setLink(nodeId, target) {
    def targetNode = nodeId ? findNodeById(nodeId) : node
    if (!targetNode) return [error: "Node not found: ${nodeId}"]
    if (!target) return [error: "Link target is required"]

    // If target starts with "ID_", treat as node link
    if (target.startsWith("ID_")) {
        def linkedNode = findNodeById(target)
        if (!linkedNode) return [error: "Target node not found: ${target}"]
        targetNode.link.node = linkedNode
    } else {
        // URL or file path
        targetNode.link.text = target
    }
    return [success: true]
}

def removeLink(nodeId) {
    def targetNode = nodeId ? findNodeById(nodeId) : node
    if (!targetNode) return [error: "Node not found: ${nodeId}"]
    targetNode.link.remove()
    return [success: true]
}
```

**Python — add 3 tools:**

```python
Tool(
    name="get_link",
    description="Get the link (URL, file path, or node reference) from a node",
    inputSchema={
        "type": "object",
        "properties": {
            "node_id": {"type": "string", "description": "Node ID (omit for current)"}
        }
    }
),
Tool(
    name="set_link",
    description="Set a link on a node. Target can be a URL (https://...), file path, or node ID (ID_xxx for inter-node link)",
    inputSchema={
        "type": "object",
        "properties": {
            "node_id": {"type": "string", "description": "Node ID (omit for current)"},
            "target": {"type": "string", "description": "URL, file path, or node ID (ID_xxx)"}
        },
        "required": ["target"]
    }
),
Tool(
    name="remove_link",
    description="Remove the link from a node",
    inputSchema={
        "type": "object",
        "properties": {
            "node_id": {"type": "string", "description": "Node ID (omit for current)"}
        }
    }
)
```

Command map entries: `"get_link": "get_link"`, `"set_link": "set_link"`, `"remove_link": "remove_link"`

### 2. Details — get, set

Freeplane's `node.details` is a secondary text field displayed below the main node text.
It's the ideal place for AI to add context without cluttering the tree.

**Groovy — add 2 commands:**

```groovy
case "get_details":
    return getDetails(params.node_id)
case "set_details":
    return setDetails(params.node_id, params.text)
```

Implementations:
```groovy
def getDetails(nodeId) {
    def targetNode = nodeId ? findNodeById(nodeId) : node
    if (!targetNode) return [error: "Node not found: ${nodeId}"]
    return [
        details: targetNode.details?.text ?: "",
        details_content_type: targetNode.detailsContentType
    ]
}

def setDetails(nodeId, text) {
    def targetNode = nodeId ? findNodeById(nodeId) : node
    if (!targetNode) return [error: "Node not found: ${nodeId}"]
    targetNode.details = text  // null clears details
    return [success: true]
}
```

**Python — add 2 tools:**

```python
Tool(
    name="get_details",
    description="Get the details text (secondary text shown below node text) of a node",
    inputSchema={
        "type": "object",
        "properties": {
            "node_id": {"type": "string", "description": "Node ID (omit for current)"}
        }
    }
),
Tool(
    name="set_details",
    description="Set or clear the details text of a node. Details appear below the main node text — ideal for supplementary info. Pass null/empty to clear.",
    inputSchema={
        "type": "object",
        "properties": {
            "node_id": {"type": "string", "description": "Node ID (omit for current)"},
            "text": {"type": "string", "description": "Details text (supports HTML). Omit or null to clear."}
        }
    }
)
```

Command map: `"get_details": "get_details"`, `"set_details": "set_details"`

### 3. Move Node

Freeplane's `node.moveTo(parentNode)` and `node.moveTo(parentNode, position)` allow reorganizing the tree.

**Groovy — add command:**

```groovy
case "move_node":
    return moveNode(params.node_id, params.target_parent_id, params.position)
```

Implementation:
```groovy
def moveNode(nodeId, targetParentId, position) {
    if (!nodeId) return [error: "node_id is required"]
    if (!targetParentId) return [error: "target_parent_id is required"]

    def sourceNode = findNodeById(nodeId)
    def targetParent = findNodeById(targetParentId)

    if (!sourceNode) return [error: "Source node not found: ${nodeId}"]
    if (!targetParent) return [error: "Target parent not found: ${targetParentId}"]
    if (sourceNode.isRoot()) return [error: "Cannot move root node"]

    // Check that target is not a descendant of source (would create cycle)
    if (targetParent.isDescendantOf(sourceNode)) {
        return [error: "Cannot move node into its own subtree"]
    }

    if (position != null) {
        sourceNode.moveTo(targetParent, position as int)
    } else {
        sourceNode.moveTo(targetParent)
    }
    return [success: true, node: getNodeInfo(sourceNode)]
}
```

**Python — add tool:**

```python
Tool(
    name="move_node",
    description="Move a node to a different parent. Optionally specify position among siblings.",
    inputSchema={
        "type": "object",
        "properties": {
            "node_id": {"type": "string", "description": "ID of the node to move"},
            "target_parent_id": {"type": "string", "description": "ID of the new parent node"},
            "position": {"type": "integer", "description": "Position among siblings (0-based). Omit to append as last child."}
        },
        "required": ["node_id", "target_parent_id"]
    }
)
```

Command map: `"move_node": "move_node"`

## Files to modify

1. `groovy/FreeplaneHttpBridge.groovy`:
   - Add 6 cases to `executeCommand` switch
   - Add 6 implementation functions
   - Add all 6 command names to `getAvailableCommands()`

2. `python/server.py`:
   - Add 6 Tool definitions to `list_tools()`
   - Add 6 entries to `command_map` in `call_tool()`

## Important constraints

- Keep error handling consistent: return `[error: "message"]` maps, never throw
- All `node_id` parameters are optional where it makes sense (default to selected node)
- `move_node` requires both `node_id` and `target_parent_id` — no defaults
- Do NOT modify existing tools or the EDT wrapper
- Maintain existing code style (indentation, naming, patterns)
