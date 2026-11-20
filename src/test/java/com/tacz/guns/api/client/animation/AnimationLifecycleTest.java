package com.tacz.guns.api.client.animation;

import com.google.gson.GsonBuilder;
import com.tacz.guns.client.resource.pojo.animation.bedrock.BedrockAnimationFile;
import com.tacz.guns.client.resource.pojo.animation.bedrock.SoundEffectKeyframes;
import com.tacz.guns.client.resource.serialize.SoundEffectKeyframesSerializer;
import com.tacz.guns.util.time.GunTime;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AnimationLifecycleTest {
    @Test void emptySoundTrackDoesNotRejectTheClip() {
        var gson = new GsonBuilder().registerTypeAdapter(SoundEffectKeyframes.class, new SoundEffectKeyframesSerializer()).create();
        var file = gson.fromJson("{\"animations\":{\"draw\":{\"sound_effects\":{}}}}", BedrockAnimationFile.class);
        var clips = Animations.createAnimationFromBedrock(file);
        assertEquals("draw", clips.getFirst().name);
        assertNull(clips.getFirst().getSoundChannel());
    }

    @Test void neitherUpdatePathReplaysTimeFromAPreviousConnection() {
        var animation = new ObjectAnimation("long clip") {
            @Override public float getMaxEndTimeS() { return 10f; }
        };
        var runner = new ObjectAnimationRunner(animation);
        runner.setProgressNs(1000);
        runner.run();
        GunTime.resetEpoch();
        runner.updateSoundOnly();
        assertEquals(1000L, runner.getProgressNs());
        GunTime.resetEpoch();
        runner.update(false);
        assertEquals(1000L, runner.getProgressNs());
    }
}
