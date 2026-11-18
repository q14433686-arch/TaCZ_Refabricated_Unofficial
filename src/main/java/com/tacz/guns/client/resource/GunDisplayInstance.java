package com.tacz.guns.client.resource;

import cn.sh1rocu.tacz.compat.meshloader.model.TaczPolyMeshGunModel;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.tacz.guns.client.resource.pojo.display.SoundData;
import com.tacz.guns.GunMod;
import com.tacz.guns.api.client.animation.AnimationController;
import com.tacz.guns.api.client.animation.Animations;
import com.tacz.guns.api.client.animation.ObjectAnimation;
import com.tacz.guns.api.client.animation.gltf.AnimationStructure;
import com.tacz.guns.api.client.animation.statemachine.LuaAnimationStateMachine;
import com.tacz.guns.api.client.animation.statemachine.LuaStateMachineFactory;
import com.tacz.guns.api.item.gun.FireMode;
import com.tacz.guns.client.animation.statemachine.GunAnimationStateContext;
import com.tacz.guns.client.model.BedrockGunModel;
import com.tacz.guns.client.model.BedrockAmmoModel;
import com.tacz.guns.client.resource.pojo.model.BedrockVersion;
import com.tacz.guns.client.resource.pojo.animation.bedrock.BedrockAnimationFile;
import com.tacz.guns.client.resource.pojo.display.LaserConfig;
import com.tacz.guns.client.resource.pojo.display.ammo.AmmoParticle;
import com.tacz.guns.client.resource.pojo.display.ammo.HitSurfaceParticle;
import com.tacz.guns.client.resource.pojo.display.gun.*;
import com.tacz.guns.client.sound.GunSoundPreload;
import com.tacz.guns.config.client.ResourceConfig;
import com.tacz.guns.sound.SoundManager;
import com.tacz.guns.util.ColorHex;
import it.unimi.dsi.fastutil.ints.Int2ObjectArrayMap;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.commands.arguments.ParticleArgument;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.Nullable;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.lib.jse.CoerceJavaToLua;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * 经过处理和校验的枪械显示数据
 */
@Environment(EnvType.CLIENT)
public class GunDisplayInstance {
    private final Identifier displayId;
    private final GunDisplay display;
    private final Object loadLock = new Object();

    // 加载标志位
    private volatile boolean modelLoaded = false;
    private volatile boolean animationLoaded = false;

    // 错误标志位
    private volatile boolean modelLoadFailed = false;
    private volatile boolean animationLoadFailed = false;

    // 异步加载任务
    private volatile CompletableFuture<Void> modelWarmUpTask = null;
    private volatile CompletableFuture<Void> animationWarmUpTask = null;

    private String thirdPersonAnimation = "empty";
    private BedrockGunModel gunModel;
    private final DisplayBake<Pair<BedrockGunModel, Identifier>> externalHigh;
    private final DisplayBake<Pair<BedrockGunModel, Identifier>> externalLod;

    private LuaAnimationStateMachine<GunAnimationStateContext> animationStateMachine;
    private @Nullable LuaTable stateMachineParam;

    private @Nullable Identifier playerAnimator3rd = Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "rifle_default.player_animation");
    private boolean is3rdFixedHand = false;
    private Map<String, SoundData> sounds = Maps.newHashMap();
    private List<Identifier> preloadSounds = Lists.newArrayList();
    private GunTransform transform = GunTransform.getDefault();

    private Identifier modelTexture;
    private Identifier slotTexture = MissingTextureAtlasSprite.getLocation();
    private Identifier hudTexture = MissingTextureAtlasSprite.getLocation();
    private @Nullable Identifier hudEmptyTexture;

    private @Nullable ShellEjection shellEjection;
    private @Nullable BedrockAmmoModel shellModel;
    private @Nullable MuzzleFlash muzzleFlash;
    private LayerGunShow offhandShow = new LayerGunShow();
    private @Nullable Int2ObjectArrayMap<LayerGunShow> hotbarShow;
    private float ironZoom;
    private static final Identifier LEGACY_ADS = Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "legacy");
    private AdsPreset adsPreset;
    private float modelFov;
    private float zoomModelFov;
    private boolean showCrosshair = false;
    private @Nullable AmmoParticle particle;
    private float @Nullable [] tracerColor = null;
    private EnumMap<FireMode, ControllableData> controllableData = new EnumMap<>(FireMode.class);
    private AmmoCountStyle ammoCountStyle = AmmoCountStyle.NORMAL;
    private DamageStyle damageStyle = DamageStyle.PER_PROJECTILE;
    private @Nullable LaserConfig laserConfig;
    private boolean enableTransparency;

    GunDisplayInstance(Identifier displayId, GunDisplay display) {
        this.displayId = displayId;
        this.display = Objects.requireNonNull(display, "display");
        long revision = ClientIndexManager.revision();
        externalHigh = externalBake(false, revision);
        externalLod = externalBake(true, revision);
        initBase(display);
        if (!ResourceConfig.ENABLE_LAZY_CLIENT_ASSET_LOAD.get()) {
            ensureAnimationLoaded();
        }
    }

    public static GunDisplayInstance create(Identifier displayId, GunDisplay display) throws IllegalArgumentException {
        return new GunDisplayInstance(displayId, display);
    }

    // 后台预热
    public void warmUpModel() {
        if (!ResourceConfig.ENABLE_LAZY_CLIENT_ASSET_LOAD.get()) {
            ensureModelLoaded();
            return;
        }
        if (modelLoaded || modelWarmUpTask != null) {
            return;
        }
        synchronized (loadLock) {
            if (modelLoaded || modelWarmUpTask != null) {
                return;
            }
            scheduleModelWarmUpLocked();
        }
    }

    private DisplayBake<Pair<BedrockGunModel, Identifier>> externalBake(boolean lod, long revision) {
        return new DisplayBake<>(ClientAssetLoadDispatcher.executor(), () -> revision == ClientIndexManager.revision(),
                () -> GunRigBaker.bake(display, lod, true),
                error -> GunMod.LOGGER.warn("Failed to bake external {} rig for {}", lod ? "LOD" : "main", displayId, error));
    }

    public void warmUpLod() {
        if (ResourceConfig.ENABLE_LAZY_CLIENT_ASSET_LOAD.get()) externalLod.request();
        else externalLod.loadNow();
    }

    public void warmUpRuntime() {
        if (!ResourceConfig.ENABLE_LAZY_CLIENT_ASSET_LOAD.get()) {
            ensureAnimationLoaded();
            return;
        }
        if (animationLoaded || animationWarmUpTask != null) {
            return;
        }
        synchronized (loadLock) {
            if (animationLoaded || animationWarmUpTask != null) {
                return;
            }
            scheduleAnimationWarmUpLocked();
        }
    }

    // 确保所需资源完成，没完成阻塞一下
    private void ensureModelLoaded() {
        if (modelLoaded || modelLoadFailed) {
            return;
        }
        CompletableFuture<Void> task = modelWarmUpTask;
        if (task == null) {
            try {
                loadModelIfNecessary();
            } catch (Throwable throwable) {
                handleModelLoadFailure(throwable);
            }
            return;
        }
        try {
            task.join();
        } catch (CompletionException exception) {
            handleModelLoadFailure(exception.getCause() == null ? exception : exception.getCause());
        }
    }

    private void ensureAnimationLoaded() {
        if (animationLoaded || animationLoadFailed) {
            return;
        }
        ensureModelLoaded();
        if (!modelLoaded) {
            animationLoadFailed = true;
            return;
        }
        CompletableFuture<Void> task = animationWarmUpTask;
        if (task == null) {
            try {
                loadAnimationAfterModelReady();
            } catch (Throwable throwable) {
                handleAnimationLoadFailure(throwable);
            }
            return;
        }
        try {
            task.join();
        } catch (CompletionException exception) {
            handleAnimationLoadFailure(exception.getCause() == null ? exception : exception.getCause());
        }
    }

    // 加载任务
    private void loadModelIfNecessary() {
        if (modelLoaded) {
            return;
        }
        synchronized (loadLock) {
            if (modelLoaded) {
                return;
            }
            checkTextureAndModel(display);
            checkTextShow(display);
            bakeShellOverride();
            modelLoaded = true;
        }
    }

    private CompletableFuture<Void> scheduleModelWarmUpLocked() {
        if (modelLoaded) {
            return CompletableFuture.completedFuture(null);
        }
        if (modelWarmUpTask == null) {
            modelWarmUpTask = CompletableFuture.runAsync(this::loadModelIfNecessary, ClientAssetLoadDispatcher.executor());
            modelWarmUpTask.whenComplete((unused, throwable) -> {
                if (throwable != null) {
                    handleModelLoadFailure(throwable);
                }
            });
        }
        return modelWarmUpTask;
    }

    private CompletableFuture<Void> scheduleAnimationWarmUpLocked() {
        if (animationLoaded) {
            return CompletableFuture.completedFuture(null);
        }
        if (animationWarmUpTask == null) {
            // 动画任务尝试挂到模型加载后面
            CompletableFuture<Void> modelTask = scheduleModelWarmUpLocked();
            animationWarmUpTask = modelTask.thenRunAsync(this::loadAnimationAfterModelReady, ClientAssetLoadDispatcher.executor());
            animationWarmUpTask.whenComplete((unused, throwable) -> {
                if (throwable != null) {
                    handleAnimationLoadFailure(throwable);
                }
            });
        }
        return animationWarmUpTask;
    }

    private void loadAnimationAfterModelReady() {
        if (animationLoaded) {
            return;
        }
        synchronized (loadLock) {
            if (animationLoaded) {
                return;
            }
            if (!modelLoaded) {
                throw new IllegalStateException("Gun animation requires model to be loaded first");
            }
            checkAnimation(display);
            animationLoaded = true;
        }
    }

    private void handleModelLoadFailure(Throwable throwable) {
        boolean shouldLog = false;
        synchronized (loadLock) {
            if (!modelLoaded) {
                shouldLog = !modelLoadFailed;
                modelWarmUpTask = null;
                modelLoadFailed = true;
            }
        }
        if (shouldLog) {
            GunMod.LOGGER.warn("Failed to load gun model {}", displayId, throwable);
        }
    }

    private void handleAnimationLoadFailure(Throwable throwable) {
        boolean shouldLog = false;
        synchronized (loadLock) {
            if (!animationLoaded) {
                shouldLog = !animationLoadFailed;
                animationWarmUpTask = null;
                animationLoadFailed = true;
            }
        }
        if (shouldLog) {
            GunMod.LOGGER.warn("Failed to load gun animation runtime {}", displayId, throwable);
        }
    }

    // 基础数据直接读
    private void initBase(GunDisplay display) {
        Identifier preset = display.getAdsPreset();
        adsPreset = ClientAssetsManager.INSTANCE.getAdsPreset(preset == null ? LEGACY_ADS : preset);
        if (preset != null && adsPreset == null) {
            GunMod.LOGGER.warn("Missing ADS preset {} in display {}; using {}", preset, displayId, LEGACY_ADS);
            adsPreset = ClientAssetsManager.INSTANCE.getAdsPreset(LEGACY_ADS);
        }
        checkSlotTexture(display);
        checkHUDTexture(display);
        checkSounds(display);
        checkTransform(display);
        checkShellEjection(display);
        checkGunAmmo(display);
        checkMuzzleFlash(display);
        checkLayerGunShow(display);
        checkIronZoom(display);
        modelFov = validFov(display.getModelFov());
        zoomModelFov = validFov(display.getZoomModelFov());
        thirdPersonAnimation = StringUtils.defaultIfBlank(display.getThirdPersonAnimation(), "empty");
        if (display.getPlayerAnimator3rd() != null) {
            playerAnimator3rd = display.getPlayerAnimator3rd();
            is3rdFixedHand = display.is3rdFixedHand();
        }
        showCrosshair = display.isShowCrosshair();
        controllableData = display.getControllableData();
        ammoCountStyle = display.getAmmoCountStyle();
        damageStyle = display.getDamageStyle();
        laserConfig = display.getLaserConfig();
        enableTransparency = display.enablesTransparency();
    }

    private void checkIronZoom(GunDisplay display) {
        ironZoom = display.getIronZoom();
        if (ironZoom < 1) {
            ironZoom = 1;
        }
    }

    private static float validFov(float fov) {
        // A projection outside (0,180) is not meaningful.
        return Float.isFinite(fov) && fov > 0 && fov < 180 ? fov : 70;
    }

    private void checkTextShow(GunDisplay display) {
        Map<String, TextShow> textShowMap = Maps.newHashMap();
        display.getTextShows().forEach((key, value) -> {
            if (StringUtils.isNoneBlank(key)) {
                int color = ColorHex.colorTextToRbgInt(value.getColorText());
                value.setColorInt(color);
                textShowMap.put(key, value);
            }
        });
        gunModel.setTextShowList(textShowMap);
    }

    private void checkTextureAndModel(GunDisplay display) {
        var baked = Objects.requireNonNull(GunRigBaker.bake(display, false, false));
        gunModel = baked.getLeft();
        modelTexture = baked.getRight();
    }

    private void checkAnimation(GunDisplay display) {
        Identifier location = display.getAnimationLocation();
        AnimationController controller;
        if (location == null) {
            controller = new AnimationController(Lists.newArrayList(), gunModel);
        } else {
            AnimationStructure gltfAnimations = ClientAssetsManager.INSTANCE.getGltfAnimation(location);
            BedrockAnimationFile bedrockAnimationFile = ClientAssetsManager.INSTANCE.getBedrockAnimations(location);
            if (bedrockAnimationFile != null) {
                // 用 bedrock 动画资源创建动画控制器
                controller = Animations.createControllerFromBedrock(bedrockAnimationFile, gunModel);
            } else if (gltfAnimations != null) {
                // 用 gltf 动画资源创建动画控制器
                controller = Animations.createControllerFromGltf(gltfAnimations, gunModel);
            } else {
                // FALLBACK: 动画文件缺失时创建空控制器，而不是崩溃
                GunMod.LOGGER.warn("Animation not found for {}: {}, using empty controller", displayId, location);
                controller = new AnimationController(Lists.newArrayList(), gunModel);
            }
            // 将默认动画填入动画控制器
            Identifier defaultAnimation = display.getDefaultAnimation();
            if (defaultAnimation != null) {
                BedrockAnimationFile animationFile = ClientAssetsManager.INSTANCE.getBedrockAnimations(defaultAnimation);
                if (animationFile != null) {
                    List<ObjectAnimation> animations = Animations.createAnimationFromBedrock(animationFile);
                    for (ObjectAnimation animation : animations) {
                        controller.providePrototypeIfAbsent(animation.name, () -> new ObjectAnimation(animation));
                    }
                } else {
                    GunMod.LOGGER.warn("Default animation not found for {}: {}", displayId, defaultAnimation);
                }
            } else {
                DefaultAnimationType defaultAnimationType = display.getDefaultAnimationType();
                if (defaultAnimationType != null) {
                    switch (defaultAnimationType) {
                        case RIFLE -> {
                            for (ObjectAnimation animation : InternalAssetLoader.getDefaultRifleAnimations()) {
                                controller.providePrototypeIfAbsent(animation.name, () -> new ObjectAnimation(animation));
                            }
                        }
                        case PISTOL -> {
                            for (ObjectAnimation animation : InternalAssetLoader.getDefaultPistolAnimations()) {
                                controller.providePrototypeIfAbsent(animation.name, () -> new ObjectAnimation(animation));
                            }
                        }
                    }
                }
            }
        }
        // 初始化动画状态机，将动画控制器封装进去。
        Identifier stateMachineLocation = display.getStateMachineLocation();
        if (stateMachineLocation == null) {
            // 如果没指定状态机，则使用默认状态机
            stateMachineLocation = Identifier.fromNamespaceAndPath("tacz", "default_state_machine");
        }
        LuaTable script = ClientAssetsManager.INSTANCE.getScript(stateMachineLocation);
        if (script != null) {
            animationStateMachine = new LuaStateMachineFactory<GunAnimationStateContext>()
                    .setController(controller)
                    .setLuaScripts(script)
                    .build();
        } else {
            // FALLBACK: 状态机脚本缺失时记录警告，而不是崩溃
            GunMod.LOGGER.warn("State machine script not found for {}: {}", displayId, stateMachineLocation);
        }
        // 加载状态机参数
        Map<String, Object> params = display.getStateMachineParam();
        if (params != null) {
            stateMachineParam = new LuaTable();
            for (Map.Entry<String, Object> entry : params.entrySet()) {
                stateMachineParam.set(entry.getKey(), CoerceJavaToLua.coerce(entry.getValue()));
            }
        }
    }

    private void checkSounds(GunDisplay display) {
        sounds = Maps.newHashMap();
        preloadSounds = Lists.newArrayList();
        Map<String, SoundData> soundMaps = display.getSoundData();
        if (soundMaps == null || soundMaps.isEmpty()) {
            return;
        }
        // 部分音效为默认音效，不存在则需要添加默认音效
        soundMaps.putIfAbsent(SoundManager.DRY_FIRE_SOUND, SoundData.of(Identifier.fromNamespaceAndPath(GunMod.MOD_ID, SoundManager.DRY_FIRE_SOUND)));
        soundMaps.putIfAbsent(SoundManager.FIRE_SELECT, SoundData.of(Identifier.fromNamespaceAndPath(GunMod.MOD_ID, SoundManager.FIRE_SELECT)));
        soundMaps.putIfAbsent(SoundManager.HEAD_HIT_SOUND, SoundData.of(Identifier.fromNamespaceAndPath(GunMod.MOD_ID, SoundManager.HEAD_HIT_SOUND)));
        soundMaps.putIfAbsent(SoundManager.FLESH_HIT_SOUND, SoundData.of(Identifier.fromNamespaceAndPath(GunMod.MOD_ID, SoundManager.FLESH_HIT_SOUND)));
        soundMaps.putIfAbsent(SoundManager.KILL_SOUND, SoundData.of(Identifier.fromNamespaceAndPath(GunMod.MOD_ID, SoundManager.KILL_SOUND)));
        soundMaps.putIfAbsent(SoundManager.MELEE_BAYONET, SoundData.of(Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "melee_bayonet/melee_bayonet_01")));
        soundMaps.putIfAbsent(SoundManager.MELEE_STOCK, SoundData.of(Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "melee_stock/melee_stock_01")));
        soundMaps.putIfAbsent(SoundManager.MELEE_PUSH, SoundData.of(Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "melee_stock/melee_stock_02")));
        sounds.putAll(soundMaps);

        Set<Identifier> preloadSet = new LinkedHashSet<>();
        for (String name : GunSoundPreload.DEFAULT_PRELOAD_NAMES) {
            addPreloadSound(preloadSet, name);
        }
        List<String> configuredPreloadSounds = display.getPreloadSounds();
        if (configuredPreloadSounds != null) {
            for (String name : configuredPreloadSounds) {
                addPreloadSound(preloadSet, name);
            }
        }
        preloadSounds.addAll(preloadSet);
    }

    private void addPreloadSound(Set<Identifier> preloadSet, String name) {
        if (StringUtils.isBlank(name)) {
            return;
        }
        Identifier soundId = getSounds(name);
        if (soundId != null) {
            preloadSet.add(soundId);
        }
    }

    private void checkTransform(GunDisplay display) {
        GunTransform readTransform = display.getTransform();
        if (readTransform == null || readTransform.getScale() == null) {
            transform = GunTransform.getDefault();
        } else {
            transform = display.getTransform();
        }
    }

    private void checkSlotTexture(GunDisplay display) {
        // 加载 GUI 内枪械图标
        slotTexture = Objects.requireNonNullElseGet(display.getSlotTextureLocation(), MissingTextureAtlasSprite::getLocation);
    }

    private void checkHUDTexture(GunDisplay display) {
        hudTexture = Objects.requireNonNullElseGet(display.getHudTextureLocation(), MissingTextureAtlasSprite::getLocation);
        hudEmptyTexture = display.getHudEmptyTextureLocation();
    }

    private void checkShellEjection(GunDisplay display) {
        shellEjection = display.getShellEjection();
    }

    private void bakeShellOverride() {
        if (shellEjection == null || shellEjection.getModelLocation() == null || shellEjection.getModelTexture() == null) return;
        var pojo = ClientAssetsManager.INSTANCE.getBedrockModelPOJO(shellEjection.getModelLocation());
        if (pojo == null) return;
        if (BedrockVersion.isLegacyVersion(pojo) && pojo.getGeometryModelLegacy() != null)
            shellModel = new BedrockAmmoModel(pojo, BedrockVersion.LEGACY);
        else if (BedrockVersion.isNewVersion(pojo) && pojo.getGeometryModelNew() != null)
            shellModel = new BedrockAmmoModel(pojo, BedrockVersion.NEW);
    }

    public @Nullable BedrockAmmoModel getShellModel() {
        ensureModelLoaded();
        return shellModel;
    }

    public @Nullable Identifier getShellTextureLocation() {
        return shellEjection == null ? null : shellEjection.getModelTexture();
    }

    private void checkGunAmmo(GunDisplay display) {
        GunAmmo displayGunAmmo = display.getGunAmmo();
        if (displayGunAmmo == null) {
            return;
        }
        String tracerColorText = displayGunAmmo.getTracerColor();
        if (StringUtils.isNoneBlank(tracerColorText)) {
            tracerColor = ColorHex.colorTextToRbgFloatArray(tracerColorText);
        }
        AmmoParticle particle = displayGunAmmo.getParticle();
        if (particle != null) {
            try {
                String name = particle.getName();
                if (StringUtils.isNotBlank(name)) {
                    particle.setParticleOptions(ParticleArgument.readParticle(new StringReader(name), RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY)));
                    Preconditions.checkArgument(particle.getCount() > 0, "particle count must be greater than 0");
                    Preconditions.checkArgument(particle.getLifeTime() > 0, "particle life time must be greater than 0");
                    this.particle = particle;
                }
            } catch (CommandSyntaxException e) {
                e.fillInStackTrace();
            }
        }
    }

    private void checkMuzzleFlash(GunDisplay display) {
        muzzleFlash = display.getMuzzleFlash();
        if (muzzleFlash != null && muzzleFlash.getTexture() == null) {
            muzzleFlash = null;
        }
    }

    private void checkLayerGunShow(GunDisplay display) {
        offhandShow = display.getOffhandShow();
        if (offhandShow == null) {
            offhandShow = new LayerGunShow();
        }
        Map<String, LayerGunShow> show = display.getHotbarShow();
        if (show == null || show.isEmpty()) {
            return;
        }
        hotbarShow = new Int2ObjectArrayMap<>();
        for (String key : show.keySet()) {
            try {
                hotbarShow.put(Integer.parseInt(key), show.get(key));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("index number is error: " + key);
            }
        }
    }

    public @Nullable BedrockGunModel getGunModel() {
        ensureModelLoaded();
        return gunModel;
    }

    @Nullable
    public Pair<BedrockGunModel, Identifier> getLodModel() {
        return externalLod.loadNow();
    }

    /** Collector path for third-person, ground and FIXED. Never waits for a bake or borrows the hand rig. */
    public @Nullable Pair<BedrockGunModel, Identifier> getExternalModel(boolean preferLod) {
        return preferLod ? externalLod.preferOver(externalHigh) : externalHigh.preferOver(externalLod);
    }

    void retireModels() {
        if (modelLoaded && gunModel instanceof TaczPolyMeshGunModel mesh) mesh.retire();
        forEachReadyExternalModel(model -> {
            if (model instanceof TaczPolyMeshGunModel mesh) mesh.retire();
        });
    }

    /** Deliver shell effects only to rigs that already exist; an animation event must not start a LOD bake. */
    public void forEachReadyExternalModel(java.util.function.Consumer<BedrockGunModel> action) {
        var high = externalHigh.ready();
        var lod = externalLod.ready();
        if (high != null) action.accept(high.getLeft());
        if (lod != null) action.accept(lod.getLeft());
    }

    public @Nullable LuaAnimationStateMachine<GunAnimationStateContext> getAnimationStateMachine() {
        ensureAnimationLoaded();
        return animationStateMachine;
    }

    public @Nullable LuaTable getStateMachineParam() {
        ensureAnimationLoaded();
        return stateMachineParam;
    }

    @Nullable
    public Identifier getSounds(String name) {
        SoundData sound = getSoundData(name);
        return sound == null ? null : sound.getSound();
    }

    @Nullable
    public SoundData getSoundData(String name) {
        return sounds.get(name);
    }

    public List<Identifier> getPreloadSounds() {
        return preloadSounds;
    }

    public GunTransform getTransform() {
        return transform;
    }

    public Identifier getSlotTexture() {
        return slotTexture;
    }

    public Identifier getHUDTexture() {
        return hudTexture;
    }

    @Nullable
    public Identifier getHudEmptyTexture() {
        return hudEmptyTexture;
    }

    public Identifier getModelTexture() {
        ensureModelLoaded();
        return modelTexture != null ? modelTexture : MissingTextureAtlasSprite.getLocation();
    }

    public String getThirdPersonAnimation() {
        return thirdPersonAnimation;
    }

    @Nullable
    public ShellEjection getShellEjection() {
        return shellEjection;
    }

    @Nullable public List<HitSurfaceParticle> getHitSurfaceParticles() {
        return display.getGunAmmo() == null ? null : display.getGunAmmo().getHitSurfaceParticles();
    }

    @Nullable public Boolean getBulletHoleEnabled() {
        return display.getGunAmmo() == null ? null : display.getGunAmmo().getBulletHoleEnabled();
    }

    public float @Nullable [] getTracerColor() {
        return tracerColor;
    }

    @Nullable
    public AmmoParticle getParticle() {
        return particle;
    }

    @Nullable
    public MuzzleFlash getMuzzleFlash() {
        return muzzleFlash;
    }

    public LayerGunShow getOffhandShow() {
        return offhandShow;
    }

    @Nullable
    public Int2ObjectArrayMap<LayerGunShow> getHotbarShow() {
        return hotbarShow;
    }

    public float getIronZoom() {
        return ironZoom;
    }

    public float getModelFov() {
        return modelFov;
    }

    public float getZoomModelFov() {
        return zoomModelFov;
    }

    public @Nullable AdsPath getAdsPath() {
        AdsPath path = display.getAdsPath();
        if (path == null && adsPreset != null) {
            path = adsPreset.getPath();
        }
        return path != null && path.isValid() ? path : null;
    }

    public @Nullable AdsFov getAdsFov() {
        AdsFov fov = display.getAdsFov();
        return fov != null ? fov : adsPreset == null ? null : adsPreset.getFov();
    }

    public boolean isShowCrosshair() {
        return showCrosshair;
    }

    public @Nullable Identifier getPlayerAnimator3rd() {
        return playerAnimator3rd;
    }

    public boolean is3rdFixedHand() {
        return is3rdFixedHand;
    }

    public EnumMap<FireMode, ControllableData> getControllableData() {
        return controllableData;
    }

    public AmmoCountStyle getAmmoCountStyle() {
        return ammoCountStyle;
    }

    public DamageStyle getDamageStyle() {
        return damageStyle;
    }

    public @Nullable LaserConfig getLaserConfig() {
        return laserConfig;
    }

    public boolean enablesTransparency() {
        return enableTransparency;
    }
}
