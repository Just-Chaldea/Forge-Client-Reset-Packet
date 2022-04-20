package gg.chaldea.client.reset.packet.network;

import net.minecraft.network.PacketBuffer;

import static net.minecraftforge.fml.network.FMLHandshakeMessages.LoginIndexedMessage;

public class S2CReset extends LoginIndexedMessage {

    public S2CReset() {
        super();
    }

    public void encode(PacketBuffer buffer) {

    }

    public static S2CReset decode(PacketBuffer buffer) {
        return new S2CReset();
    }
}
