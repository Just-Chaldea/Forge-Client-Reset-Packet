package gg.chaldea.client.reset.packet;

import gg.chaldea.client.reset.packet.network.S2CReset;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.GenericDirtMessageScreen;
import net.minecraft.client.multiplayer.ClientHandshakePacketListenerImpl;
import net.minecraft.network.Connection;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.IExtensionPoint;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.network.*;
import net.minecraftforge.registries.GameData;
import net.minecraft.client.gui.screens.TitleScreen;

import java.util.NoSuchElementException;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

@Mod("clientresetpacket")
public class ClientReset {

    public ClientReset() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        
        bus.addListener(ClientReset::init);

        ModLoadingContext.get().registerExtensionPoint(IExtensionPoint.DisplayTest.class, ()-> new IExtensionPoint.DisplayTest(()-> NetworkConstants.IGNORESERVERONLY, (remote, isServer)-> true));
    }

    @SubscribeEvent
    public static void init(FMLCommonSetupEvent event) {
        HandshakeHandler.LOGGER.info(HandshakeHandler.FMLHSMARKER, "Registering forge reset packet");
        NetworkConstants.handshakeChannel.messageBuilder(S2CReset.class, 98).
                loginIndex(HandshakeMessages.LoginIndexedMessage::getLoginIndex, HandshakeMessages.LoginIndexedMessage::setLoginIndex).
                decoder(S2CReset::decode).
                encoder(S2CReset::encode).
                consumerNetworkThread(HandshakeHandler.biConsumerFor(ClientReset::handleReset)).
                add();
        HandshakeHandler.LOGGER.info(HandshakeHandler.FMLHSMARKER, "Registered forge reset packet");
    }

    public static void handleReset(HandshakeHandler handler, S2CReset msg, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        Connection connection = context.getNetworkManager();

        if (context.getDirection() != NetworkDirection.LOGIN_TO_CLIENT && context.getDirection() != NetworkDirection.PLAY_TO_CLIENT) {
            connection.disconnect(Component.literal("Illegal packet received, terminating connection"));
            throw new IllegalStateException("Invalid packet received, aborting connection");
        }

        HandshakeHandler.LOGGER.debug(HandshakeHandler.FMLHSMARKER, "Received reset from server");

        if (!handleClear(context)) {
            return;
        }

        NetworkHooks.registerClientLoginChannel(connection);
        connection.setProtocol(ConnectionProtocol.LOGIN);
        connection.setListener(new ClientHandshakePacketListenerImpl(
                connection, Minecraft.getInstance(), Minecraft.getInstance().getCurrentServer(),  new TitleScreen(), true,null, statusMessage -> {}
        ));

        context.setPacketHandled(true);
        NetworkConstants.handshakeChannel.reply(
                new HandshakeMessages.C2SAcknowledge(),
                new NetworkEvent.Context(connection, NetworkDirection.LOGIN_TO_CLIENT, 98)
        );

        HandshakeHandler.LOGGER.debug(HandshakeHandler.FMLHSMARKER, "Reset complete");
    }

    @OnlyIn(Dist.CLIENT)
    public static boolean handleClear(NetworkEvent.Context context) {
        CompletableFuture<Void> future = context.enqueueWork(() -> {
            HandshakeHandler.LOGGER.debug(HandshakeHandler.FMLHSMARKER, "Clearing");

            // Preserve

            // Clear
            if (Minecraft.getInstance().level == null) {
                // Ensure the GameData is reverted in case the client is reset during the handshake.
                GameData.revertToFrozen();
            }

            // Clear
            Minecraft.getInstance().clearLevel(new GenericDirtMessageScreen(Component.translatable("connect.negotiating")));
            try {
                context.getNetworkManager().channel().pipeline().remove("forge:forge_fixes");
            } catch (NoSuchElementException ignored) {
            }
            try {
                context.getNetworkManager().channel().pipeline().remove("forge:vanilla_filter");
            } catch (NoSuchElementException ignored) {
            }
            // Restore
//            Minecraft.getInstance().setCurrentServer(serverData);
        });

        HandshakeHandler.LOGGER.debug(HandshakeHandler.FMLHSMARKER, "Waiting for clear to complete");
        try {
            future.get();
            HandshakeHandler.LOGGER.debug("Clear complete, continuing reset");
            return true;
        } catch (Exception ex) {
            HandshakeHandler.LOGGER.error(HandshakeHandler.FMLHSMARKER, "Failed to clear, closing connection", ex);
            context.getNetworkManager().disconnect(Component.literal("Failed to clear, closing connection"));
            return false;
        }
    }
}
