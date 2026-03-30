# TASK 03: Map Operations, Undo, Sort, and Font Name

## Context

This is a Freeplane MCP server with two components:
- `groovy/FreeplaneHttpBridge.groovy` — HTTP bridge running inside Freeplane's JVM
- `python/server.py` — Python MCP server communicating via HTTP on localhost:8765

Architecture: Python MCP tools → HTTP POST to `/execute` → Groovy `executeCommand` switch → Freeplane API.
All Freeplane API calls already run on EDT via the `/execute` wrapper.

Read both files fully before making changes. Maintain existing code style.

## What to implement

### 1. save_map

Save the current mind map to disk. Essential safety net for AI-driven modifications.

**Groovy:**
```groovy
case "save_map":
    return saveMap()
```

```groovy
def saveMap() {
    def map = node.map
    if (!map.file) {
        return [error: "Map has no file path (never saved). Use Freeplane UI to save first."]
    }
    def saved = map.save(false)  // false = no interaction/dialog
    return [success: saved, file: map.file?.path]
}
```

**Python:**
```python
Tool(
    name="save_map",
    description="Save the current mind map to disk. Map must have been saved at least once before (has a file path).",
    inputSchema={"type": "object", "properties": {}}
)
```

Command map: `"save_map": "save_map"`

### 2. undo / redo

Freeplane supports undo via the Controller object `c`. This is critical as a safety net when AI makes mistakes.

**Groovy:**
```groovy
case "undo":
    return doUndo()
case "redo":
    return doRedo()
```

```groovy
def doUndo() {
    try {
        c.undo()
        return [success: true]
    } catch (Exception e) {
        return [error: "Nothing to undo or undo failed: ${e.message}"]
    }
}

def doRedo() {
    try {
        c.redo()
        return [success: true]
    } catch (Exception e) {
        return [error: "Nothing to redo or redo failed: ${e.message}"]
    }
}
```

**Python:**
```python
Tool(
    name="undo",
    description="Undo the last operation in Freeplane. Use as safety net after modifications.",
    inputSchema={"type": "object", "properties": {}}
),
Tool(
    name="redo",
    description="Redo the last undone operation in Freeplane.",
    inputSchema={"type": "object", "properties": {}}
)
```

Command map: `"undo": "undo"`, `"redo": "redo"`

### 3. sort_children

Sort child nodes of a given node alphabetically or reverse-alphabetically.
Uses Freeplane 1.13 API: `node.sortChildrenBy { it.text }`.

**Groovy:**
```groovy
case "sort_children":
    return sortChildren(params.node_id, params.reverse)
```

```groovy
def sortChildren(nodeId, reverse) {
    def targetNode = nodeId ? findNodeById(nodeId) : node
    if (!targetNode) return [error: "Node not found: ${nodeId}"]
    if (targetNode.children.size() == 0) return [error: "Node has no children to sort"]

    if (reverse) {
        targetNode.sortChildrenBy { -(it.text?.toLowerCase() ?: "") }
    } else {
        targetNode.sortChildrenBy { it.text?.toLowerCase() ?: "" }
    }
    return [success: true, child_count: targetNode.children.size()]
}
```

Note: The reverse sort via negation only works for numbers. For strings, use a different approach:
```groovy
def sortChildren(nodeId, reverse) {
    def targetNode = nodeId ? findNodeById(nodeId) : node
    if (!targetNode) return [error: "Node not found: ${nodeId}"]
    if (targetNode.children.size() == 0) return [error: "Node has no children to sort"]

    // sortChildrenBy expects a Comparable return value
    // For reverse alphabetical, we can't negate strings, so we sort normally
    // then reverse by re-sorting with a wrapper
    targetNode.sortChildrenBy { it.text?.toLowerCase() ?: "" }

    if (reverse) {
        // Reverse by moving each child to position 0
        def children = targetNode.children.collect { it }
        children.each { child ->
            child.moveTo(targetNode, 0)
        }
    }
    return [success: true, child_count: targetNode.children.size()]
}
```

**Python:**
```python
Tool(
    name="sort_children",
    description="Sort child nodes of a node alphabetically by text.",
    inputSchema={
        "type": "object",
        "properties": {
            "node_id": {"type": "string", "description": "Node whose children to sort (omit for current)"},
            "reverse": {"type": "boolean", "description": "Sort in reverse (Z-A) order", "default": False}
        }
    }
)
```

Command map: `"sort_children": "sort_children"`

### 4. set_font_name

The existing MCP supports bold/italic/size but NOT font name. Add it.

**Groovy:**
```groovy
case "set_font_name":
    return setFontName(params.node_id, params.name)
```

```groovy
def setFontName(nodeId, fontName) {
    def targetNode = nodeId ? findNodeById(nodeId) : node
    if (!targetNode) return [error: "Node not found: ${nodeId}"]
    targetNode.style.font.name = fontName
    return [success: true]
}
```

**Python:**
```python
Tool(
    name="set_font_name",
    description="Set the font family of a node (e.g., 'Arial', 'Courier New', 'SansSerif')",
    inputSchema={
        "type": "object",
        "properties": {
            "node_id": {"type": "string", "description": "Node ID (omit for current)"},
            "name": {"type": "string", "description": "Font family name"}
        },
        "required": ["name"]
    }
)
```

Command map: `"set_font_name": "set_font_name"`

### 5. set_cloud

Clouds are visual grouping elements around a node and its children.

**Groovy:**
```groovy
case "set_cloud":
    return setCloud(params.node_id, params.color, params.shape)
case "remove_cloud":
    return removeCloud(params.node_id)
```

```groovy
def setCloud(nodeId, colorStr, shape) {
    def targetNode = nodeId ? findNodeById(nodeId) : node
    if (!targetNode) return [error: "Node not found: ${nodeId}"]

    targetNode.cloud.enabled = true
    if (colorStr) {
        targetNode.cloud.colorCode = colorStr  // e.g. "#ff0000"
    }
    if (shape) {
        targetNode.cloud.shape = shape  // ARC, STAR, RECT, ROUND_RECT
    }
    return [success: true]
}

def removeCloud(nodeId) {
    def targetNode = nodeId ? findNodeById(nodeId) : node
    if (!targetNode) return [error: "Node not found: ${nodeId}"]
    targetNode.cloud.enabled = false
    return [success: true]
}
```

**Python:**
```python
Tool(
    name="set_cloud",
    description="Add a cloud (visual grouping) around a node and its children",
    inputSchema={
        "type": "object",
        "properties": {
            "node_id": {"type": "string", "description": "Node ID (omit for current)"},
            "color": {"type": "string", "description": "Cloud color as hex code (e.g., '#3399ff')"},
            "shape": {"type": "string", "description": "Cloud shape", "enum": ["ARC", "STAR", "RECT", "ROUND_RECT"]}
        }
    }
),
Tool(
    name="remove_cloud",
    description="Remove the cloud from a node",
    inputSchema={
        "type": "object",
        "properties": {
            "node_id": {"type": "string", "description": "Node ID (omit for current)"}
        }
    }
)
```

Command map: `"set_cloud": "set_cloud"`, `"remove_cloud": "remove_cloud"`

### 6. set_alias / get_alias

Node aliases enable path-based node addressing — very powerful for structured maps.

**Groovy:**
```groovy
case "set_alias":
    return setAlias(params.node_id, params.alias)
case "get_alias":
    return getAlias(params.node_id)
```

```groovy
def setAlias(nodeId, alias) {
    def targetNode = nodeId ? findNodeById(nodeId) : node
    if (!targetNode) return [error: "Node not found: ${nodeId}"]
    targetNode.alias = alias ?: ""  // empty string clears alias
    return [success: true]
}

def getAlias(nodeId) {
    def targetNode = nodeId ? findNodeById(nodeId) : node
    if (!targetNode) return [error: "Node not found: ${nodeId}"]
    return [alias: targetNode.alias ?: ""]
}
```

**Python:**
```python
Tool(
    name="set_alias",
    description="Set or clear a node's alias. Aliases enable path-based addressing (node.at('~aliasName')). Pass empty string to clear.",
    inputSchema={
        "type": "object",
        "properties": {
            "node_id": {"type": "string", "description": "Node ID (omit for current)"},
            "alias": {"type": "string", "description": "Alias string (empty to clear)"}
        },
        "required": ["alias"]
    }
),
Tool(
    name="get_alias",
    description="Get a node's alias",
    inputSchema={
        "type": "object",
        "properties": {
            "node_id": {"type": "string", "description": "Node ID (omit for current)"}
        }
    }
)
```

Command map: `"set_alias": "set_alias"`, `"get_alias": "get_alias"`

## Files to modify

1. `groovy/FreeplaneHttpBridge.groovy`:
   - Add 9 cases to `executeCommand` switch
   - Add 9 implementation functions
   - Add all command names to `getAvailableCommands()`

2. `python/server.py`:
   - Add 9 Tool definitions to `list_tools()`
   - Add 9 entries to `command_map` in `call_tool()`

## Important constraints

- Return `[error: "message"]` maps for errors, never throw exceptions
- Optional `node_id` defaults to current selection (use `nodeId ? findNodeById(nodeId) : node`)
- Do NOT modify existing tools or the EDT wrapper
- `c.undo()` and `c.redo()` are Controller methods — the bridge already has `c` in scope
- Cloud shape values are enum strings: ARC, STAR, RECT, ROUND_RECT
- Test `save_map` on a map that has a file path (not a new unsaved map)
- The `sortChildrenBy` method exists since Freeplane 1.7+ and takes a closure returning a Comparable
