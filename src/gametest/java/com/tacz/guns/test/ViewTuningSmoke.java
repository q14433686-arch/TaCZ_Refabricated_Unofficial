package com.tacz.guns.test;

import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.builder.GunItemBuilder;
import com.tacz.guns.client.event.CameraSetupEvent;
import com.tacz.guns.client.renderer.item.GunItemRendererWrapper;
import com.tacz.guns.client.tuning.TuningProfile;
import com.tacz.guns.client.tuning.TuningProfiles;
import com.tacz.guns.config.client.ViewTuningConfig.Axis;
import com.tacz.guns.GunMod;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import org.joml.Vector3f;

/**
 * C1 回执（26.1.2 没有 26.2/26.3 的 {@code ViewTuningSmoke}，这里新增等价的手部枪口断言）。
 *
 * <p>上游 1.2.0 {@code FirstPersonRenderMath#muzzleOffset}：缓存的枪口偏移保留视图空间深度，
 * 横纵分量按 {@code tan(worldFov/2)/tan(itemFov/2)} 缩放。旧实现缩放深度，屏幕位置相同但曳光弹
 * 近端会被推到错误深度。这里直接驱动账户调参的基座 XYZ，读取 {@link GunItemRendererWrapper#muzzleRenderOffset}
 * 的前后差值，按同一 FOV 比例 k 断言：横纵 = XYZ×k、深度 = XYZ.z。
 *
 * <p>不经过 ViewTuningScreen 的 UI，直接改写 {@link TuningProfiles#current()} 的基座 XYZ ——
 * {@code TuningValues.visible()} 在编辑器未打开时返回的正是该基座，手部渲染每帧据此重算枪口偏移。
 */
final class ViewTuningSmoke {
    private static final String UI = "gui.tacz.view_tuning.";

    static void run(ClientGameTestContext ctx, TestSingleplayerContext world) {
        var id = Identifier.fromNamespaceAndPath("tacz", "ak47");
        var camera = ctx.computeOnClient(mc -> mc.options.getCameraType());
        var backup = ctx.computeOnClient(mc -> {
            var profile = TuningProfiles.current().copy();
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            return profile;
        });
        try {
            // AK47 带枪口定位器。等持枪姿态稳定（不在抽枪冷却/状态锁内）。
            world.getServer().runOnServer(s -> s.getPlayerList().getPlayers().getFirst().setItemInHand(
                    InteractionHand.MAIN_HAND, GunItemBuilder.create().setId(id).build()));
            ctx.waitFor(mc -> mc.player.getMainHandItem().getItem() instanceof IGun
                    && !IClientPlayerGunOperator.fromLocalPlayer(mc.player).getDataHolder().clientStateLock
                    && IGunOperator.fromLivingEntity(mc.player).getSynDrawCoolDown() == 0);
            ctx.waitTicks(25);

            Vector3f before = muzzle(ctx);

            // 写入已知 XYZ（与 1.21.11 PresentationProbe.verifyHandPreview 同值）。
            ctx.runOnClient(mc -> {
                var base = TuningProfiles.current().base();
                base.set(Axis.X.option, .237d);
                base.set(Axis.Y.option, -.113d);
                base.set(Axis.Z.option, -.330d);
            });
            // 等到手部渲染用新 XYZ 重算枪口偏移。
            ctx.waitFor(mc -> Math.abs(GunItemRendererWrapper.muzzleRenderOffset.x - before.x) > 0.01f);
            Vector3f delta = muzzle(ctx).sub(before);

            // 从渲染器使用的同一 FOV 动力学读取比例 k（世界已暂停，两者均已稳定）。
            float k = ctx.computeOnClient(mc -> {
                double item = Math.tan(Math.toRadians(CameraSetupEvent.ITEM_MODEL_FOV_DYNAMICS.get()) / 2);
                double worldTan = Math.tan(Math.toRadians(CameraSetupEvent.WORLD_FOV_DYNAMICS.get()) / 2);
                return item > 1.0e-6 ? (float) (worldTan / item) : 1f;
            });
            check(Math.abs(delta.x - .237 * k) < .035, "Muzzle X did not scale by fov ratio: delta=" + delta + ", k=" + k);
            check(Math.abs(delta.y + .113 * k) < .035, "Muzzle Y did not scale by fov ratio: delta=" + delta + ", k=" + k);
            check(Math.abs(delta.z + .330) < .035, "Muzzle depth changed; expected unchanged view depth: delta=" + delta);

            GunMod.LOGGER.info("PORT_HAND_MUZZLE: paused AK47 XYZ (.237,-.113,-.330): delta={}, fovScale={}; lateral x/y, view depth preserved",
                    delta, k);
        } finally {
            ctx.runOnClient(mc -> {
                var base = TuningProfiles.current().base();
                base.set(Axis.X.option, backup.base().get(Axis.X.option));
                base.set(Axis.Y.option, backup.base().get(Axis.Y.option));
                base.set(Axis.Z.option, backup.base().get(Axis.Z.option));
                mc.options.setCameraType(camera);
            });
        }
    }

    private static Vector3f muzzle(ClientGameTestContext ctx) {
        return ctx.computeOnClient(mc -> new Vector3f(GunItemRendererWrapper.muzzleRenderOffset));
    }

    private static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }

    private ViewTuningSmoke() {}
}
