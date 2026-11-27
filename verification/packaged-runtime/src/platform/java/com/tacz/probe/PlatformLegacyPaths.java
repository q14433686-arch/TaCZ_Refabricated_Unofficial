package com.tacz.probe;

import cn.sh1rocu.tacz.util.forge.DelegatingPackResources;
import cn.sh1rocu.tacz.util.forge.LegacyGunPackPaths;
import cn.sh1rocu.tacz.util.forge.PathPackResources;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.IoSupplier;
import java.io.InputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Exercise directory/ZIP roots and per-pack precedence through the native pack resource APIs. */
final class PlatformLegacyPaths {
    static void verify(ClientGameTestContext ctx) {
        ctx.runOnClient(mc->{
            Path temp=null;
            try {
                temp=Files.createTempDirectory("tacz-legacy-paths-");
                for(boolean zip:new boolean[]{false,true}) {
                    Path low=temp.resolve(zip?"low-zip":"low-dir"),high=temp.resolve(zip?"high-zip":"high-dir");
                    Files.createDirectories(low);Files.createDirectories(high);
                    for(String root:roots()) {
                        String old=LegacyGunPackPaths.legacy(root);
                        write(low.resolve("data/path_probe/"+root+"/nested/value.json"),"low-current");
                        write(high.resolve("data/path_probe/"+old+"/nested/value.json"),"high-legacy");
                    }
                    if(zip) {
                        Path archive=temp.resolve("high.zip");
                        try(var fs=FileSystems.newFileSystem(java.net.URI.create("jar:"+archive.toUri()),Map.of("create","true"));var paths=Files.walk(high)) {
                            for(var source:paths.filter(Files::isRegularFile).toList()) {
                                Path target=fs.getPath("/").resolve(high.relativize(source).toString());
                                Files.createDirectories(target.getParent());Files.copy(source,target);
                            }
                        }
                        try(var fs=FileSystems.newFileSystem(archive,Map.of())) {check(fs.getPath("/"),low);}
                    } else check(high,low);
                }
            } catch(IOException e){throw new java.io.UncheckedIOException(e);}
            finally {
                if(temp!=null)try(var paths=Files.walk(temp)){for(var path:paths.sorted(Comparator.reverseOrder()).toList())Files.delete(path);}
                catch(IOException e){throw new java.io.UncheckedIOException(e);}
            }
        });
        System.out.println("PORT_PLATFORM_LEGACY_PATHS: seven non-recipe vanilla data aliases, directory/ZIP get+list, per-pack precedence and same-pack current-path priority passed; assets and recipe paths untouched");
    }
    private static void check(Path highRoot,Path lowRoot)throws IOException {
        try(var high=new PathPackResources("high",false,highRoot);var low=new PathPackResources("low",false,lowRoot);
            var aggregate=new DelegatingPackResources("paths",false,null,List.of(high,low))) {
            for(String root:roots()) {
                var id=Identifier.parse("path_probe:"+root+"/nested/value.json");
                if(!read(high.getResource(PackType.SERVER_DATA,id)).equals("high-legacy"))throw new AssertionError("Legacy direct get: "+root);
                if(!read(aggregate.getResource(PackType.SERVER_DATA,id)).equals("high-legacy"))throw new AssertionError("Higher legacy pack lost to lower current pack: "+root);
                listed(aggregate,root,id,"high-legacy");listed(high,root,id,"high-legacy");
                // A modern file within the same pack takes priority over the alias.
                write(highRoot.resolve("data/path_probe/"+root+"/nested/value.json"),"high-current");
                if(!read(aggregate.getResource(PackType.SERVER_DATA,id)).equals("high-current"))throw new AssertionError("Same-pack current get priority: "+root);
                listed(aggregate,root,id,"high-current");listed(high,root,id,"high-current");
                if(high.getResource(PackType.CLIENT_RESOURCES,id)!=null)throw new AssertionError("Server alias leaked to assets");
            }
        }
    }
    private static void listed(PackResources pack,String root,Identifier expected,String contents)throws IOException {
        var listed=new LinkedHashMap<Identifier,IoSupplier<InputStream>>();
        pack.listResources(PackType.SERVER_DATA,"path_probe",root,(id,input)->{if(listed.put(id,input)!=null)throw new AssertionError("Duplicate alias: "+id);});
        if(listed.size()!=1 || !read(listed.get(expected)).equals(contents))throw new AssertionError("Alias get/list disagree: "+root+" "+listed.keySet());
    }
    private static List<String> roots(){return List.of("loot_table","advancement","tags/block","tags/item","tags/entity_type","tags/fluid","tags/game_event");}
    private static void write(Path file,String value)throws IOException{Files.createDirectories(file.getParent());Files.writeString(file,value);}
    private static String read(IoSupplier<InputStream> supplier)throws IOException{if(supplier==null)throw new AssertionError("Missing alias supplier");try(var input=supplier.get()){return new String(input.readAllBytes(),StandardCharsets.UTF_8);}}
    private PlatformLegacyPaths(){}
}
