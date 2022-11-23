package gg.chaldea.client.reset.packet.mixin;

import com.google.common.collect.Maps;
import net.minecraft.network.login.server.SDisconnectLoginPacket;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.StringTextComponent;
import net.minecraftforge.fml.network.*;
import net.minecraftforge.registries.ForgeRegistry;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import static net.minecraftforge.registries.ForgeRegistry.REGISTRIES;

@Mixin(FMLHandshakeHandler.class)
public class MixinFMLHandshakeHandler {


    @Shadow(remap = false) @Final static Marker FMLHSMARKER;
    @Shadow(remap = false) @Final private static Logger LOGGER;

    @Shadow(remap = false) private Set<ResourceLocation> registriesToReceive;
    @Shadow(remap = false) private Map<ResourceLocation, ForgeRegistry.Snapshot> registrySnapshots;

    /**
     * @author
     * @reason
     */
    @Overwrite(remap = false)
    void handleServerModListOnClient(FMLHandshakeMessages.S2CModList serverModList, Supplier<NetworkEvent.Context> c) {
        LOGGER.debug(FMLHSMARKER, "Logging into server with mod list [{}]", String.join(", ", serverModList.getModList()));
        c.get().setPacketHandled(true);
        FMLNetworkConstants.handshakeChannel.reply(new FMLHandshakeMessages.C2SModListReply(), c.get());

        LOGGER.debug(FMLHSMARKER, "Accepted server connection");
        // Set the modded marker on the channel so we know we got packets
        c.get().getNetworkManager().channel().attr(FMLNetworkConstants.FML_NETVERSION).set(FMLNetworkConstants.NETVERSION);
        c.get().getNetworkManager().channel().attr(FMLNetworkConstants.FML_CONNECTION_DATA)
                .set(new FMLConnectionData(serverModList.getModList(), serverModList.getChannels()));

        this.registriesToReceive = new HashSet<>(serverModList.getRegistries());
        this.registrySnapshots = Maps.newHashMap();
        LOGGER.debug(REGISTRIES, "Expecting {} registries: {}", ()->this.registriesToReceive.size(), ()->this.registriesToReceive);
    }

    /**
     * @author
     * @reason
     */
    @Overwrite(remap = false)
    void handleClientModListOnServer(FMLHandshakeMessages.C2SModListReply clientModList, Supplier<NetworkEvent.Context> c) {
        LOGGER.debug(FMLHSMARKER, "Received client connection with modlist [{}]", String.join(", ", clientModList.getModList()));
        boolean accepted = NetworkRegistry.validateServerChannels(clientModList.getChannels());
        ((NetworkEvent.Context)c.get()).getNetworkManager().channel().attr(FMLNetworkConstants.FML_CONNECTION_DATA).set(new FMLConnectionData(clientModList.getModList(), clientModList.getChannels()));
        ((NetworkEvent.Context)c.get()).setPacketHandled(true);
        if (!accepted) {
            LOGGER.error(FMLHSMARKER, "Terminating connection with client, mismatched mod list");
            c.get().getNetworkManager().send(new SDisconnectLoginPacket(new StringTextComponent("Connection closed - mismatched mod channel list")));
            ((NetworkEvent.Context)c.get()).getNetworkManager().disconnect(new StringTextComponent("Connection closed - mismatched mod channel list"));
        } else {
            LOGGER.debug(FMLHSMARKER, "Accepted client connection mod list");
        }
    }
}
