#!/usr/bin/env python3
"""Static checks for the TaCZ <-> YSM (Yes Steve Model) adapter contract (no JVM needed).

Two things are checked:

1. Every TaCZ symbol that ModernYSM's 1.20.1 TacCompat/TacBinding/TacAnimHandler used
   still exists in this tree with the expected shape. This is the surface YSM's 26.3
   port has to restore (ModernYSM `port/26.3` currently ships `rip/ysm/compat/**` as
   excluded stubs, so nothing on their side calls these yet).
2. Our own integration wiring: the YsmCompat facade, the client config gate, the
   first-person handshake guard and the per-tick reset.

This is NOT a compile, a Mixin application test, or an in-game test.
Run: python3 scripts/verify_ysm_compat.py
"""
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parent.parent
JAVA = ROOT / "src/main/java"
FACADE = JAVA / "com/tacz/guns/compat/ysm/YsmCompat.java"
MIXIN = JAVA / "com/tacz/guns/mixin/client/FirstPersonHandsAndItemsRendererMixin.java"
CONFIG = JAVA / "com/tacz/guns/config/client/RenderConfig.java"
CLIENT = JAVA / "cn/sh1rocu/tacz/client/TaCZFabricClient.java"


def read(path):
    return path.read_text(encoding="utf-8")


def code(path):
    return re.sub(r"/\*.*?\*/|//[^\n]*", "", read(path), flags=re.S)


class YsmContractChecks(unittest.TestCase):
    # ---- 1. TaCZ side of the contract YSM 1.20.1 used -------------------

    def test_gun_item_api_surface(self):
        source = code(JAVA / "com/tacz/guns/api/item/IGun.java")
        self.assertIn("static IGun getIGunOrNull(", source)
        self.assertIn("static boolean mainHandHoldGun(LivingEntity", source)
        # YSM 1.20.1 calls the old name; it must stay present (deprecated is fine).
        self.assertIn("static boolean mainhandHoldGun(LivingEntity", source)
        self.assertIn("static FireMode getMainHandFireMode(LivingEntity", source)
        self.assertIn("static FireMode getMainhandFireMode(LivingEntity", source)
        self.assertIn("Identifier getGunId(ItemStack gun)", source)

    def test_gun_operator_signals(self):
        source = code(JAVA / "com/tacz/guns/api/entity/IGunOperator.java")
        self.assertIn("static IGunOperator fromLivingEntity(LivingEntity entity)", source)
        for signal in ("long getSynShootCoolDown()", "long getSynMeleeCoolDown()",
                       "long getSynDrawCoolDown()", "ReloadState getSynReloadState()",
                       "float getSynAimingProgress()"):
            self.assertIn(signal, source)
        reload_state = code(JAVA / "com/tacz/guns/api/entity/ReloadState.java")
        self.assertIn("getCountDown()", reload_state)
        client_operator = code(JAVA / "com/tacz/guns/api/client/gameplay/IClientPlayerGunOperator.java")
        self.assertIn("long getClientShootCoolDown()", client_operator)

    def test_gun_type_and_fire_mode(self):
        api = code(JAVA / "com/tacz/guns/api/TimelessAPI.java")
        self.assertIn("getCommonGunIndex(Identifier gunId)", api)
        index = code(JAVA / "com/tacz/guns/resource/index/CommonGunIndex.java")
        self.assertIn("public String getType()", index)
        tab = code(JAVA / "com/tacz/guns/api/item/GunTabType.java")
        for tab_type in ("PISTOL", "RIFLE", "SNIPER", "SHOTGUN", "SMG", "RPG", "MG"):
            self.assertIn(tab_type, tab)
        fire_mode = code(JAVA / "com/tacz/guns/api/item/gun/FireMode.java")
        self.assertIn("UNKNOWN", fire_mode)

    def test_self_flag_fields_used_by_ysm_tacanimhandler(self):
        for name in ("MuzzleFlashRender", "ShellRender"):
            source = code(JAVA / f"com/tacz/guns/client/model/functional/{name}.java")
            self.assertIn("public static boolean isSelf", source, name)

    def test_gun_events_are_fabric_events(self):
        # YSM 1.20.1 used Forge @SubscribeEvent; on Fabric the same hooks are
        # Event<Callback> with CALLBACK.invoker().post(...).
        for name in ("GunFireEvent", "GunMeleeEvent", "GunReloadEvent"):
            source = code(JAVA / f"com/tacz/guns/api/event/common/{name}.java")
            self.assertIn("EventFactory.createArrayBacked", source, name)
            full = read(JAVA / f"com/tacz/guns/api/event/common/{name}.java")
            self.assertIn("public static final Event<Callback> CALLBACK", full, name)
        fire = code(JAVA / "com/tacz/guns/api/event/common/GunFireEvent.java")
        self.assertIn("public LivingEntity getShooter()", fire)
        reload_event = code(JAVA / "com/tacz/guns/api/event/common/GunReloadEvent.java")
        self.assertIn("public LivingEntity getEntity()", reload_event)

    def test_item_model_dispatches_any_display_context(self):
        # YSM renders the gun with ItemRenderer.renderStatic(stack, FIXED, ...);
        # that only shows a gun if the dynamic item model forwards the context.
        source = code(JAVA / "com/tacz/guns/client/renderer/item/TaczDynamicItemModel.java")
        self.assertIn("ItemDisplayContext displayContext", source)
        self.assertIn("layer.setupSpecialModel(SPECIAL_RENDERER, argument)", source)
        self.assertIn("renderer.render(argument.stack(), argument.displayContext(),", source)

    # ---- 2. our side of the adapter -------------------------------------

    def test_facade_exposes_every_molang_variable(self):
        source = code(FACADE)
        for constant in ("VAR_HOLD_GUN", "VAR_GUN_TYPE", "VAR_GUN_ID", "VAR_IS_FIRE",
                         "VAR_IS_AIM", "VAR_IS_RELOAD", "VAR_IS_MELEE", "VAR_IS_DRAW",
                         "VAR_FIRE_MODE"):
            self.assertIn(f'String {constant} = "tac_', source, constant)
        for getter in ("isHoldingGun", "getGunType", "getGunId", "isFiring", "isAiming",
                       "isReloading", "isMelee", "isDrawing", "getFireMode"):
            self.assertIn(f"{getter}(", source, getter)
        self.assertIn('MOD_ID = "yes_steve_model"', source)

    def test_facade_never_uses_the_unchecked_cast(self):
        source = code(FACADE)
        self.assertNotIn("IGunOperator.fromLivingEntity", source)
        self.assertIn("entity instanceof IGunOperator operator", source)
        self.assertIn("IClientPlayerGunOperator.fromLocalPlayer(localPlayer)", source)

    def test_handshake_is_opt_in_and_expires_each_tick(self):
        facade = code(FACADE)
        self.assertIn("private static volatile boolean externalGunRender;", facade)
        self.assertIn("return externalGunRender && isEnabled();", facade)
        self.assertIn("externalGunRender = false;", facade)
        client = code(CLIENT)
        self.assertIn("YsmCompat.clearExternalGunRender()", client)

    def test_first_person_viewmodel_yields_to_the_external_renderer(self):
        source = code(MIXIN)
        self.assertIn("if (YsmCompat.isExternalGunRenderActive())", source)
        # The guard must sit on the TACZ viewmodel path, i.e. after the ordinary-item
        # pass-through and before the geo renderer submit.
        guard = source.index("YsmCompat.isExternalGunRenderActive()")
        self.assertLess(source.index("original.call(instance, playerState"), guard)
        self.assertLess(guard, source.index("geoRenderer.renderFirstPerson("))

    def test_config_gate_exists_and_defaults_on(self):
        config = read(CONFIG)
        self.assertIn("public static ForgeConfigSpec.BooleanValue YSM_COMPAT;", config)
        self.assertRegex(config, r'(?s)YSM_COMPAT = builder.*?\.define\("YsmCompat", true\);')


if __name__ == "__main__":
    unittest.main(verbosity=2)
