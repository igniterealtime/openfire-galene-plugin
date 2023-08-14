## Galene
Selective Forwarding Unit (SFU) for Openfire using Galene.

## Overview
<img src="https://igniterealtime.github.io/openfire-galene-plugin/galene-summary.png" />

## Known Issues

This version has embedded binaries for only Linux 64 and Windows 64.

## Installation

copy galene.jar to the plugins folder

## Configuration

<img src="https://user-images.githubusercontent.com/110731/259781570-5a9a2918-ca51-4bed-80f0-b50db7aa63cb.png" />

### Enable SFU
Enables or disables the plugin. Reload plugin or restart Openfire if this or any of the settings other settings are changed.

### Enable MUC (Groupchat) Support
If this option is not enabled, Galene will only use a single parent group called *public*. Subgroups will be enabled and all participants will have *operator* previledges. They can publish media streams and manage the meeting. See Galene documentation for full details. Both anonymous and  authenticated XMPP sessions can join a public group and all subgroups. 
To join a public subgroup called my-meeting, with the galene web client, use http://your-openfire-server:7070/galene/?room=public/my-meeting&username=your-user-name. 

If this option is enabled, then Galene becomes integrated with Openfire. Openfire MUC groupchat rooms can be used as Galene groups. Authentication is enabled and only authenticated XMPP sessions can join a group subject to the room configuration. For example, member only MUC rooms will disable allow-anonymous in Galene and only room members will be allowed to join the Galene group.
In this mode, users will require an authenticated XMPP session to join an Openfire MUC groupchat room. See Pade client or the Galene plugin for ConverseJS for more details.
Once a user has joined with at least presenter permissions, they can invite an external user with an invitaion token.
To join an MUC groupchat room called lobby with the Galene web client as user *fred* using invitation token xxxxxxxxxxx, use http://localhost:7070/galene?token=xxxxxxxxxxx&room=public&username=fred

### Username/Password
This is Openfire username/password for the user that will have Galene admin permissions to view stream stats and join any meeting. By default the user will be "sfu-user" and the password witll be a random string. If you are using ldap or your Openfire user manager is in read-only mode and a new user cannot be created, then you must create the user and specify the username and password here..

### IP Address/TCP Port
This is the internal IP address of the network card to which you want Galene to bind to. Galene is not exposed outside of the internal network and can only be accessed via Openfire using an XMPP client connection. By default port 6060 will be used. However any other internal port can be used. Use http://ip-address:port on the internal network to confirm galene is up and running.

### UDP Port Min/Max
This limits the pool of ephemeral ports that ICE UDP connections can allocate from. This affects both host candidates, and the local address of server reflexive candidates.

### TURN IP Address/Port
This is the public IP address of the FQDN of your openfire server that will be exposed to client web browsers when they ask for ICE canndidates during media negotiation. Make sure the port specified is opened for TCP and UDP. galene will bind its interrnal TURN server to this port. If Galene is behind NAT and your NAT device doesn't support hairpinning, then you must use an external TURN server. 

### External URL
This is the base public URL (minus path) that is used to access galene externally by clients. The host name should resolve to the IP address specified for TURN above otherwise, ensure you have a proxy or http forwarding rule that ensures this happens.

## How to use

This plugin implements [XEP-XXXX: In-Band SFU Sessions](https://igniterealtime.github.io/openfire-galene-plugin/xep/xep-xxx-sfu_01-01.xml) for the Galene SFU. 

For an example client, see the [galene plugin for conversejs](https://github.com/conversejs/community-plugins/tree/master/packages/galene)

<img src="https://github.com/conversejs/community-plugins/blob/master/packages/galene/galene.png?raw=true" />

Also see the [Gitea plugin for Openfire](https://github.com/igniterealtime/openfire-zgitea-plugin)

![image](https://user-images.githubusercontent.com/110731/180422009-3ef9255b-0f27-4b93-b06a-f250aeaf69c1.png)

## Further information

Galène's web page is at <https://galene.org> and was created by Juliusz Chroboczek <https://www.irif.fr/~jch/>
Answers to common questions and issues are at <https://galene.org#faq>.


