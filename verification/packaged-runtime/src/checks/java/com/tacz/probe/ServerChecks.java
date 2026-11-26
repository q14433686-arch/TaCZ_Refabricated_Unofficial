package com.tacz.probe;

import net.fabricmc.api.ModInitializer;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import java.util.function.Consumer;

/** Use vanilla test_instance data and test functions. The development-only annotation scanner
 * reflects on a named enum constant that does not exist under its source name in production. */
public final class ServerChecks implements ModInitializer {
    private static void test(String name, Consumer<GameTestHelper> method) {
        Registry.register(BuiltInRegistries.TEST_FUNCTION, Identifier.fromNamespaceAndPath("tacz_server_checks", name), method);
    }

    @Override public void onInitialize() {
        test("transfer_transactions_and_component_identity", AmmoStorageSmoke::transferTransactionsAndComponentIdentity);
        test("block_loot_and_both_crafting_upgrades_retain_stock", AmmoStorageSmoke::blockLootAndBothCraftingUpgradesRetainStock);
        test("loaded_catalog", CatalogChecks::loadedCatalog);
        test("ammo_block_policies", AmmoPolicySmoke::blockPolicies);
        test("ammo_entity_policies", AmmoPolicySmoke::entityPolicies);
        test("fire_callback_phases", EventPhaseSmoke::fireCallbackPhases);
        test("target_profile_roundtrip", com.tacz.guns.block.entity.TargetIdentitySmoke::explicitSkinAndIncompleteIdentityRoundTrip);
        test("target_identity_generations", com.tacz.guns.block.entity.TargetIdentitySmoke::lateProfileLookupCannotReplaceChangedOwner);
        test("connected_case_locks_and_survival_contents", RackChecks::connectedCaseLocksAndSurvivalContents);
        test("oem_is_not_an_inventory_item", RefitChecks::oemIsNotAnInventoryItem);
        test("refit_options_persist_and_reject_incompatible_transitions", RefitChecks::refitOptionsPersistAndRejectIncompatibleTransitions);
        test("settle_every_stock_table_product", WorkbenchChecks::settleEveryStockTableProduct);
        test("ordinary_table_crafts_workbench", WorkbenchChecks::ordinaryTableCraftsWorkbench);
        test("viewer_selection_honors_the_open_table", WorkbenchChecks::viewerSelectionHonorsTheOpenTable);
        test("both_viewer_directions_use_the_same_indexed_stacks", WorkbenchChecks::bothViewerDirectionsUseTheSameIndexedStacks);
        test("reload_transition_and_aim_gate", GunActionSmoke::reloadTransitionAndAimGate);
        test("quick_unload_conserves_rounds_and_honors_scripts", GunActionSmoke::quickUnloadConservesRoundsAndHonorsScripts);
        test("no_refund_into_an_unrelated_inventory", GunActionSmoke::noRefundIntoAnUnrelatedInventory);
        test("default_script_feeds_conserve_reserve", DefaultReloadSmoke::defaultScriptFeedsConserveReserve);
        test("bad_reload_and_nested_failure_do_not_announce_success", ScriptGuardSmoke::badReloadAndNestedFailureDoNotAnnounceSuccess);
        test("queued_callbacks_belong_to_adraw_and_astack", ScriptGuardSmoke::queuedCallbacksBelongToADrawAndAStack);
        test("unselected_minigun_cools_without_ticking_held_gun_twice", ScriptGuardSmoke::unselectedMinigunCoolsWithoutTickingHeldGunTwice);
    }
}
