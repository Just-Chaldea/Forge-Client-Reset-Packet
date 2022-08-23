package net.foxmcloud.clientresetpacket;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;

import net.foxmcloud.clientresetpacket.network.S2CReset;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.GenericDirtMessageScreen;
import net.minecraft.client.multiplayer.ClientHandshakePacketListenerImpl;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.Connection;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.chat.TextComponent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.IExtensionPoint.DisplayTest;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import net.minecraftforge.network.HandshakeHandler;
import net.minecraftforge.network.HandshakeMessages;
import net.minecraftforge.network.NetworkConstants;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkHooks;
import net.minecraftforge.network.simple.SimpleChannel;
import net.minecraftforge.registries.GameData;

@Mod("clientresetpacket")
public class ClientReset {

	public static final Field handshakeField;
	public static final Constructor contextConstructor;
	static final Logger logger = LogManager.getLogger();
	static final Marker RESETMARKER = MarkerManager.getMarker("RESETPACKET").setParents(MarkerManager.getMarker("FMLNETWORK"));

	public static SimpleChannel handshakeChannel;

	public ClientReset() {
		IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
		bus.addListener(ClientReset::init);
		ModLoadingContext.get().registerExtensionPoint(DisplayTest.class, () -> new DisplayTest(() -> NetworkConstants.IGNORESERVERONLY, (a, b) -> true));
	}

	@SubscribeEvent
	public static void init(FMLCommonSetupEvent event) {
		if (handshakeField == null) {
			logger.error(RESETMARKER, "Failed to find FML's handshake channel. Disabling mod.");
			return;
		}
		if (contextConstructor == null) {
			logger.error(RESETMARKER, "Failed to find FML's network event context constructor. Disabling mod.");
			return;
		}
		try {
			//handshakeField.setAccessible(true);
			//contextConstructor.setAccessible(true);
			Object handshake = handshakeField.get(null);
			if (handshake instanceof SimpleChannel) {
				handshakeChannel = (SimpleChannel)handshake;
				logger.info(RESETMARKER, "Registering forge reset packet.");
				handshakeChannel.messageBuilder(S2CReset.class, 98).
				loginIndex(S2CReset::getLoginIndex, S2CReset::setLoginIndex).
				decoder(S2CReset::decode).
				encoder(S2CReset::encode).
				consumer(HandshakeHandler.biConsumerFor(ClientReset::handleReset)).
				add();
				logger.info(RESETMARKER, "Registered forge reset packet successfully.");
			}
		}
		catch (Exception e) {
			logger.error(RESETMARKER, "Caught exception when attempting to utilize FML's handshake. Disabling mod. Exception: " + e.getMessage());
		}
	}

	public static void handleReset(HandshakeHandler handler, S2CReset msg, Supplier<NetworkEvent.Context> contextSupplier) {
		NetworkEvent.Context context = contextSupplier.get();
		Connection connection = context.getNetworkManager();

		if (context.getDirection() != NetworkDirection.LOGIN_TO_CLIENT && context.getDirection() != NetworkDirection.PLAY_TO_CLIENT) {
			connection.disconnect(new TextComponent("Illegal packet received, terminating connection"));
			throw new IllegalStateException("Invalid packet received, aborting connection");
		}

		logger.info(RESETMARKER, "Received reset packet from server.");

		if (!handleClear(context)) {
			return;
		}

		NetworkHooks.registerClientLoginChannel(connection);
		connection.setProtocol(ConnectionProtocol.LOGIN);
		connection.setListener(new ClientHandshakePacketListenerImpl(
				connection, Minecraft.getInstance(), null, statusMessage -> {}
		));

		context.setPacketHandled(true);
		try {
			handshakeChannel.reply(
				new HandshakeMessages.C2SAcknowledge(),
				(NetworkEvent.Context)contextConstructor.newInstance(connection, NetworkDirection.LOGIN_TO_CLIENT, 98)
			);
		}
		catch (Exception e) {
			logger.error(RESETMARKER, "Exception occurred when attempting to reply to reset packet.  Exception: " + e.getMessage());
			context.setPacketHandled(false);
			return;
		}
		logger.info(RESETMARKER, "Reset complete.");
	}

	@OnlyIn(Dist.CLIENT)
	public static boolean handleClear(NetworkEvent.Context context) {
		CompletableFuture<Void> future = context.enqueueWork(() -> {
			logger.debug(RESETMARKER, "Clearing");

			// Preserve
			ServerData serverData = Minecraft.getInstance().getCurrentServer();

			// Clear
			if (Minecraft.getInstance().level == null) {
				// Ensure the GameData is reverted in case the client is reset during the handshake.
				GameData.revertToFrozen();
			}

			// Clear
			Minecraft.getInstance().clearLevel(new GenericDirtMessageScreen(new TextComponent("Negotiating..."/*"connect.negotiating"*/)));

			// Restore
			Minecraft.getInstance().setCurrentServer(serverData);
		});

		logger.debug(RESETMARKER, "Waiting for clear to complete");
		try {
			future.get();
			logger.debug("Clear complete, continuing reset");
			return true;
		} catch (Exception ex) {
			logger.error(RESETMARKER, "Failed to clear, closing connection", ex);
			context.getNetworkManager().disconnect(new TextComponent("Failed to clear, closing connection"));
			return false;
		}
	}

	private static Field fetchHandshakeChannel() {
		try {
			return ObfuscationReflectionHelper.findField(NetworkConstants.class, "handshakeChannel");
		}
		catch (Exception e) {
			logger.error("Exception occurred while accessing handshakeChannel: " + e.getMessage());
			return null;
		}
	}

	private static Constructor fetchNetworkEventContext() {
		try {
			return ObfuscationReflectionHelper.findConstructor(NetworkEvent.Context.class, Connection.class, NetworkDirection.class, int.class);
		}
		catch (Exception e) {
			logger.error("Exception occurred while accessing getLoginIndex: " + e.getMessage());
			return null;
		}
	}

	static {
		handshakeField = fetchHandshakeChannel();
		contextConstructor = fetchNetworkEventContext();
	}
}
