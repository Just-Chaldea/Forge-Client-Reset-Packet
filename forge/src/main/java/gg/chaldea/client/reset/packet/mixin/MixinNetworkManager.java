package gg.chaldea.client.reset.packet.mixin;

import io.netty.channel.Channel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.GenericDirtMessageScreen;
import net.minecraft.client.gui.screens.multiplayer.ServerSelectionList;
import net.minecraft.client.gui.screens.multiplayer.WarningScreen;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.client.resources.language.I18n;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.internal.TextComponentMessageFormatHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(Connection.class)
public class MixinNetworkManager {

    @Shadow private Channel channel;
    @Shadow private Component disconnectedReason;

    /**
     * @author
     * @reason
     */

    @Overwrite
    @OnlyIn(Dist.CLIENT)
    public void disconnect(Component p_150718_1_) {
        if (this.channel.isOpen()) {
            this.channel.close().awaitUninterruptibly();
            this.disconnectedReason = p_150718_1_;

            if (Minecraft.getInstance().screen instanceof GenericDirtMessageScreen ) {
                Component title = Minecraft.getInstance().screen.getTitle();

                if (title instanceof TranslatableComponent &&
                        ((TranslationTextComponent) title).getKey().equals("connect.negotiating")) {
                    Minecraft.getInstance().setScreen(new DisconnectedScreen(new ServerSelectionList(new MainMenuScreen()), new StringTextComponent(""), this.disconnectedReason));
                }
            }
        }
    }
}
