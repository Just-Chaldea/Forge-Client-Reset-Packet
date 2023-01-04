# Forge-Client-Reset-Packet

In 1.13 Mojang introduced datapacks and as such Forge had to change the handshake packet to encorporate these changes. This broke mod compatability with servers using proxy software as it meant that clients could no longer swap between servers without the game breaking. 

This mod restores compatability with proxy software for the Modern Forge handshake by adding a packet, that can be sent by the proxy to the client, to reset the client's resource pack and datapack. 