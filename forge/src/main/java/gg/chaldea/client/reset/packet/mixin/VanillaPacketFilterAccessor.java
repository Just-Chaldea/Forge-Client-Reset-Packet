package gg.chaldea.client.reset.packet.mixin;

import net.minecraft.network.NetworkManager;
import net.minecraftforge.network.VanillaPacketFilter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(VanillaPacketFilter.class)
public interface VanillaPacketFilterAccessor {

    @Invoker("isNecessary")
    boolean invokeIsNecessary(NetworkManager manager);
}
