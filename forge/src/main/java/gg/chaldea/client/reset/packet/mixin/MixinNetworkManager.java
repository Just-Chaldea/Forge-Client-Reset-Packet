package gg.chaldea.client.reset.packet.mixin;

import io.netty.channel.Channel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.DirtMessageScreen;
import net.minecraft.client.gui.screen.DisconnectedScreen;
import net.minecraft.client.gui.screen.MainMenuScreen;
import net.minecraft.client.gui.screen.MultiplayerScreen;
import net.minecraft.network.NetworkManager;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(NetworkManager.class)
public class MixinNetworkManager {

    @Shadow private Channel channel;
    @Shadow private ITextComponent disconnectedReason;

    /**
     * @author
     * @reason
     */
    @Overwrite
    @OnlyIn(Dist.CLIENT)
    public void disconnect(ITextComponent p_150718_1_) {
        if (this.channel.isOpen()) {
            this.channel.close().awaitUninterruptibly();
            this.disconnectedReason = p_150718_1_;

            if (Minecraft.getInstance().screen instanceof DirtMessageScreen) {
                Minecraft.getInstance().setScreen(new DisconnectedScreen(new MultiplayerScreen(new MainMenuScreen()), new StringTextComponent(""), this.disconnectedReason));
            }
        }
    }
}
