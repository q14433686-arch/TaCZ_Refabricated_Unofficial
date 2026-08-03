package cn.sh1rocu.tacz.mixin.client;

import cn.sh1rocu.tacz.api.event.AddPackFindersEvent;
import cn.sh1rocu.tacz.api.event.InputEvent;
import cn.sh1rocu.tacz.api.mixin.PackRepositoryExtension;
import cn.sh1rocu.tacz.util.forge.ClientHooks;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.main.GameConfig;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
    @Shadow
    public abstract PackRepository getResourcePackRepository();

    @Shadow
    @Final
    public Options options;

    @Shadow
    @Final
    public ParticleEngine particleEngine;

    @Shadow
    @Nullable
    public MultiPlayerGameMode gameMode;

    @Shadow
    @Nullable
    public LocalPlayer player;

    @Shadow
    @Nullable
    public HitResult hitResult;

    @Inject(method = "<init>", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/packs/repository/PackRepository;reload()V"))
    private void tacz$addPacks(GameConfig gameConfig, CallbackInfo ci) {
        AddPackFindersEvent event = new AddPackFindersEvent(PackType.CLIENT_RESOURCES, ((PackRepositoryExtension) this.getResourcePackRepository())::tacz$addPackFinder, false);
        AddPackFindersEvent.CALLBACK.invoker().onAddPackFinders(event);
    }

    /**
     * 退出路径之一：被服务器踢出 / 连接断开。
     *
     * <p>由 {@code ClientPacketListener} 调到 {@code Minecraft#clearClientLevel}。</p>
     */
    @Inject(method = "clearClientLevel(Lnet/minecraft/client/gui/screens/Screen;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GameRenderer;resetData()V"))
    private void tacz$disconnect(Screen screen, CallbackInfo ci) {
        ClientHooks.firePlayerLogout(this.gameMode, this.player);
    }

    /**
     * 退出路径之二：<b>玩家主动退出到标题画面</b>。
     *
     * <h2>为什么必须单独注入</h2>
     * 26.2 的退出有两条互不相干的路径（字节码逐条确认）：
     * <ol>
     *   <li>被踢/断线 → {@code ClientPacketListener} → {@code clearClientLevel}；</li>
     *   <li>主动退出 → {@code Minecraft#disconnect(Screen,boolean,boolean)}，
     *       它自己完成收尾（{@code GameRenderer#resetData} → {@code gameMode=null}
     *       → {@code level=null} → {@code player=null}），
     *       <b>整个过程不经过 {@code clearClientLevel}</b>。</li>
     * </ol>
     * 也就是说此前只挂在路径 1 上的登出事件，在玩家最常用的「退出到标题」时
     * <b>从来没有被触发过</b>，导致所有依赖它做清理的静态状态跨存档残留。
     *
     * <p>具体造成的可见 bug：{@code InventoryEvent} 的 {@code oldHotbarSelected}
     * 不被复位，于是「退出后再进同一个存档」时切枪检测判定为「槽位没变」，
     * 首次 draw 包永远不发；服务端 {@code ShooterDataHolder#currentGunItem}
     * 恒为 null，{@code LivingEntityShoot#shoot} 直接返回 {@code NOT_DRAW}——
     * 表现为能扣扳机但子弹不减、无伤害、无曳光弹。
     * 而「首次进入」或「交叉进入不同存档」因为槽位/物品恰好不同，反而正常。
     *
     * <p>注入点选 {@code GameRenderer#resetData()}：此刻 {@code player} 与
     * {@code gameMode} 都还没被置空（置空发生在其后），事件能拿到有效引用，
     * 与路径 1 的时机语义保持一致。</p>
     */
    @Inject(method = "disconnect(Lnet/minecraft/client/gui/screens/Screen;ZZ)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GameRenderer;resetData()V"))
    private void tacz$disconnectToTitle(Screen screen, boolean keepResourcePacks, boolean showSavingScreen, CallbackInfo ci) {
        ClientHooks.firePlayerLogout(this.gameMode, this.player);
    }

    @Unique
    private InputEvent.InteractionKeyMappingTriggered tacz$onClickInput(int button, KeyMapping keyMapping, InteractionHand hand) {
        InputEvent.InteractionKeyMappingTriggered event = new InputEvent.InteractionKeyMappingTriggered(button, keyMapping, hand);
        InputEvent.InteractionKeyMappingTriggered.EVENT.invoker().onInteractionKeyMappingTriggered(event);
        return event;
    }

    @Inject(method = "continueAttack", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/BlockHitResult;getDirection()Lnet/minecraft/core/Direction;"), cancellable = true)
    private void tacz$onClickInputEvent(boolean leftClick, CallbackInfo ci, @Local BlockHitResult blockHitResult, @Local BlockPos blockPos, @Share("event") LocalRef<InputEvent.InteractionKeyMappingTriggered> eventRef) {
        InputEvent.InteractionKeyMappingTriggered inputEvent = tacz$onClickInput(0, this.options.keyAttack, InteractionHand.MAIN_HAND);
        eventRef.set(inputEvent);
        if (inputEvent.isCanceled()) {
            if (inputEvent.shouldSwingHand()) {
                this.player.swing(InteractionHand.MAIN_HAND);
            }
            ci.cancel();
        }
    }

    @ModifyExpressionValue(method = "continueAttack", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;continueDestroyBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;)Z"))
    private boolean tacz$checkHandSwing(boolean original, @Share("event") LocalRef<InputEvent.InteractionKeyMappingTriggered> eventRef) {
        return original && eventRef.get().shouldSwingHand();
    }

    @Inject(method = "startAttack", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/HitResult;getType()Lnet/minecraft/world/phys/HitResult$Type;"), cancellable = true)
    private void tacz$onAttackClickInputEvent(CallbackInfoReturnable<Boolean> cir, @Share("inputEvent") LocalRef<InputEvent.InteractionKeyMappingTriggered> inputEvent, @Local boolean flag) {
        inputEvent.set(tacz$onClickInput(0, this.options.keyAttack, InteractionHand.MAIN_HAND));

        if (inputEvent.get().isCanceled()) {
            if (inputEvent.get().shouldSwingHand())
                this.player.swing(InteractionHand.MAIN_HAND);

            cir.setReturnValue(flag);
        }
    }

    @WrapWithCondition(method = "startAttack", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;swing(Lnet/minecraft/world/InteractionHand;)V"))
    private boolean tacz$swingHandIfEventPermits(LocalPlayer instance, InteractionHand interactionHand, @Share("inputEvent") LocalRef<InputEvent.InteractionKeyMappingTriggered> inputEvent) {
        return inputEvent.get() == null || inputEvent.get().shouldSwingHand();
    }

    @Inject(method = "startUseItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getItemInHand(Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/world/item/ItemStack;", ordinal = 0), cancellable = true)
    private void tacz$callForgeUseInputEvent(CallbackInfo ci, @Share("inputEvent") LocalRef<InputEvent.InteractionKeyMappingTriggered> inputEvent, @Local InteractionHand hand) {
        inputEvent.set(tacz$onClickInput(1, this.options.keyUse, hand));

        if (inputEvent.get().isCanceled()) {
            if (inputEvent.get().shouldSwingHand())
                this.player.swing(hand);

            ci.cancel();
        }
    }

    @Inject(method = "pickBlockOrEntity", at = @At("HEAD"), cancellable = true)
    private void tacz$callInteractionPickInput(CallbackInfo ci) {
        if (this.hitResult == null || this.hitResult.getType() == HitResult.Type.MISS) {
            return;
        }
        if (tacz$onClickInput(2, this.options.keyPickItem, InteractionHand.MAIN_HAND).isCanceled()) {
            ci.cancel();
        }
    }
}
