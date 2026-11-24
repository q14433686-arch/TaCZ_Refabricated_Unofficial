package com.tacz.guns.api.client.animation;

import java.util.*;

/** Chronological half-open (from,to] sampling, bounded even after a long suspended frame. */
public final class ObjectAnimationParticleChannel {
    public record Keyframe(double time, List<AnimationParticleEvent> events) {
        public Keyframe { if(!Double.isFinite(time)||time<0)throw new IllegalArgumentException("Invalid particle time");events=List.copyOf(events); }
    }
    private final List<Keyframe> frames;
    public ObjectAnimationParticleChannel(Collection<Keyframe> frames){
        this.frames=frames.stream().sorted(Comparator.comparingDouble(Keyframe::time)).toList();
    }
    public double getEndTimeS(){return frames.isEmpty()?0:frames.getLast().time();}
    public List<Keyframe> frames(){return frames;}
    public void collect(double from, double elapsed, double duration, boolean loop, boolean initial, List<AnimationParticleEvent> sink){
        if(!Double.isFinite(from)||!Double.isFinite(elapsed)||elapsed<0||sink.size()>=256)return;
        double end=from+elapsed;
        double start=initial&&from==0?Math.nextDown(0D):from;
        if(loop&&duration>0){
            long first=(long)Math.floor(Math.max(0,from)/duration);
            long last=(long)Math.floor(end/duration);
            // Limit work as well as output. Old missed loops are not replayed without bound.
            last=Math.min(last,first+255);
            for(long cycle=first;cycle<=last && sink.size()<256;cycle++)collectRange(start-cycle*duration,end-cycle*duration,sink);
        }else collectRange(start,end,sink);
    }
    private void collectRange(double from,double to,List<AnimationParticleEvent> sink){
        for(var frame:frames){if(frame.time()>to)break;if(frame.time()>from)
            for(var event:frame.events()){if(sink.size()==256)return;sink.add(event);}}
    }
}
