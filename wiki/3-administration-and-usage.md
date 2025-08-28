# Administration & Usage

> **Relevant source files**
> * [docs/galene-summary.png](https://github.com/igniterealtime/openfire-galene-plugin/blob/5aa54ac8/docs/galene-summary.png)
> * [src/i18n/galene_i18n.properties](https://github.com/igniterealtime/openfire-galene-plugin/blob/5aa54ac8/src/i18n/galene_i18n.properties)
> * [src/java/org/ifsoft/galene/openfire/MUCRoomProperties.java](https://github.com/igniterealtime/openfire-galene-plugin/blob/5aa54ac8/src/java/org/ifsoft/galene/openfire/MUCRoomProperties.java)
> * [src/web/galene-expire.jsp](https://github.com/igniterealtime/openfire-galene-plugin/blob/5aa54ac8/src/web/galene-expire.jsp)
> * [src/web/galene-muc.jsp](https://github.com/igniterealtime/openfire-galene-plugin/blob/5aa54ac8/src/web/galene-muc.jsp)
> * [src/web/galene-settings.jsp](https://github.com/igniterealtime/openfire-galene-plugin/blob/5aa54ac8/src/web/galene-settings.jsp)
> * [src/web/galene-summary.jsp](https://github.com/igniterealtime/openfire-galene-plugin/blob/5aa54ac8/src/web/galene-summary.jsp)

This page covers how administrators and users interact with the Galene plugin through its various interfaces, including monitoring active streams, configuring settings, and managing video conferences. For technical details about the underlying plugin architecture, see [System Architecture](/igniterealtime/openfire-galene-plugin/4-system-architecture). For specific client integration patterns, see [XMPP Client Integration](/igniterealtime/openfire-galene-plugin/3.3-xmpp-client-integration).

## Administrative Interface Architecture

The plugin provides web-based administrative interfaces integrated into Openfire's admin console, along with programmatic management capabilities.

### Admin Console Integration

```mermaid
flowchart TD

CONSOLE["Admin Console Framework"]
MENU["Plugin Menu Items"]
SUMMARY["galene-summary.jsp<br>Stream Monitoring"]
SETTINGS["galene-settings.jsp<br>Global Configuration"]
MUC_CONFIG["galene-muc.jsp<br>Room Configuration"]
EXPIRE["galene-expire.jsp<br>Client Termination"]
GALENE_PLUGIN["Galene.self<br>Main Plugin Instance"]
MUC_PROPS["MUCRoomProperties<br>Database Layer"]
JIVE_GLOBALS["JiveGlobals<br>Configuration Store"]
GALENE_API["Galene API<br>/galene-api/v0/.stats"]
DB_PROPS["ofMucRoomProp<br>Database Table"]
PLUGIN_CONFIG["Plugin Configuration<br>Properties"]

CONSOLE --> SUMMARY
CONSOLE --> SETTINGS
CONSOLE --> MUC_CONFIG
MENU --> SUMMARY
MENU --> SETTINGS
SUMMARY --> GALENE_PLUGIN
SETTINGS --> JIVE_GLOBALS
MUC_CONFIG --> MUC_PROPS
EXPIRE --> GALENE_PLUGIN
GALENE_PLUGIN --> GALENE_API
MUC_PROPS --> DB_PROPS
JIVE_GLOBALS --> PLUGIN_CONFIG

subgraph subGraph3 ["Data Sources"]
    GALENE_API
    DB_PROPS
    PLUGIN_CONFIG
end

subgraph subGraph2 ["Backend Services"]
    GALENE_PLUGIN
    MUC_PROPS
    JIVE_GLOBALS
end

subgraph subGraph1 ["Galene Admin Pages"]
    SUMMARY
    SETTINGS
    MUC_CONFIG
    EXPIRE
end

subgraph subGraph0 ["Openfire Admin Console"]
    CONSOLE
    MENU
end
```

**Sources:** [src/web/galene-summary.jsp L1-L214](https://github.com/igniterealtime/openfire-galene-plugin/blob/5aa54ac8/src/web/galene-summary.jsp#L1-L214)

 [src/web/galene-settings.jsp L1-L214](https://github.com/igniterealtime/openfire-galene-plugin/blob/5aa54ac8/src/web/galene-settings.jsp#L1-L214)

 [src/web/galene-muc.jsp L1-L76](https://github.com/igniterealtime/openfire-galene-plugin/blob/5aa54ac8/src/web/galene-muc.jsp#L1-L76)

 [src/web/galene-expire.jsp L1-L21](https://github.com/igniterealtime/openfire-galene-plugin/blob/5aa54ac8/src/web/galene-expire.jsp#L1-L21)

## Stream Monitoring and Management

Administrators can monitor active video streams and manage client connections through the summary interface.

### Real-time Stream Monitoring

The `galene-summary.jsp` page provides comprehensive monitoring of active connections:

```mermaid
flowchart TD

SUMMARY_PAGE["galene-summary.jsp"]
CLIENT_TABLE["Client Stream Table"]
METRICS["Stream Metrics Display"]
STATS_API["/galene-api/v0/.stats<br>Galene Statistics API"]
CLIENT_REGISTRY["GaleneIQHandler.clients<br>XMPP Client Registry"]
CONNECTION_DATA["JSONArray connections<br>Active Connections"]
CLIENT_DATA["JSONObject client<br>Per-Client Data"]
TRACK_DATA["up/down arrays<br>Media Track Info"]
EXPIRE_ACTION["Client Termination<br>galene-expire.jsp"]
REDIRECT["Redirect to Group<br>External URL"]

SUMMARY_PAGE --> STATS_API
SUMMARY_PAGE --> CLIENT_REGISTRY
STATS_API --> CONNECTION_DATA
CLIENT_TABLE --> EXPIRE_ACTION
CLIENT_TABLE --> REDIRECT
METRICS --> CONNECTION_DATA

subgraph Actions ["Actions"]
    EXPIRE_ACTION
    REDIRECT
end

subgraph subGraph2 ["Stream Data"]
    CONNECTION_DATA
    CLIENT_DATA
    TRACK_DATA
    CONNECTION_DATA --> CLIENT_DATA
    CLIENT_DATA --> TRACK_DATA
end

subgraph subGraph1 ["Data Collection"]
    STATS_API
    CLIENT_REGISTRY
end

subgraph subGraph0 ["Monitoring Interface"]
    SUMMARY_PAGE
    CLIENT_TABLE
    METRICS
end
```

**Sources:** [src/web/galene-summary.jsp L19-L26](https://github.com/igniterealtime/openfire-galene-plugin/blob/5aa54ac8/src/web/galene-summary.jsp#L19-L26)

 [src/web/galene-summary.jsp L54-L212](https://github.com/igniterealtime/openfire-galene-plugin/blob/5aa54ac8/src/web/galene-summary.jsp#L54-L212)

 [src/web/galene-expire.jsp L13-L16](https://github.com/igniterealtime/openfire-galene-plugin/blob/5aa54ac8/src/web/galene-expire.jsp#L13-L16)

### Stream Metrics and Information

The monitoring interface displays detailed information about each active stream:

| Metric | Description | Source |
| --- | --- | --- |
| Client ID | XMPP JID or connection identifier | `GaleneIQHandler.clients` registry |
| Stream ID | Galene internal stream identifier | `/galene-api/v0/.stats` |
| Stream Type | `up` (sending) or `down` (receiving) | Track data arrays |
| Track Type | `audio` or `video` | Track metadata |
| Bitrate | Current bitrate in bits/sec | Track statistics |
| Max Bitrate | Maximum configured bitrate | Track configuration |
| Packet Loss | Network packet loss percentage | RTC statistics |
| Jitter | Network jitter measurements | RTC statistics |

**Sources:** [src/web/galene-summary.jsp L64-L75](https://github.com/igniterealtime/openfire-galene-plugin/blob/5aa54ac8/src/web/galene-summary.jsp#L64-L75)

 [src/web/galene-summary.jsp L168-L189](https://github.com/igniterealtime/openfire-galene-plugin/blob/5aa54ac8/src/web/galene-summary.jsp#L168-L189)

 [src/i18n/galene_i18n.properties L34-L42](https://github.com/igniterealtime/openfire-galene-plugin/blob/5aa54ac8/src/i18n/galene_i18n.properties#L34-L42)

## Configuration Management

The plugin provides multiple levels of configuration: global settings, per-room configuration, and runtime parameters.

### Global Plugin Configuration

```mermaid
flowchart TD

SETTINGS_FORM["galene-settings.jsp<br>Configuration Form"]
UPDATE_HANDLER["Form Update Handler"]
ENABLED["galene.enabled<br>Plugin Enable/Disable"]
MUC_ENABLED["galene.muc.enabled<br>MUC Integration"]
NETWORK_CONFIG["Network Configuration<br>Ports and Addresses"]
TURN_CONFIG["TURN Server Settings"]
AUTH_CONFIG["Authentication Settings"]
JIVE_GLOBALS["JiveGlobals.setProperty()<br>Openfire Configuration"]
PLUGIN_REFRESH["plugin.setupGaleneFiles()<br>Configuration Reload"]

UPDATE_HANDLER --> ENABLED
UPDATE_HANDLER --> MUC_ENABLED
UPDATE_HANDLER --> NETWORK_CONFIG
UPDATE_HANDLER --> TURN_CONFIG
UPDATE_HANDLER --> AUTH_CONFIG
ENABLED --> JIVE_GLOBALS
MUC_ENABLED --> JIVE_GLOBALS
NETWORK_CONFIG --> JIVE_GLOBALS
TURN_CONFIG --> JIVE_GLOBALS
AUTH_CONFIG --> JIVE_GLOBALS

subgraph subGraph2 ["Storage Layer"]
    JIVE_GLOBALS
    PLUGIN_REFRESH
    JIVE_GLOBALS --> PLUGIN_REFRESH
end

subgraph subGraph1 ["Configuration Properties"]
    ENABLED
    MUC_ENABLED
    NETWORK_CONFIG
    TURN_CONFIG
    AUTH_CONFIG
end

subgraph subGraph0 ["Configuration Interface"]
    SETTINGS_FORM
    UPDATE_HANDLER
    SETTINGS_FORM --> UPDATE_HANDLER
end
```

**Sources:** [src/web/galene-settings.jsp L16-L61](https://github.com/igniterealtime/openfire-galene-plugin/blob/5aa54ac8/src/web/galene-settings.jsp#L16-L61)

 [src/web/galene-settings.jsp L91-L192](https://github.com/igniterealtime/openfire-galene-plugin/blob/5aa54ac8/src/web/galene-settings.jsp#L91-L192)

### Configuration Properties Reference

| Property | Default | Purpose |
| --- | --- | --- |
| `galene.enabled` | `true` | Enable/disable the plugin |
| `galene.muc.enabled` | `false` | Enable MUC room integration |
| `galene.mux.enabled` | `false` | Use single UDP port vs range |
| `galene.turn.enabled` | `false` | Enable embedded TURN server |
| `galene.username` | `sfu-admin` | Admin username for Galene |
| `galene.password` | `sfu-admin` | Admin password for Galene |
| `galene.port` | Auto-detected | TCP port for Galene |
| `galene.port.range.min` | Auto-detected | Minimum UDP port |
| `galene.port.range.max` | Auto-detected | Maximum UDP port |
| `galene.ipaddr` | Auto-detected | Server IP address |
| `galene.url` | Auto-detected | External access URL |

**Sources:** [src/web/galene-settings.jsp L18-L60](https://github.com/igniterealtime/openfire-galene-plugin/blob/5aa54ac8/src/web/galene-settings.jsp#L18-L60)

 [src/i18n/galene_i18n.properties L7-L24](https://github.com/igniterealtime/openfire-galene-plugin/blob/5aa54ac8/src/i18n/galene_i18n.properties#L7-L24)

### Per-Room Configuration

Individual MUC rooms can have specific SFU settings managed through the room configuration interface:

```mermaid
flowchart TD

ROOM_PAGE["galene-muc.jsp<br>Room Settings Page"]
ROOM_FORM["Configuration Form"]
ROOM_ENABLED["galene.enabled<br>Per-Room Enable"]
FEDERATION["galene.federation.enabled<br>Federation Settings"]
MUC_PROPS_CLASS["MUCRoomProperties<br>Property Management Class"]
PROP_METHODS["get/put/remove methods<br>Property CRUD Operations"]
DB_TABLE["ofMucRoomProp<br>Database Storage"]
GROUP_FILE["Galene Group File<br>JSON Configuration"]
FILE_WRITER["writeGaleneGroupFile()<br>File Generator"]

ROOM_FORM --> ROOM_ENABLED
ROOM_FORM --> FEDERATION
ROOM_ENABLED --> MUC_PROPS_CLASS
FEDERATION --> MUC_PROPS_CLASS
MUC_PROPS_CLASS --> FILE_WRITER

subgraph subGraph3 ["File Generation"]
    GROUP_FILE
    FILE_WRITER
    FILE_WRITER --> GROUP_FILE
end

subgraph subGraph2 ["Database Layer"]
    MUC_PROPS_CLASS
    PROP_METHODS
    DB_TABLE
    MUC_PROPS_CLASS --> PROP_METHODS
    PROP_METHODS --> DB_TABLE
end

subgraph subGraph1 ["Room Properties"]
    ROOM_ENABLED
    FEDERATION
end

subgraph subGraph0 ["Room Configuration"]
    ROOM_PAGE
    ROOM_FORM
    ROOM_PAGE --> ROOM_FORM
end
```

**Sources:** [src/web/galene-muc.jsp L23-L31](https://github.com/igniterealtime/openfire-galene-plugin/blob/5aa54ac8/src/web/galene-muc.jsp#L23-L31)

 [src/java/org/ifsoft/galene/openfire/MUCRoomProperties.java L172-L196](https://github.com/igniterealtime/openfire-galene-plugin/blob/5aa54ac8/src/java/org/ifsoft/galene/openfire/MUCRoomProperties.java#L172-L196)

 [src/java/org/ifsoft/galene/openfire/MUCRoomProperties.java L227-L244](https://github.com/igniterealtime/openfire-galene-plugin/blob/5aa54ac8/src/java/org/ifsoft/galene/openfire/MUCRoomProperties.java#L227-L244)

## User Access Patterns

Users can access video conferences through multiple pathways depending on their client capabilities and preferences.

### Access Method Overview

```mermaid
flowchart TD

WEB_BROWSER["Web Browser<br>Direct HTTP Access"]
XMPP_CLIENTS["XMPP Clients<br>Protocol Integration"]
MOBILE_APPS["Mobile Applications<br>Custom Implementations"]
ANON_ACCESS["Anonymous Access<br>Public Groups"]
AUTH_ACCESS["Authenticated Access<br>User Credentials"]
MUC_PERMS["MUC Permissions<br>Room-Based Access"]
GALENE_URL["galene.url<br>Direct Web Access"]
IQ_HANDLERS["IQ Message Handlers<br>XMPP Integration"]
JWT_AUTH["JWT Authentication<br>Token-Based Access"]

WEB_BROWSER --> GALENE_URL
XMPP_CLIENTS --> IQ_HANDLERS
MOBILE_APPS --> JWT_AUTH
GALENE_URL --> ANON_ACCESS
IQ_HANDLERS --> MUC_PERMS
JWT_AUTH --> AUTH_ACCESS
ANON_ACCESS --> WEB_BROWSER
AUTH_ACCESS --> XMPP_CLIENTS
MUC_PERMS --> XMPP_CLIENTS

subgraph subGraph2 ["Entry Points"]
    GALENE_URL
    IQ_HANDLERS
    JWT_AUTH
end

subgraph subGraph1 ["Authentication Paths"]
    ANON_ACCESS
    AUTH_ACCESS
    MUC_PERMS
end

subgraph subGraph0 ["User Access Methods"]
    WEB_BROWSER
    XMPP_CLIENTS
    MOBILE_APPS
end
```

**Sources:** [src/web/galene-muc.jsp L34-L35](https://github.com/igniterealtime/openfire-galene-plugin/blob/5aa54ac8/src/web/galene-muc.jsp#L34-L35)

 [src/web/galene-summary.jsp L57-L58](https://github.com/igniterealtime/openfire-galene-plugin/blob/5aa54ac8/src/web/galene-summary.jsp#L57-L58)

 [src/i18n/galene_i18n.properties L43-L44](https://github.com/igniterealtime/openfire-galene-plugin/blob/5aa54ac8/src/i18n/galene_i18n.properties#L43-L44)

### Administrative Actions

Administrators can perform various management tasks through the web interface:

| Action | Interface | Purpose |
| --- | --- | --- |
| View Active Streams | `galene-summary.jsp` | Monitor real-time connections |
| Terminate Client | `galene-expire.jsp` | Force disconnect problematic clients |
| Configure Global Settings | `galene-settings.jsp` | Adjust plugin-wide parameters |
| Configure Room Settings | `galene-muc.jsp` | Set per-room SFU options |
| Access Group Lobby | External URL links | Direct web interface access |

The termination workflow demonstrates the administrative control flow:

```mermaid
sequenceDiagram
  participant Administrator
  participant galene-summary.jsp
  participant galene-expire.jsp
  participant Galene.self
  participant Galene Server

  Administrator->>galene-summary.jsp: "View active streams"
  galene-summary.jsp->>Galene.self: "getJson('/galene-api/v0/.stats')"
  Galene.self->>Galene Server: "HTTP GET /stats"
  Galene Server-->>Galene.self: "JSON stream data"
  Galene.self-->>galene-summary.jsp: "Connection information"
  galene-summary.jsp-->>Administrator: "Display stream table"
  Administrator->>galene-expire.jsp: "Click expire client"
  galene-expire.jsp->>Galene.self: "terminateClient(client, group)"
  Galene.self->>Galene Server: "Terminate client request"
  galene-expire.jsp-->>Administrator: "Redirect with success message"
```

**Sources:** [src/web/galene-summary.jsp L195-L197](https://github.com/igniterealtime/openfire-galene-plugin/blob/5aa54ac8/src/web/galene-summary.jsp#L195-L197)

 [src/web/galene-expire.jsp L16-L20](https://github.com/igniterealtime/openfire-galene-plugin/blob/5aa54ac8/src/web/galene-expire.jsp#L16-L20)