#!/usr/bin/env python3
"""
Freeplane Application Control MCP Server

An MCP server that controls the Freeplane application in real-time through
the Groovy HTTP Bridge. Requires Freeplane to be running with the bridge script.
"""

import os
import json
import httpx
from typing import Any, Dict

# MCP imports
from mcp.server import Server
from mcp.types import Tool, TextContent
import mcp.server.stdio


# Configuration
BRIDGE_HOST = os.getenv("FREEPLANE_BRIDGE_HOST", "localhost")
BRIDGE_PORT = int(os.getenv("FREEPLANE_BRIDGE_PORT", "8765"))
BRIDGE_URL = f"http://{BRIDGE_HOST}:{BRIDGE_PORT}"

# Initialize MCP server
app = Server("freeplane-app-control")


class FreeplaneClient:
    """Client for communicating with Freeplane via the HTTP Bridge"""

    def __init__(self, base_url: str = BRIDGE_URL):
        self.base_url = base_url
        self.timeout = 10.0

    async def check_connection(self) -> Dict:
        """Check if Freeplane bridge is running"""
        try:
            async with httpx.AsyncClient(timeout=self.timeout) as client:
                response = await client.get(f"{self.base_url}/status")
                return response.json()
        except httpx.HTTPError as e:
            return {
                "error": "Cannot connect to Freeplane bridge",
                "details": str(e),
                "hint": "Make sure Freeplane is running and the FreeplaneHttpBridge.groovy script is executed"
            }

    async def execute(self, command: str, params: Dict | None = None) -> Dict:
        """Execute a command on Freeplane"""
        try:
            async with httpx.AsyncClient(timeout=self.timeout) as client:
                response = await client.post(
                    f"{self.base_url}/execute",
                    json={"command": command, "params": params or {}},
                )
                return response.json()
        except httpx.HTTPError as e:
            return {"error": str(e)}


# Global client instance
freeplane = FreeplaneClient()


@app.list_tools()
async def list_tools() -> list[Tool]:
    """List available tools"""
    return [
        # Connection & Status
        Tool(
            name="check_connection",
            description="Check if Freeplane is running and the bridge is active",
            inputSchema={"type": "object", "properties": {}}
        ),
        Tool(
            name="get_map_info",
            description="Get information about the currently open mind map",
            inputSchema={"type": "object", "properties": {}}
        ),

        # Node Selection & Navigation
        Tool(
            name="get_selected_node",
            description="Get information about the currently selected node",
            inputSchema={"type": "object", "properties": {}}
        ),
        Tool(
            name="select_node",
            description="Select a specific node by its ID",
            inputSchema={
                "type": "object",
                "properties": {
                    "node_id": {"type": "string", "description": "ID of the node to select"}
                },
                "required": ["node_id"]
            }
        ),
        Tool(
            name="navigate_to_parent",
            description="Navigate to the parent of the currently selected node",
            inputSchema={"type": "object", "properties": {}}
        ),
        Tool(
            name="navigate_to_child",
            description="Navigate to a child of the currently selected node",
            inputSchema={
                "type": "object",
                "properties": {
                    "index": {"type": "integer", "description": "Index of the child (0-based)", "default": 0}
                }
            }
        ),

        # Node Creation & Deletion
        Tool(
            name="create_child_node",
            description="Create a new child node under the currently selected node",
            inputSchema={
                "type": "object",
                "properties": {
                    "text": {"type": "string", "description": "Text for the new node"},
                    "position": {"type": "string", "description": "Position: 'left' or 'right'", "enum": ["left", "right"]}
                }
            }
        ),
        Tool(
            name="create_sibling_node",
            description="Create a new sibling node next to the currently selected node",
            inputSchema={
                "type": "object",
                "properties": {
                    "text": {"type": "string", "description": "Text for the new node"},
                    "before": {"type": "boolean", "description": "Insert before (true) or after (false) current node"}
                }
            }
        ),
        Tool(
            name="set_node_text",
            description="Change the text of a node",
            inputSchema={
                "type": "object",
                "properties": {
                    "node_id": {"type": "string", "description": "ID of the node (omit for current)"},
                    "text": {"type": "string", "description": "New text for the node"}
                },
                "required": ["text"]
            }
        ),
        Tool(
            name="delete_node",
            description="Delete a node and all its children",
            inputSchema={
                "type": "object",
                "properties": {
                    "node_id": {"type": "string", "description": "ID of the node to delete"}
                },
                "required": ["node_id"]
            }
        ),

        # Visual Styling
        Tool(
            name="set_node_color",
            description="Set the text color of a node",
            inputSchema={
                "type": "object",
                "properties": {
                    "node_id": {"type": "string", "description": "ID of the node (omit for current)"},
                    "color": {"type": "string", "description": "Color as hex (#FF0000) or name (red)"}
                },
                "required": ["color"]
            }
        ),
        Tool(
            name="set_background_color",
            description="Set the background color of a node",
            inputSchema={
                "type": "object",
                "properties": {
                    "node_id": {"type": "string", "description": "ID of the node (omit for current)"},
                    "color": {"type": "string", "description": "Color as hex (#FF0000) or name (red)"}
                },
                "required": ["color"]
            }
        ),
        Tool(
            name="set_node_style",
            description="Apply a predefined style to a node",
            inputSchema={
                "type": "object",
                "properties": {
                    "node_id": {"type": "string", "description": "ID of the node (omit for current)"},
                    "style": {"type": "string", "description": "Style name (e.g., 'default', 'important')"}
                },
                "required": ["style"]
            }
        ),
        Tool(
            name="set_font_formatting",
            description="Set font formatting (bold, italic, size) for a node",
            inputSchema={
                "type": "object",
                "properties": {
                    "node_id": {"type": "string", "description": "ID of the node (omit for current)"},
                    "bold": {"type": "boolean", "description": "Make text bold"},
                    "italic": {"type": "boolean", "description": "Make text italic"},
                    "size": {"type": "integer", "description": "Font size in points"}
                }
            }
        ),

        # Icons
        Tool(
            name="add_icon",
            description="Add an icon to a node",
            inputSchema={
                "type": "object",
                "properties": {
                    "node_id": {"type": "string", "description": "ID of the node (omit for current)"},
                    "icon": {"type": "string", "description": "Icon name (use list_icons to see available)"}
                },
                "required": ["icon"]
            }
        ),
        Tool(
            name="remove_icon",
            description="Remove an icon from a node",
            inputSchema={
                "type": "object",
                "properties": {
                    "node_id": {"type": "string", "description": "ID of the node (omit for current)"},
                    "icon": {"type": "string", "description": "Icon name to remove"}
                },
                "required": ["icon"]
            }
        ),
        Tool(
            name="list_icons",
            description="List all available icons that can be added to nodes",
            inputSchema={"type": "object", "properties": {}}
        ),

        # Connectors
        Tool(
            name="add_connector",
            description="Add a visual connector (arrow) between two nodes",
            inputSchema={
                "type": "object",
                "properties": {
                    "source_id": {"type": "string", "description": "ID of the source node"},
                    "target_id": {"type": "string", "description": "ID of the target node"},
                    "label": {"type": "string", "description": "Optional label for the connector"}
                },
                "required": ["source_id", "target_id"]
            }
        ),
        Tool(
            name="remove_connector",
            description="Remove a connector between two nodes",
            inputSchema={
                "type": "object",
                "properties": {
                    "source_id": {"type": "string", "description": "ID of the source node"},
                    "target_id": {"type": "string", "description": "ID of the target node"}
                },
                "required": ["source_id", "target_id"]
            }
        ),

        # Attributes
        Tool(
            name="set_node_attribute",
            description="Set a custom attribute (key-value pair) on a node",
            inputSchema={
                "type": "object",
                "properties": {
                    "node_id": {"type": "string", "description": "ID of the node (omit for current)"},
                    "name": {"type": "string", "description": "Attribute name"},
                    "value": {"type": "string", "description": "Attribute value"}
                },
                "required": ["name", "value"]
            }
        ),
        Tool(
            name="get_node_attributes",
            description="Get all attributes of a node",
            inputSchema={
                "type": "object",
                "properties": {
                    "node_id": {"type": "string", "description": "ID of the node (omit for current)"}
                }
            }
        ),

        # Notes
        Tool(
            name="set_node_note",
            description="Set or update the note text for a node",
            inputSchema={
                "type": "object",
                "properties": {
                    "node_id": {"type": "string", "description": "ID of the node (omit for current)"},
                    "text": {"type": "string", "description": "Note text (supports HTML)"}
                },
                "required": ["text"]
            }
        ),
        Tool(
            name="get_node_note",
            description="Get the note text of a node",
            inputSchema={
                "type": "object",
                "properties": {
                    "node_id": {"type": "string", "description": "ID of the node (omit for current)"}
                }
            }
        ),

        # Map Operations
        Tool(
            name="fold_node",
            description="Fold (collapse) a node to hide its children",
            inputSchema={
                "type": "object",
                "properties": {
                    "node_id": {"type": "string", "description": "ID of the node to fold"}
                },
                "required": ["node_id"]
            }
        ),
        Tool(
            name="unfold_node",
            description="Unfold (expand) a node to show its children",
            inputSchema={
                "type": "object",
                "properties": {
                    "node_id": {"type": "string", "description": "ID of the node to unfold"}
                },
                "required": ["node_id"]
            }
        ),
        Tool(
            name="center_on_node",
            description="Center the map view on a specific node",
            inputSchema={
                "type": "object",
                "properties": {
                    "node_id": {"type": "string", "description": "ID of the node to center on"}
                },
                "required": ["node_id"]
            }
        ),

        # Search
        Tool(
            name="find_nodes",
            description="Search for nodes containing specific text",
            inputSchema={
                "type": "object",
                "properties": {
                    "text": {"type": "string", "description": "Text to search for"},
                    "case_sensitive": {"type": "boolean", "description": "Case-sensitive search", "default": False}
                },
                "required": ["text"]
            }
        )
    ]


@app.call_tool()
async def call_tool(name: str, arguments: Any) -> list[TextContent]:
    """Handle tool calls"""
    try:
        # Special case: check connection doesn't need bridge call
        if name == "check_connection":
            result = await freeplane.check_connection()
            return [TextContent(type="text", text=json.dumps(result, indent=2))]

        # Map tool names to commands
        command_map = {
            "get_map_info": "get_map_info",
            "get_selected_node": "get_selected_node",
            "select_node": "select_node",
            "navigate_to_parent": "navigate_to_parent",
            "navigate_to_child": "navigate_to_child",
            "create_child_node": "create_child",
            "create_sibling_node": "create_sibling",
            "set_node_text": "set_node_text",
            "delete_node": "delete_node",
            "set_node_color": "set_node_color",
            "set_background_color": "set_background_color",
            "set_node_style": "set_node_style",
            "add_icon": "add_icon",
            "remove_icon": "remove_icon",
            "list_icons": "list_icons",
            "add_connector": "add_connector",
            "remove_connector": "remove_connector",
            "set_node_attribute": "set_attribute",
            "get_node_attributes": "get_attributes",
            "set_node_note": "set_note",
            "get_node_note": "get_note",
            "fold_node": "fold_node",
            "unfold_node": "unfold_node",
            "center_on_node": "center_on_node",
            "find_nodes": "find_nodes"
        }

        # Handle font formatting specially
        if name == "set_font_formatting":
            results = []
            if "bold" in arguments:
                res = await freeplane.execute("set_font_bold", {
                    "node_id": arguments.get("node_id"),
                    "bold": arguments["bold"]
                })
                results.append(res)

            if "italic" in arguments:
                res = await freeplane.execute("set_font_italic", {
                    "node_id": arguments.get("node_id"),
                    "italic": arguments["italic"]
                })
                results.append(res)

            if "size" in arguments:
                res = await freeplane.execute("set_font_size", {
                    "node_id": arguments.get("node_id"),
                    "size": arguments["size"]
                })
                results.append(res)

            return [TextContent(type="text", text=json.dumps({
                "results": results,
                "success": all(r.get("success") for r in results if "success" in r)
            }, indent=2))]

        # Execute the command
        command = command_map.get(name)
        if not command:
            return [TextContent(type="text", text=json.dumps({
                "error": f"Unknown tool: {name}"
            }, indent=2))]

        result = await freeplane.execute(command, arguments)
        return [TextContent(type="text", text=json.dumps(result, indent=2))]

    except Exception as e:
        return [TextContent(type="text", text=json.dumps({
            "error": str(e),
            "tool": name
        }, indent=2))]


async def main():
    """Run the MCP server"""
    async with mcp.server.stdio.stdio_server() as (read_stream, write_stream):
        await app.run(
            read_stream,
            write_stream,
            app.create_initialization_options()
        )


if __name__ == "__main__":
    import asyncio
    asyncio.run(main())
