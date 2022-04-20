package gg.chaldea.client.reset.packet;

import gg.chaldea.client.reset.packet.network.S2CReset;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.DirtMessageScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.network.login.ClientLoginNetHandler;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.ProtocolType;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.network.*;
import net.minecraftforge.registries.GameData;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

@Mod("clientresetpacket")
public class ClientReset {

    @SubscribeEvent
    public static void init(final FMLCommonSetupEvent event) {
        FMLNetworkConstants.handshakeChannel.messageBuilder(S2CReset.class, 98).
                loginIndex(FMLHandshakeMessages.LoginIndexedMessage::getLoginIndex, FMLHandshakeMessages.LoginIndexedMessage::setLoginIndex).
                decoder(S2CReset::decode).
                encoder(S2CReset::encode).
                consumer(FMLHandshakeHandler.biConsumerFor(ClientReset::handleReset)).
                add();
    }

    public static void handleReset(FMLHandshakeHandler handler, S2CReset msg, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        NetworkManager connection = context.getNetworkManager();

        if (context.getDirection() != NetworkDirection.LOGIN_TO_CLIENT && context.getDirection() != NetworkDirection.PLAY_TO_CLIENT) {
            connection.disconnect(new StringTextComponent("Illegal packet received, terminating connection"));
            throw new IllegalStateException("Invalid packet received, aborting connection");
        }

        FMLHandshakeHandler.LOGGER.debug(FMLHandshakeHandler.FMLHSMARKER, "Received reset from server");

        if (!handleClear(context)) {
            return;
        }

        NetworkHooks.registerClientLoginChannel(connection);
        connection.setProtocol(ProtocolType.LOGIN);
        connection.setListener(new ClientLoginNetHandler(
                connection, Minecraft.getInstance(), null, statusMessage -> {}
        ));

        context.setPacketHandled(true);
        FMLNetworkConstants.handshakeChannel.reply(
                new FMLHandshakeMessages.C2SAcknowledge(),
                new NetworkEvent.Context(connection, NetworkDirection.LOGIN_TO_CLIENT, 98)
        );

        FMLHandshakeHandler.LOGGER.debug(FMLHandshakeHandler.FMLHSMARKER, "Reset complete");
    }

    @OnlyIn(Dist.CLIENT)
    public static boolean handleClear(NetworkEvent.Context context) {
        CompletableFuture<Void> future = context.enqueueWork(() -> {
            FMLHandshakeHandler.LOGGER.debug(FMLHandshakeHandler.FMLHSMARKER, "Clearing");

            // Preserve
            ServerData serverData = Minecraft.getInstance().getCurrentServer();

            // Clear
            if (Minecraft.getInstance().level == null) {
                // Ensure the GameData is reverted in case the client is reset during the handshake.
                GameData.revertToFrozen();
            }

            // Clear
            Minecraft.getInstance().clearLevel(new DirtMessageScreen(new TranslationTextComponent("connect.negotiating")));

            // Restore
            Minecraft.getInstance().setCurrentServer(serverData);
        });

        FMLHandshakeHandler.LOGGER.debug(FMLHandshakeHandler.FMLHSMARKER, "Waiting for clear to complete");
        try {
            future.get();
            FMLHandshakeHandler.LOGGER.debug("Clear complete, continuing reset");
            return true;
        } catch (Exception ex) {
            FMLHandshakeHandler.LOGGER.error(FMLHandshakeHandler.FMLHSMARKER, "Failed to clear, closing connection", ex);
            context.getNetworkManager().disconnect(new StringTextComponent("Failed to clear, closing connection"));
            return false;
        }
    }
}
