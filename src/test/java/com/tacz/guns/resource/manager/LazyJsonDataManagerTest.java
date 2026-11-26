package com.tacz.guns.resource.manager;

import com.google.gson.Gson;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import static org.junit.jupiter.api.Assertions.*;

class LazyJsonDataManagerTest {
    private static final Identifier ID=Identifier.parse("probe:model");
    private static LazyJsonDataManager<String> manager() {
        return new LazyJsonDataManager<>(String.class,new Gson(),FileToIdConverter.json("models"),"LazyProbe");
    }
    @Test void oldWorkerCannotPublishOverReloadAndIdSnapshotsAreDetached() throws Exception {
        var manager=manager();var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        manager.apply(new LazyJsonDataManager.PreparedResult<>(Map.of(ID,LazyJsonDataManager.PreparedEntry.lazy(()->{
            entered.countDown();
            try {if(!release.await(3,TimeUnit.SECONDS))throw new AssertionError("Test did not release lazy loader");}
            catch(InterruptedException e){Thread.currentThread().interrupt();throw new AssertionError(e);}
            return LazyJsonDataManager.LoadResult.success("old");
        })),Set.of(ID)),null,null);
        var ids=manager.getAllIds();
        var old=new CompletableFuture<String>();
        var worker=new Thread(()->{try{old.complete(manager.getData(ID));}catch(Throwable e){old.completeExceptionally(e);}},"old-model-loader");
        var reloaded=new CompletableFuture<Void>();
        var reload=new Thread(()->{try{
            manager.apply(new LazyJsonDataManager.PreparedResult<>(Map.of(ID,LazyJsonDataManager.PreparedEntry.lazy(()->LazyJsonDataManager.LoadResult.success("new"))),Set.of(ID)),null,null);
            reloaded.complete(null);
        }catch(Throwable e){reloaded.completeExceptionally(e);}},"resource-reload");
        worker.start();
        try {
            assertTrue(entered.await(2,TimeUnit.SECONDS));reload.start();
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(2);
            while(reload.getState()!=Thread.State.BLOCKED && !reloaded.isDone() && System.nanoTime()<deadline)LockSupport.parkNanos(100_000);
            assertEquals(Thread.State.BLOCKED,reload.getState(),"Reload apply must serialize with an in-flight lazy parse");
        } finally {release.countDown();worker.join(3000);if(reload.getState()!=Thread.State.NEW)reload.join(3000);}
        assertEquals("old",old.get(1,TimeUnit.SECONDS));reloaded.get(1,TimeUnit.SECONDS);
        assertEquals("new",manager.getData(ID));
        manager.apply(new LazyJsonDataManager.PreparedResult<>(Map.of(),Set.of()),null,null);
        assertEquals(Set.of(ID),ids,"Reload mutated an earlier getAllIds result");
        assertTrue(manager.getAllIds().isEmpty());assertNull(manager.getData(ID));assertNull(manager.getData(null));
    }
}
