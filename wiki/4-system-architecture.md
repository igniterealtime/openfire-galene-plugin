# System Architecture

> **Relevant source files**
> * [plugin.xml](https://github.com/igniterealtime/openfire-galene-plugin/blob/5aa54ac8/plugin.xml)
> * [src/java/org/ifsoft/galene/openfire/Galene.java](https://github.com/igniterealtime/openfire-galene-plugin/blob/5aa54ac8/src/java/org/ifsoft/galene/openfire/Galene.java)

This document provides a comprehensive overview of the internal architecture of the Openfire Galene Plugin, focusing on how the various components interact to provide SFU (Selective Forwarding Unit) video conferencing capabilities within the XMPP ecosystem. For installation and configuration details, see [Installation & Configuration](./2-installation-and-configuration.md). For protocol specifications, see [XMPP Protocol Extensions](./5-xmpp-protocol-extensions.md). For API details, see [API Reference](./8-api-reference.md).

## System Overview

The Openfire Galene Plugin implements a hybrid architecture that combines XMPP signaling with WebRTC media delivery by embedding the Galene SFU server within Openfire. The system bridges multiple client types (XMPP clients and web browsers) through a sophisticated proxy and handler system while maintaining deep integration with Openfire's Multi-User Chat (MUC) functionality.

### High-Level Component Architecture

```mermaid
flowchart TD

GALENE_MAIN["Galene.java<br>implements Plugin, MUCEventListener<br>ProcessListener, PropertyEventListener"]
INIT_METHOD["initializePlugin()<br>destroyPlugin()"]
CHECK_NATIVES["checkNatives()"]
START_GO["startGoProcesses()"]
GALENE_THREAD["galeneThread: XProcess"]
EXEC_PATH["galeneExePath<br>Platform-specific binary"]
HOME_PATH["galeneHomePath<br>Platform-specific data dir"]
JSP_SERVICE["jspService: WebAppContext<br>/galene context"]
START_JSP["startJSP()"]
GALENE_CONTEXT["galeneContext: ServletContextHandler<br>Proxy to embedded Galene"]
GALENE_WS_CONTEXT["galeneWsContext: ServletContextHandler<br>WebSocket proxy /galene-ws"]
GALENE_IQ["galeneIQHandler: GaleneIQHandler<br>urn:xmpp:sfu:galene:0"]
OLMEET_IQ["olMeetIQHandler: OlMeetIQHandler<br>XEP-0483 Online Meetings"]
IQ_ROUTER["XMPPServer.getIQRouter()"]
SOCKET_CREATOR["GaleneSocketCreator<br>implements JettyWebSocketCreator"]
PROXY_WS["ProxyWebSocket"]
PROXY_CONN["ProxyConnection"]

START_GO --> GALENE_CONTEXT
START_GO --> GALENE_WS_CONTEXT
GALENE_MAIN --> CHECK_NATIVES
GALENE_MAIN --> START_JSP
GALENE_MAIN --> START_GO
GALENE_MAIN --> GALENE_IQ
GALENE_MAIN --> OLMEET_IQ
GALENE_WS_CONTEXT --> SOCKET_CREATOR

subgraph subGraph4 ["WebSocket Infrastructure"]
    SOCKET_CREATOR
    PROXY_WS
    PROXY_CONN
    SOCKET_CREATOR --> PROXY_WS
    PROXY_WS --> PROXY_CONN
end

subgraph subGraph3 ["XMPP Handlers"]
    GALENE_IQ
    OLMEET_IQ
    IQ_ROUTER
    GALENE_IQ --> IQ_ROUTER
    OLMEET_IQ --> IQ_ROUTER
end

subgraph subGraph2 ["Service Integration"]
    JSP_SERVICE
    START_JSP
    GALENE_CONTEXT
    GALENE_WS_CONTEXT
    START_JSP --> JSP_SERVICE
end

subgraph subGraph1 ["Process Management"]
    CHECK_NATIVES
    START_GO
    GALENE_THREAD
    EXEC_PATH
    HOME_PATH
    CHECK_NATIVES --> EXEC_PATH
    CHECK_NATIVES --> HOME_PATH
    START_GO --> GALENE_THREAD
end

subgraph subGraph0 ["Core Plugin"]
    GALENE_MAIN
    INIT_METHOD
    GALENE_MAIN --> INIT_METHOD
end
```

**Sources:** [src/java/org/ifsoft/galene/openfire/Galene.java L60-L133](https://github.com/igniterealtime/openfire-galene-plugin/blob/5aa54ac8/src/java/org/ifsoft/galene/openfire/Galene.java#L60-L133)

 [plugin.xml L1-L27](https://github.com/igniterealtime/openfire-galene-plugin/blob/5aa54ac8/plugin.xml#L1-L27)

## Core Plugin Lifecycle

The `Galene` class serves as the main orchestrator, implementing multiple Openfire interfaces to integrate with different subsystems. The plugin follows a structured initialization and cleanup pattern.

### Plugin Initialization Sequence

```mermaid
sequenceDiagram
  participant PluginManager
  participant Galene.java
  participant Openfire Services
  participant Galene Server Process

  PluginManager->>Galene.java: "initializePlugin(manager, pluginDirectory)"
  Galene.java->>Galene.java: "muc_properties = CacheFactory.createLocalCache()"
  Galene.java->>Openfire Services: "PropertyEventDispatcher.addListener(this)"
  Galene.java->>Openfire Services: "MUCEventDispatcher.addListener(this)"
  Galene.java->>Galene.java: "checkNatives(pluginDirectory)"
  Galene.java->>Galene.java: "executor = Executors.newCachedThreadPool()"
  Galene.java->>Galene.java: "startJSP(pluginDirectory)"
  Galene.java->>Galene.java: "startGoProcesses(pluginDirectory)"
  Galene.java->>Openfire Services: "galeneIQHandler = new GaleneIQHandler()"
  Galene.java->>Openfire Services: "XMPPServer.getIQRouter().addHandler(galeneIQHandler)"
  Galene.java->>Openfire Services: "olMeetIQHandler = new OlMeetIQHandler()"
  Galene.java->>Openfire Services: "XMPPServer.getIQRouter().addHandler(olMeetIQHandler)"
  Galene.java->>Galene Server Process: "Spawn.startProcess(galeneExePath + params)"
  Galene.java->>Galene.java: "startAdminConnection()"
```

**Sources:** [src/java/org/ifsoft/galene/openfire/Galene.java L110-L133](https://github.com/igniterealtime/openfire-galene-plugin/blob/5aa54ac8/src/java/org/ifsoft/galene/openfire/Galene.java#L110-L133)

### Platform Detection and Binary Management

The system supports multiple platforms by detecting the operating system and selecting appropriate binaries:

| Platform | Detection Method | Binary Path | Executable |
| --- | --- | --- | --- |
| Linux 64-bit | `OSUtils.IS_LINUX64` | `classes/linux-64/` | `galene` |
| macOS ARM64 | `OSUtils.IS_MAC64` | `classes/macos-64/` | `galene` |
| Windows 64-bit | `OSUtils.IS_WINDOWS64` | `classes/win-64/` | `galene.exe` |

```mermaid
flowchart TD

CHECK_NATIVES["checkNatives()"]
IS_LINUX["OSUtils.IS_LINUX64"]
IS_MAC["OSUtils.IS_MAC64"]
IS_WIN["OSUtils.IS_WINDOWS64"]
LINUX_PATH["galeneHomePath + /linux-64/<br>galeneExePath + /galene"]
MAC_PATH["galeneHomePath + /macos-64/<br>galeneExePath + /galene"]
WIN_PATH["galeneHomePath + /win-64/<br>galeneExePath + /galene.exe"]
MAKE_EXEC["makeFileExecutable(galeneExePath)"]
SET_PERMS["file.setReadable/setWritable/setExecutable"]

CHECK_NATIVES --> IS_LINUX
CHECK_NATIVES --> IS_MAC
CHECK_NATIVES --> IS_WIN
IS_LINUX --> LINUX_PATH
IS_MAC --> MAC_PATH
IS_WIN --> WIN_PATH
LINUX_PATH --> MAKE_EXEC
MAC_PATH --> MAKE_EXEC
WIN_PATH --> MAKE_EXEC

subgraph subGraph2 ["File Operations"]
    MAKE_EXEC
    SET_PERMS
    MAKE_EXEC --> SET_PERMS
end

subgraph subGraph1 ["Path Resolution"]
    LINUX_PATH
    MAC_PATH
    WIN_PATH
end

subgraph subGraph0 ["Platform Detection"]
    IS_LINUX
    IS_MAC
    IS_WIN
end
```

**Sources:** [src/java/org/ifsoft/galene/openfire/Galene.java L326-L380](https://github.com/igniterealtime/openfire-galene-plugin/blob/5aa54ac8/src/java/org/ifsoft/galene/openfire/Galene.java#L326-L380)

## Service Integration Points

The plugin integrates with Openfire through multiple service attachment points, each serving different client types and protocols.

### HTTP Service Integration

```mermaid
flowchart TD

HTTP_BIND_MGR["HttpBindManager.getInstance()"]
ADD_HANDLER["addJettyHandler()"]
JSP_CTX["jspService: WebAppContext<br>Context: /galene<br>Path: classes/jsp/"]
PROXY_CTX["galeneContext: ServletContextHandler<br>Context: /<br>ProxyServlet.Transparent"]
WS_CTX["galeneWsContext: ServletContextHandler<br>Context: /galene-ws<br>WebSocket endpoint"]
JETTY_WS_INIT["JettyWebSocketServletContainerInitializer"]
WS_CONTAINER["wsContainer.setMaxTextMessageSize(65535)"]
SOCKET_MAPPING["wsContainer.addMapping('/*', GaleneSocketCreator)"]
PROXY_SERVLET["ServletHolder(ProxyServlet.Transparent.class)"]
PROXY_TO["proxyTo: Unsupported markdown: link"]
PREFIX_PARAM["prefix: /"]

ADD_HANDLER --> JSP_CTX
ADD_HANDLER --> PROXY_CTX
ADD_HANDLER --> WS_CTX
WS_CTX --> JETTY_WS_INIT
PROXY_CTX --> PROXY_SERVLET

subgraph subGraph3 ["Proxy Configuration"]
    PROXY_SERVLET
    PROXY_TO
    PREFIX_PARAM
    PROXY_SERVLET --> PROXY_TO
    PROXY_SERVLET --> PREFIX_PARAM
end

subgraph subGraph2 ["WebSocket Configuration"]
    JETTY_WS_INIT
    WS_CONTAINER
    SOCKET_MAPPING
    JETTY_WS_INIT --> WS_CONTAINER
    WS_CONTAINER --> SOCKET_MAPPING
end

subgraph subGraph1 ["Service Contexts"]
    JSP_CTX
    PROXY_CTX
    WS_CTX
end

subgraph subGraph0 ["HttpBindManager Integration"]
    HTTP_BIND_MGR
    ADD_HANDLER
    HTTP_BIND_MGR --> ADD_HANDLER
end
```

**Sources:** [src/java/org/ifsoft/galene/openfire/Galene.java L227-L235](https://github.com/igniterealtime/openfire-galene-plugin/blob/5aa54ac8/src/java/org/ifsoft/galene/openfire/Galene.java#L227-L235)

 [src/java/org/ifsoft/galene/openfire/Galene.java L245-L274](https://github.com/igniterealtime/openfire-galene-plugin/blob/5aa54ac8/src/java/org/ifsoft/galene/openfire/Galene.java#L245-L274)

### IQ Handler Registration

The plugin registers two IQ handlers for different XMPP protocol extensions:

```mermaid
flowchart TD

IQ_ROUTER["XMPPServer.getInstance().getIQRouter()"]
DISCO_HANDLER["XMPPServer.getInstance().getIQDiscoInfoHandler()"]
GALENE_IQ_HANDLER["galeneIQHandler: GaleneIQHandler"]
GALENE_NAMESPACE["urn:xmpp:sfu:galene:0"]
GALENE_START["galeneIQHandler.startHandler()"]
OLMEET_IQ_HANDLER["olMeetIQHandler: OlMeetIQHandler"]
OLMEET_NS1["urn:xmpp:http:online-meetings:initiate:0"]
OLMEET_NS2["urn:xmpp:http:online-meetings#galene"]

IQ_ROUTER --> GALENE_IQ_HANDLER
IQ_ROUTER --> OLMEET_IQ_HANDLER
DISCO_HANDLER --> GALENE_NAMESPACE
DISCO_HANDLER --> OLMEET_NS1
DISCO_HANDLER --> OLMEET_NS2

subgraph subGraph2 ["Online Meetings Handler"]
    OLMEET_IQ_HANDLER
    OLMEET_NS1
    OLMEET_NS2
    OLMEET_IQ_HANDLER --> OLMEET_NS1
    OLMEET_IQ_HANDLER --> OLMEET_NS2
end

subgraph subGraph1 ["Galene SFU Handler"]
    GALENE_IQ_HANDLER
    GALENE_NAMESPACE
    GALENE_START
    GALENE_IQ_HANDLER --> GALENE_START
    GALENE_IQ_HANDLER --> GALENE_NAMESPACE
end

subgraph subGraph0 ["IQ Router Registration"]
    IQ_ROUTER
    DISCO_HANDLER
end
```

**Sources:** [src/java/org/ifsoft/galene/openfire/Galene.java L122-L131](https://github.com/igniterealtime/openfire-galene-plugin/blob/5aa54ac8/src/java/org/ifsoft/galene/openfire/Galene.java#L122-L131)

## WebSocket Proxy Architecture

The WebSocket proxy system bridges web clients to the embedded Galene server through a sophisticated connection management system.

### WebSocket Creation Flow

```mermaid
flowchart TD

WEB_CLIENT["Web Browser Client"]
WS_REQUEST["WebSocket Upgrade Request<br>/galene-ws/*"]
CREATOR["GaleneSocketCreator.createWebSocket()"]
REQ_PROCESSING["JettyServerUpgradeRequest req<br>JettyServerUpgradeResponse resp"]
PATH_EXTRACT["path = request.getRequestURI()<br>query = request.getQueryString()"]
PROTOCOL_HANDLING["req.getSubProtocols()<br>resp.setAcceptedSubProtocol()"]
URL_BUILD["url = ws://ipaddr:port/ws"]
PROXY_CONN_CREATE["ProxyConnection(URI.create(url), protocols, 10000, null)"]
PROXY_WS_CREATE["ProxyWebSocket()<br>socket.setProxyConnection()"]
GALENE_IPADDR["JiveGlobals.getProperty('galene.ipaddr', getIpAddress())"]
GALENE_PORT["JiveGlobals.getProperty('galene.port', getPort())"]

WS_REQUEST --> CREATOR
CREATOR --> URL_BUILD
GALENE_IPADDR --> URL_BUILD
GALENE_PORT --> URL_BUILD

subgraph subGraph3 ["Configuration Properties"]
    GALENE_IPADDR
    GALENE_PORT
end

subgraph subGraph2 ["Connection Setup"]
    URL_BUILD
    PROXY_CONN_CREATE
    PROXY_WS_CREATE
    URL_BUILD --> PROXY_CONN_CREATE
    PROXY_CONN_CREATE --> PROXY_WS_CREATE
end

subgraph GaleneSocketCreator ["GaleneSocketCreator"]
    CREATOR
    REQ_PROCESSING
    PATH_EXTRACT
    PROTOCOL_HANDLING
    CREATOR --> REQ_PROCESSING
    REQ_PROCESSING --> PATH_EXTRACT
    REQ_PROCESSING --> PROTOCOL_HANDLING
end

subgraph subGraph0 ["Client Request"]
    WEB_CLIENT
    WS_REQUEST
    WEB_CLIENT --> WS_REQUEST
end
```

**Sources:** [src/java/org/ifsoft/galene/openfire/Galene.java L293-L324](https://github.com/igniterealtime/openfire-galene-plugin/blob/5aa54ac8/src/java/org/ifsoft/galene/openfire/Galene.java#L293-L324)

## Process Management

The plugin manages the embedded Galene server process with comprehensive parameter configuration and lifecycle management.

### Galene Process Configuration

```mermaid
flowchart TD

BASE_PARAMS["--insecure=true"]
HTTP_PARAM["--http=:6060"]
UDP_RANGE["--udp-range=portValue"]
TURN_PARAM["--turn=ipaddr:port"]
PORT_RANGE_MIN["JiveGlobals.getProperty('galene.port.range.min', '10000')"]
PORT_RANGE_MAX["JiveGlobals.getProperty('galene.port.range.max', '20000')"]
PORT_MUX["JiveGlobals.getProperty('galene.port.mux', '10000')"]
MUX_ENABLED["JiveGlobals.getBooleanProperty('galene.mux.enabled', false)"]
TURN_ENABLED["JiveGlobals.getBooleanProperty('galene.turn.enabled', false)"]
PARAM_BUILDER["params = --insecure=true --http=:port + turnParam + --udp-range=portValue"]
SPAWN_PROCESS["Spawn.startProcess(galeneExePath + params, new File(galeneHomePath), this)"]
XPROCESS["galeneThread: XProcess"]

PORT_RANGE_MIN --> UDP_RANGE
PORT_RANGE_MAX --> UDP_RANGE
PORT_MUX --> UDP_RANGE
MUX_ENABLED --> UDP_RANGE
TURN_ENABLED --> TURN_PARAM
BASE_PARAMS --> PARAM_BUILDER
HTTP_PARAM --> PARAM_BUILDER
UDP_RANGE --> PARAM_BUILDER
TURN_PARAM --> PARAM_BUILDER

subgraph subGraph2 ["Process Execution"]
    PARAM_BUILDER
    SPAWN_PROCESS
    XPROCESS
    PARAM_BUILDER --> SPAWN_PROCESS
    SPAWN_PROCESS --> XPROCESS
end

subgraph subGraph1 ["Configuration Resolution"]
    PORT_RANGE_MIN
    PORT_RANGE_MAX
    PORT_MUX
    MUX_ENABLED
    TURN_ENABLED
end

subgraph subGraph0 ["Process Parameters"]
    BASE_PARAMS
    HTTP_PARAM
    UDP_RANGE
    TURN_PARAM
end
```

**Sources:** [src/java/org/ifsoft/galene/openfire/Galene.java L256-L286](https://github.com/igniterealtime/openfire-galene-plugin/blob/5aa54ac8/src/java/org/ifsoft/galene/openfire/Galene.java#L256-L286)

## Configuration File Management

The system dynamically generates configuration files for the Galene server, including the main configuration and per-group files based on MUC room settings.

### Configuration Generation System

```mermaid
flowchart TD

SETUP_FILES["setupGaleneFiles()"]
CONFIG_JSON["data/config.json"]
ADMIN_USER_CONFIG["users: {sfu-admin: {password, permissions}}"]
WRITABLE_GROUPS["writableGroups: true"]
ORIGINS["allowOrigin/allowAdminOrigin: [hostname]"]
MUC_ENABLED["JiveGlobals.getBooleanProperty('galene.muc.enabled')"]
ROOM_ITERATION["XMPPServer.getMultiUserChatManager()<br>.getMultiUserChatService('conference')<br>.getActiveChatRooms()"]
WRITE_GROUP_FILE["writeGaleneGroupFile(room.getJID(), null)"]
PUBLIC_JSON["groups/public.json"]
PUBLIC_CONFIG["public: true<br>description: 'A public place to hangout'"]
AUTH_KEYS["authKeys: [JWT configuration]"]
AUTH_SERVER["authServer: Unsupported markdown: link"]
CODECS["codecs: ['vp9', 'av1', 'opus', 'h264']"]
JWT_KEY["JWebToken.SECRET_KEY"]
KEY_CONFIG["kty: 'oct', alg: 'HS256', k: SECRET_KEY, kid: '0'"]

SETUP_FILES --> MUC_ENABLED
SETUP_FILES --> PUBLIC_JSON
AUTH_KEYS --> JWT_KEY

subgraph subGraph3 ["JWT Configuration"]
    JWT_KEY
    KEY_CONFIG
    JWT_KEY --> KEY_CONFIG
end

subgraph subGraph2 ["Public Group Template"]
    PUBLIC_JSON
    PUBLIC_CONFIG
    AUTH_KEYS
    AUTH_SERVER
    CODECS
    PUBLIC_JSON --> PUBLIC_CONFIG
    PUBLIC_JSON --> AUTH_KEYS
    PUBLIC_JSON --> AUTH_SERVER
    PUBLIC_JSON --> CODECS
end

subgraph subGraph1 ["Group File Generation"]
    MUC_ENABLED
    ROOM_ITERATION
    WRITE_GROUP_FILE
    MUC_ENABLED --> ROOM_ITERATION
    ROOM_ITERATION --> WRITE_GROUP_FILE
end

subgraph subGraph0 ["Main Configuration Generation"]
    SETUP_FILES
    CONFIG_JSON
    ADMIN_USER_CONFIG
    WRITABLE_GROUPS
    ORIGINS
    SETUP_FILES --> CONFIG_JSON
    CONFIG_JSON --> ADMIN_USER_CONFIG
    CONFIG_JSON --> WRITABLE_GROUPS
    CONFIG_JSON --> ORIGINS
end
```

**Sources:** [src/java/org/ifsoft/galene/openfire/Galene.java L382-L475](https://github.com/igniterealtime/openfire-galene-plugin/blob/5aa54ac8/src/java/org/ifsoft/galene/openfire/Galene.java#L382-L475)

### MUC Integration and Group File Generation

The system creates Galene group files that correspond to MUC rooms, inheriting permissions and settings:

```mermaid
flowchart TD

MUC_ROOM["MUCRoom object"]
ROOM_JID["room.getJID()"]
ROOM_PASSWORD["room.getPassword()"]
IS_PUBLIC["room.isPublicRoom()"]
DESCRIPTION["room.getDescription()"]
MAX_USERS["room.getMaxUsers()"]
LOG_ENABLED["room.isLogEnabled()"]
GROUP_JSON["groups/{roomName}.json"]
PERMISSIONS["op: [owner passwords]<br>presenter: [admin passwords]<br>other: [member passwords]"]
GROUP_SETTINGS["public: isPublicRoom<br>description: room description<br>max-clients: maxUsers<br>allow-recording: logEnabled"]
AUTH_CONFIG["authKeys: [JWT keys]<br>authServer: auth endpoint"]
OWNER_PASS["galene.owner.password property"]
ADMIN_PASS["galene.admin.password property"]
MEMBER_PASS["MUC room password"]

ROOM_JID --> GROUP_JSON
OWNER_PASS --> PERMISSIONS
ADMIN_PASS --> PERMISSIONS
MEMBER_PASS --> PERMISSIONS
IS_PUBLIC --> GROUP_SETTINGS
DESCRIPTION --> GROUP_SETTINGS
MAX_USERS --> GROUP_SETTINGS
LOG_ENABLED --> GROUP_SETTINGS

subgraph subGraph2 ["Permission Mapping"]
    OWNER_PASS
    ADMIN_PASS
    MEMBER_PASS
end

subgraph subGraph1 ["Galene Group Configuration"]
    GROUP_JSON
    PERMISSIONS
    GROUP_SETTINGS
    AUTH_CONFIG
    GROUP_JSON --> AUTH_CONFIG
end

subgraph subGraph0 ["MUC Room Properties"]
    MUC_ROOM
    ROOM_JID
    ROOM_PASSWORD
    IS_PUBLIC
    DESCRIPTION
    MAX_USERS
    LOG_ENABLED
    MUC_ROOM --> ROOM_JID
    MUC_ROOM --> ROOM_PASSWORD
    MUC_ROOM --> IS_PUBLIC
    MUC_ROOM --> DESCRIPTION
    MUC_ROOM --> MAX_USERS
    MUC_ROOM --> LOG_ENABLED
end
```

**Sources:** [src/java/org/ifsoft/galene/openfire/Galene.java L612-L710](https://github.com/igniterealtime/openfire-galene-plugin/blob/5aa54ac8/src/java/org/ifsoft/galene/openfire/Galene.java#L612-L710)

## Admin Console Integration

The plugin provides administrative interfaces through JSP pages integrated into Openfire's admin console structure.

### Admin Console Structure

| Page ID | JSP File | Purpose | Tab Location |
| --- | --- | --- | --- |
| `galene-summary` | `galene-summary.jsp` | Overview and status | Media Services |
| `galene-settings` | `galene-settings.jsp` | Global configuration | Media Services |
| `galene-muc` | `galene-muc.jsp` | MUC-specific SFU settings | Group Chat → Room Options |

**Sources:** [plugin.xml L12-L26](https://github.com/igniterealtime/openfire-galene-plugin/blob/5aa54ac8/plugin.xml#L12-L26)

This architecture provides a robust foundation for integrating Galene SFU capabilities into Openfire while maintaining compatibility with existing XMPP infrastructure and providing multiple client access methods through both native XMPP protocols and web-based interfaces.