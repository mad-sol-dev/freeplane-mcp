/**
 * Freeplane HTTP Bridge Server
 *
 * This Groovy script runs inside Freeplane and provides an HTTP API
 * for external programs to control the Freeplane application.
 *
 * To use:
 * 1. Copy this file to your Freeplane scripts directory
 * 2. Run it from Freeplane: Tools > Scripts > FreeplaneHttpBridge
 * 3. The server will start on http://localhost:8765
 *
 * API Endpoints:
 * - POST /execute - Execute Freeplane commands
 * - GET /status - Check if server is running
 * - POST /stop - Stop the server
 */

import com.sun.net.httpserver.*
import groovy.json.JsonBuilder
import groovy.json.JsonSlurper
import java.awt.Color
import javax.swing.SwingUtilities
import java.util.concurrent.atomic.AtomicReference

// Configuration
final int PORT = 8765
final String HOST = "localhost"

// Global server reference (use binding so re-runs can find the previous instance)
if (!binding.hasVariable('_fpBridgeServer')) {
    binding._fpBridgeServer = null
}

// Start the HTTP server
def startServer() {
    // Stop previous instance if still running
    if (binding._fpBridgeServer != null) {
        try {
            binding._fpBridgeServer.stop(0)
            c.statusInfo = "HTTP Bridge: stopped previous instance"
        } catch (Exception ignore) {}
        binding._fpBridgeServer = null
    }

    try {
        def httpServer = HttpServer.create(new InetSocketAddress(HOST, PORT), 0)

        // Status endpoint
        httpServer.createContext("/status") { HttpExchange http ->
            handleRequest(http) {
                def resultRef = new AtomicReference()
                SwingUtilities.invokeAndWait {
                    resultRef.set([
                        status: "running",
                        freeplane_version: c.freeplaneVersion,
                        map_title: node.map.root.text,
                        current_node: node.id
                    ])
                }
                resultRef.get()
            }
        }

        // Execute command endpoint
        httpServer.createContext("/execute") { HttpExchange http ->
            handleRequest(http) {
                if (http.requestMethod != "POST") {
                    return [error: "Method not allowed", status: 405]
                }

                def json = new JsonSlurper().parse(http.requestBody)
                def command = json.command
                def params = json.params ?: [:]

                // Freeplane API is Swing-based — must run on Event Dispatch Thread
                def resultRef = new AtomicReference()
                def errorRef = new AtomicReference()
                SwingUtilities.invokeAndWait {
                    try {
                        resultRef.set(executeCommand(command, params))
                    } catch (Exception edtEx) {
                        errorRef.set(edtEx)
                    }
                }
                if (errorRef.get()) {
                    throw errorRef.get()
                }
                resultRef.get()
            }
        }

        // Stop server endpoint
        httpServer.createContext("/stop") { HttpExchange http ->
            handleRequest(http) {
                stopServer()
                [status: "stopping"]
            }
        }

        httpServer.start()
        binding._fpBridgeServer = httpServer

        ui.informationMessage("""
Freeplane HTTP Bridge Server Started!

Server: http://${HOST}:${PORT}
Status: http://${HOST}:${PORT}/status

The server is now accepting commands from external programs.
""")

        c.statusInfo = "HTTP Bridge running on port ${PORT}"

    } catch (Exception e) {
        ui.errorMessage("Failed to start HTTP Bridge: ${e.message}")
    }
}

// Stop the server
def stopServer() {
    if (binding._fpBridgeServer) {
        binding._fpBridgeServer.stop(0)
        binding._fpBridgeServer = null
        c.statusInfo = "HTTP Bridge stopped"
        ui.informationMessage("HTTP Bridge Server Stopped")
    }
}

// Handle HTTP requests with error handling
def handleRequest(HttpExchange http, Closure action) {
    def response
    def statusCode = 200

    try {
        // No CORS headers — bridge is localhost-only, accessed by local MCP server.
        // Browser-based cross-origin requests are intentionally blocked.
        http.responseHeaders.add("Content-Type", "application/json")

        def result = action()

        if (result?.status) {
            statusCode = result.status
        }

        response = new JsonBuilder(result).toString()

    } catch (Exception e) {
        statusCode = 500
        response = new JsonBuilder([
            error: e.message,
            stackTrace: e.stackTrace.take(5)*.toString()
        ]).toString()
    }

    def bytes = response.bytes
    http.sendResponseHeaders(statusCode, bytes.length)
    http.responseBody.write(bytes)
    http.responseBody.close()
}

// Execute Freeplane commands
def executeCommand(String command, Map params) {
    switch (command) {
        // Node Selection & Navigation
        case "select_node":
            return selectNode(params.node_id)

        case "get_selected_node":
            return getSelectedNode()

        case "get_root_node":
            return getNodeInfo(node.map.root)

        case "navigate_to_parent":
            return navigateToParent()

        case "navigate_to_child":
            return navigateToChild(params.index ?: 0)

        // Node Manipulation
        case "create_child":
            return createChild(params.text, params.position)

        case "create_sibling":
            return createSibling(params.text, params.before)

        case "set_node_text":
            return setNodeText(params.node_id, params.text)

        case "delete_node":
            return deleteNode(params.node_id)

        // Node Styling
        case "set_node_color":
            return setNodeColor(params.node_id, params.color)

        case "set_background_color":
            return setBackgroundColor(params.node_id, params.color)

        case "set_node_style":
            return setNodeStyle(params.node_id, params.style)

        case "set_font_bold":
            return setFontBold(params.node_id, params.bold)

        case "set_font_italic":
            return setFontItalic(params.node_id, params.italic)

        case "set_font_size":
            return setFontSize(params.node_id, params.size)

        // Icons
        case "add_icon":
            return addIcon(params.node_id, params.icon)

        case "remove_icon":
            return removeIcon(params.node_id, params.icon)

        case "remove_all_icons":
            return removeAllIcons(params.node_id)

        case "list_icons":
            return listAvailableIcons()

        // Connectors
        case "add_connector":
            return addConnector(params.source_id, params.target_id, params.label)

        case "remove_connector":
            return removeConnector(params.source_id, params.target_id)

        // Attributes
        case "set_attribute":
            return setAttribute(params.node_id, params.name, params.value)

        case "get_attributes":
            return getAttributes(params.node_id)

        case "remove_attribute":
            return removeAttribute(params.node_id, params.name)

        // Notes
        case "set_note":
            return setNote(params.node_id, params.text)

        case "get_note":
            return getNote(params.node_id)

        // Map Operations
        case "get_map_info":
            return getMapInfo()

        case "center_on_node":
            return centerOnNode(params.node_id)

        case "fold_node":
            return foldNode(params.node_id)

        case "unfold_node":
            return unfoldNode(params.node_id)

        case "get_subtree":
            return getSubtree(
                params.node_id,
                params.max_depth,
                params.include_details,
                params.include_notes,
                params.include_attributes
            )

        case "bulk_create":
            return bulkCreate(params.node_id, params.outline)

        case "get_link":
            return getLink(params.node_id)

        case "set_link":
            return setLink(params.node_id, params.target)

        case "remove_link":
            return removeLink(params.node_id)

        case "get_details":
            return getDetails(params.node_id)

        case "set_details":
            return setDetails(params.node_id, params.text)

        case "move_node":
            return moveNode(params.node_id, params.target_parent_id, params.position)

        // Search
        case "find_nodes":
            return findNodes(params.text, params.case_sensitive)

        default:
            return [error: "Unknown command: ${command}", available_commands: getAvailableCommands()]
    }
}

// Command Implementations

def selectNode(nodeId) {
    def targetNode = findNodeById(nodeId)
    if (!targetNode) {
        return [error: "Node not found: ${nodeId}"]
    }
    c.select(targetNode)
    return [success: true, node: getNodeInfo(targetNode)]
}

def getSelectedNode() {
    return [node: getNodeInfo(node)]
}

def navigateToParent() {
    if (node.parent) {
        c.select(node.parent)
        return [success: true, node: getNodeInfo(node.parent)]
    }
    return [error: "Node has no parent"]
}

def navigateToChild(int index) {
    def children = node.children
    if (index < 0 || index >= children.size()) {
        return [error: "Invalid child index: ${index}", child_count: children.size()]
    }
    def child = children[index]
    c.select(child)
    return [success: true, node: getNodeInfo(child)]
}

def createChild(text, position) {
    def child = node.createChild()
    child.text = text ?: "New Node"
    if (position == "left") {
        child.left = true
    }
    return [success: true, node: getNodeInfo(child)]
}

def createSibling(text, before) {
    if (!node.parent) {
        return [error: "Cannot create sibling of root node"]
    }
    def sibling = before ? node.parent.createChild(node.parent.getChildPosition(node)) :
                          node.parent.createChild(node.parent.getChildPosition(node) + 1)
    sibling.text = text ?: "New Node"
    return [success: true, node: getNodeInfo(sibling)]
}

def setNodeText(nodeId, text) {
    def targetNode = nodeId ? findNodeById(nodeId) : node
    if (!targetNode) {
        return [error: "Node not found: ${nodeId}"]
    }
    targetNode.text = text
    return [success: true, node: getNodeInfo(targetNode)]
}

def deleteNode(nodeId) {
    def targetNode = findNodeById(nodeId)
    if (!targetNode) {
        return [error: "Node not found: ${nodeId}"]
    }
    if (targetNode.isRoot()) {
        return [error: "Cannot delete root node"]
    }
    targetNode.delete()
    return [success: true]
}

def setNodeColor(nodeId, colorStr) {
    def targetNode = nodeId ? findNodeById(nodeId) : node
    if (!targetNode) {
        return [error: "Node not found: ${nodeId}"]
    }
    targetNode.style.textColor = parseColor(colorStr)
    return [success: true]
}

def setBackgroundColor(nodeId, colorStr) {
    def targetNode = nodeId ? findNodeById(nodeId) : node
    if (!targetNode) {
        return [error: "Node not found: ${nodeId}"]
    }
    targetNode.style.backgroundColor = parseColor(colorStr)
    return [success: true]
}

def setNodeStyle(nodeId, styleName) {
    def targetNode = nodeId ? findNodeById(nodeId) : node
    if (!targetNode) {
        return [error: "Node not found: ${nodeId}"]
    }
    targetNode.style.name = styleName
    return [success: true]
}

def setFontBold(nodeId, bold) {
    def targetNode = nodeId ? findNodeById(nodeId) : node
    if (!targetNode) {
        return [error: "Node not found: ${nodeId}"]
    }
    targetNode.style.font.bold = bold
    return [success: true]
}

def setFontItalic(nodeId, italic) {
    def targetNode = nodeId ? findNodeById(nodeId) : node
    if (!targetNode) {
        return [error: "Node not found: ${nodeId}"]
    }
    targetNode.style.font.italic = italic
    return [success: true]
}

def setFontSize(nodeId, size) {
    def targetNode = nodeId ? findNodeById(nodeId) : node
    if (!targetNode) {
        return [error: "Node not found: ${nodeId}"]
    }
    targetNode.style.font.size = size
    return [success: true]
}

def addIcon(nodeId, iconName) {
    def targetNode = nodeId ? findNodeById(nodeId) : node
    if (!targetNode) {
        return [error: "Node not found: ${nodeId}"]
    }
    targetNode.icons.add(iconName)
    return [success: true, icons: targetNode.icons.icons*.toString()]
}

def removeIcon(nodeId, iconName) {
    def targetNode = nodeId ? findNodeById(nodeId) : node
    if (!targetNode) {
        return [error: "Node not found: ${nodeId}"]
    }
    targetNode.icons.remove(iconName)
    return [success: true, icons: targetNode.icons.icons*.toString()]
}

def removeAllIcons(nodeId) {
    def targetNode = nodeId ? findNodeById(nodeId) : node
    if (!targetNode) {
        return [error: "Node not found: ${nodeId}"]
    }
    targetNode.icons.clear()
    return [success: true]
}

def listAvailableIcons() {
    return [icons: c.iconNames]
}

def addConnector(sourceId, targetId, label) {
    def sourceNode = findNodeById(sourceId)
    def targetNode = findNodeById(targetId)

    if (!sourceNode || !targetNode) {
        return [error: "Source or target node not found"]
    }

    def connector = sourceNode.addConnectorTo(targetNode)
    if (label) {
        connector.setMiddleLabel(label)
    }
    return [success: true]
}

def removeConnector(sourceId, targetId) {
    def sourceNode = findNodeById(sourceId)
    def targetNode = findNodeById(targetId)

    if (!sourceNode || !targetNode) {
        return [error: "Source or target node not found"]
    }

    sourceNode.connectorsOut.find { it.target == targetNode }?.remove()
    return [success: true]
}

def setAttribute(nodeId, name, value) {
    def targetNode = nodeId ? findNodeById(nodeId) : node
    if (!targetNode) {
        return [error: "Node not found: ${nodeId}"]
    }
    targetNode.attributes.set(name, value)
    return [success: true]
}

def getAttributes(nodeId) {
    def targetNode = nodeId ? findNodeById(nodeId) : node
    if (!targetNode) {
        return [error: "Node not found: ${nodeId}"]
    }
    def attrs = [:]
    targetNode.attributes.each { attrs[it.name] = it.value }
    return [attributes: attrs]
}

def removeAttribute(nodeId, name) {
    def targetNode = nodeId ? findNodeById(nodeId) : node
    if (!targetNode) {
        return [error: "Node not found: ${nodeId}"]
    }
    targetNode.attributes.remove(name)
    return [success: true]
}

def setNote(nodeId, text) {
    def targetNode = nodeId ? findNodeById(nodeId) : node
    if (!targetNode) {
        return [error: "Node not found: ${nodeId}"]
    }
    targetNode.note = text
    return [success: true]
}

def getNote(nodeId) {
    def targetNode = nodeId ? findNodeById(nodeId) : node
    if (!targetNode) {
        return [error: "Node not found: ${nodeId}"]
    }
    return [note: targetNode.note?.text ?: ""]
}

def getMapInfo() {
    def root = node.map.root
    return [
        title: root.text,
        file: node.map.file?.path,
        node_count: countAllNodes(root),
        root_id: root.id
    ]
}

def centerOnNode(nodeId) {
    def targetNode = findNodeById(nodeId)
    if (!targetNode) {
        return [error: "Node not found: ${nodeId}"]
    }
    c.centerOnNode(targetNode)
    return [success: true]
}

def foldNode(nodeId) {
    def targetNode = findNodeById(nodeId)
    if (!targetNode) {
        return [error: "Node not found: ${nodeId}"]
    }
    targetNode.folded = true
    return [success: true]
}

def unfoldNode(nodeId) {
    def targetNode = findNodeById(nodeId)
    if (!targetNode) {
        return [error: "Node not found: ${nodeId}"]
    }
    targetNode.folded = false
    return [success: true]
}

def findNodes(searchText, caseSensitive) {
    def search = caseSensitive ? searchText : searchText.toLowerCase()

    def results = node.map.root.findAll().findAll { n ->
        def nodeText = caseSensitive ? n.text : n.text?.toLowerCase() ?: ""
        nodeText.contains(search)
    }.collect { getNodeInfo(it) }

    return [results: results, count: results.size()]
}

def getSubtree(nodeId, maxDepth, includeDetails, includeNotes, includeAttributes) {
    def targetNode = nodeId ? findNodeById(nodeId) : node
    if (!targetNode) {
        return [error: "Node not found: ${nodeId}"]
    }
    def depthLimit = (maxDepth != null) ? (maxDepth as int) : -1
    return buildSubtree(
        targetNode,
        depthLimit,
        0,
        includeDetails ?: false,
        includeNotes ?: false,
        includeAttributes ?: false
    )
}

def buildSubtree(n, maxDepth, currentDepth, includeDetails, includeNotes, includeAttributes) {
    def result = [
        id: n.id,
        text: n.text,
        child_count: n.children.size(),
        is_folded: n.folded,
        icons: n.icons.icons*.toString(),
        link: n.link?.text
    ]

    if (includeDetails) {
        result.details = n.details?.text ?: ""
    }
    if (includeNotes) {
        result.note = n.note?.text ?: ""
    }
    if (includeAttributes) {
        def attrs = [:]
        n.attributes.each { attrs[it.name] = it.value }
        result.attributes = attrs
    }

    if (includeDetails || includeNotes || includeAttributes) {
        result.style = n.style.name
        result.text_color = colorToHex(n.style.textColor)
        result.background_color = colorToHex(n.style.backgroundColor)
    }

    if (maxDepth < 0 || currentDepth < maxDepth) {
        result.children = n.children.collect {
            buildSubtree(it, maxDepth, currentDepth + 1, includeDetails, includeNotes, includeAttributes)
        }
    }

    return result
}

def bulkCreate(nodeId, outline) {
    def targetNode = nodeId ? findNodeById(nodeId) : node
    if (!targetNode) {
        return [error: "Node not found: ${nodeId}"]
    }
    if (!outline?.trim()) {
        return [error: "Outline text is required"]
    }
    targetNode.appendTextOutlineAsBranch(outline)
    return [
        success: true,
        parent_id: targetNode.id,
        child_count: targetNode.children.size()
    ]
}

def getLink(nodeId) {
    def targetNode = nodeId ? findNodeById(nodeId) : node
    if (!targetNode) {
        return [error: "Node not found: ${nodeId}"]
    }
    return [
        text: targetNode.link?.text,
        uri: targetNode.link?.uri?.toString(),
        node_id: targetNode.link?.node?.id
    ]
}

def setLink(nodeId, target) {
    def targetNode = nodeId ? findNodeById(nodeId) : node
    if (!targetNode) {
        return [error: "Node not found: ${nodeId}"]
    }
    if (!target) {
        return [error: "Link target is required"]
    }

    if (target.startsWith("ID_")) {
        def linkedNode = findNodeById(target)
        if (!linkedNode) {
            return [error: "Target node not found: ${target}"]
        }
        targetNode.link.node = linkedNode
    } else {
        targetNode.link.text = target
    }
    return [success: true]
}

def removeLink(nodeId) {
    def targetNode = nodeId ? findNodeById(nodeId) : node
    if (!targetNode) {
        return [error: "Node not found: ${nodeId}"]
    }
    targetNode.link.remove()
    return [success: true]
}

def getDetails(nodeId) {
    def targetNode = nodeId ? findNodeById(nodeId) : node
    if (!targetNode) {
        return [error: "Node not found: ${nodeId}"]
    }
    return [
        details: targetNode.details?.text ?: "",
        details_content_type: targetNode.detailsContentType
    ]
}

def setDetails(nodeId, text) {
    def targetNode = nodeId ? findNodeById(nodeId) : node
    if (!targetNode) {
        return [error: "Node not found: ${nodeId}"]
    }
    targetNode.details = text
    return [success: true]
}

def moveNode(nodeId, targetParentId, position) {
    if (!nodeId) {
        return [error: "node_id is required"]
    }
    if (!targetParentId) {
        return [error: "target_parent_id is required"]
    }

    def sourceNode = findNodeById(nodeId)
    def targetParent = findNodeById(targetParentId)

    if (!sourceNode) {
        return [error: "Source node not found: ${nodeId}"]
    }
    if (!targetParent) {
        return [error: "Target parent not found: ${targetParentId}"]
    }
    if (sourceNode.isRoot()) {
        return [error: "Cannot move root node"]
    }
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

// Utility Functions

def findNodeById(nodeId) {
    return node.map.node(nodeId)
}

def getNodeInfo(n) {
    return [
        id: n.id,
        text: n.text,
        note: n.note?.text ?: "",
        is_root: n.isRoot(),
        is_folded: n.folded,
        child_count: n.children.size(),
        icons: n.icons.icons*.toString(),
        attributes: n.attributes.map.collectEntries { [(it.key): it.value] },
        style: n.style.name,
        text_color: colorToHex(n.style.textColor),
        background_color: colorToHex(n.style.backgroundColor)
    ]
}

def countAllNodes(n) {
    return 1 + (n.children.collect { countAllNodes(it) }.sum(0))
}

def parseColor(colorStr) {
    if (colorStr.startsWith("#")) {
        return Color.decode(colorStr)
    }
    // Try named colors
    switch (colorStr.toLowerCase()) {
        case "red": return Color.RED
        case "blue": return Color.BLUE
        case "green": return Color.GREEN
        case "yellow": return Color.YELLOW
        case "orange": return Color.ORANGE
        case "white": return Color.WHITE
        case "black": return Color.BLACK
        case "gray": return Color.GRAY
        default: return Color.decode(colorStr)
    }
}

def colorToHex(Color color) {
    if (!color) return null
    return String.format("#%02x%02x%02x", color.red, color.green, color.blue)
}

def getAvailableCommands() {
    return [
        "select_node", "get_selected_node", "get_root_node",
        "navigate_to_parent", "navigate_to_child",
        "create_child", "create_sibling", "set_node_text", "delete_node",
        "set_node_color", "set_background_color", "set_node_style",
        "set_font_bold", "set_font_italic", "set_font_size",
        "add_icon", "remove_icon", "remove_all_icons", "list_icons",
        "add_connector", "remove_connector",
        "set_attribute", "get_attributes", "remove_attribute",
        "set_note", "get_note",
        "get_map_info", "center_on_node", "fold_node", "unfold_node",
        "get_subtree", "bulk_create",
        "get_link", "set_link", "remove_link",
        "get_details", "set_details",
        "move_node",
        "find_nodes"
    ]
}

// Main Execution
startServer()
