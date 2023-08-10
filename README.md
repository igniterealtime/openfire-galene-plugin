# Selective Forwarding Unit (SFU) for Openfire using Galene.

<img src="https://igniterealtime.github.io/openfire-galene-plugin/galene-summary.png" />

## Known Issues

This version has embedded binaries for only Linux 64 and Windows 64.

# Installation

copy galene.jar to the plugins folder

# Configuration

<img src="https://user-images.githubusercontent.com/110731/259781570-5a9a2918-ca51-4bed-80f0-b50db7aa63cb.png" />

## Username/Password
This is Openfire username/password for the user that will have Galene admin permissions to view stream stats and join any meeting. By default the user will be "sfu-user" and the password witll be a random string. If you are using ldap or your Openfire user manager is in read-only mode and a new user cannot be created, then you must create the user and specify the username and password here..

## IP Address/Port
This is the internal IP address of the network card to which you want Galene to bind to. Galene is not exposed outside of the internal network and can only be accessed via Openfire using an XMPP client connection. By default port 6060 will be used. However any other internal port can be used. Use http://ip-address:port on the internal network to confirm galene is up and running.

## TURN IP Address/Port
This is the public IP address of the FQDN of your openfire server that will be exposed to client web browsers when they ask for ICE canndidates during media negotiation. Make sure the port specified is opened for TCP and UDP. galene will bind its interrnal TURN server to this port. If Galene is behind NAT and your NAT device doesn't support hairpinning, then you must use an external TURN server. 

## External URL
This is the base public URL (minus path) that is used to access galene externally by clients. The host name should resolve to the IP address specified for TURN above otherwise, ensure you have a proxy or http forwarding rule that ensures this happens.

# How to use

This plugin implements [XEP-XXXX: In-Band SFU Sessions](https://igniterealtime.github.io/openfire-galene-plugin/xep/xep-xxx-sfu_01-01.xml) for the Galene SFU. 

For an example client, see the [galene plugin for conversejs](https://github.com/conversejs/community-plugins/tree/master/packages/galene)

<img src="https://github.com/conversejs/community-plugins/blob/master/packages/galene/galene.png?raw=true" />

Also see the [Gitea plugin for Openfire](https://github.com/igniterealtime/openfire-zgitea-plugin)

![image](https://user-images.githubusercontent.com/110731/180422009-3ef9255b-0f27-4b93-b06a-f250aeaf69c1.png)

# Further information

Galène's web page is at <https://galene.org> and was created by Juliusz Chroboczek <https://www.irif.fr/~jch/>
Answers to common questions and issues are at <https://galene.org#faq>.


