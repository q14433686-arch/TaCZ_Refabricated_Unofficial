package com.tacz.guns.util;

import com.tacz.guns.resource.CommonAssetsManager;
import net.minecraft.resources.Identifier;
import org.apache.commons.lang3.tuple.Pair;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

public final class AllowAttachmentTagMatcher {
    private static final String TAG_PREFIX = "#";
    private static volatile Cache cache = new Cache();

    public record Cache(
            Map<Identifier, Set<Identifier>> allowAttachmentCache,
            Map<Identifier, Set<Identifier>> tagMatchCache
    ) {
        public Cache() {
            this(new ConcurrentHashMap<>(), new ConcurrentHashMap<>());
        }
    }

    public static boolean match(Identifier gunId, Identifier attachmentId) {
        return cache.allowAttachmentCache().computeIfAbsent(gunId, AllowAttachmentTagMatcher::resolveAllowed).contains(attachmentId);
    }

    public static boolean match0(Pair<Identifier, Identifier> record) {
        return resolveAllowed(record.getLeft()).contains(record.getRight());
    }

    private static Set<Identifier> resolveAllowed(Identifier gunId) {
        var provider = CommonAssetsManager.get();
        return expand(provider.getAllowAttachmentTags(gunId), null, provider::getAttachmentTags);
    }

    /**
     * 匹配配件是否有指定的标签。
     * 目前内部用于独头弹特殊标签的判断，
     * 也能方便到外部（附属，整合包等）制作它们的特殊标签。
     *
     * @param tag          tacz 配件标签
     * @param attachmentId 配件 id
     * @return 配件 id 是否有这个配件标签
     * @since 1.1.7
     */
    public static boolean matchTag(Identifier tag, Identifier attachmentId) {
        return cache.tagMatchCache().computeIfAbsent(tag, AllowAttachmentTagMatcher::resolveTag).contains(attachmentId);
    }

    public static boolean matchTag0(Pair<Identifier, Identifier> record) {
        return resolveTag(record.getLeft()).contains(record.getRight());
    }

    private static Set<Identifier> resolveTag(Identifier tag) {
        var provider = CommonAssetsManager.get();
        return expand(provider.getAttachmentTags(tag), tag, provider::getAttachmentTags);
    }

    /** Expand a complete root independently; caching partial child results is incorrect for cycles. */
    static Set<Identifier> expand(Set<String> content, Identifier rootTag,
                                        Function<Identifier, Set<String>> getTag) {
        if (content == null || content.isEmpty()) {
            return Set.of();
        }
        Set<Identifier> attachments = new LinkedHashSet<>();
        Set<Identifier> visited = new HashSet<>();
        if (rootTag != null) {
            visited.add(rootTag);
        }
        var pending = new ArrayDeque<Iterator<String>>();
        pending.push(content.iterator());
        while (!pending.isEmpty()) {
            Iterator<String> entries = pending.peek();
            if (!entries.hasNext()) {
                pending.pop();
                continue;
            }
            String entry = entries.next();
            if (entry.startsWith(TAG_PREFIX)) {
                Identifier tagId = Identifier.tryParse(entry.substring(TAG_PREFIX.length()));
                if (tagId == null || !visited.add(tagId)) {
                    continue;
                }
                Set<String> child = getTag.apply(tagId);
                if (child != null && !child.isEmpty()) {
                    pending.push(child.iterator());
                }
            } else {
                Identifier id = Identifier.tryParse(entry);
                if (id != null) {
                    attachments.add(id);
                }
            }
        }
        return Collections.unmodifiableSet(attachments);
    }

    public static void resetCache() {
        // In-flight computations may finish in the old cache, but cannot repopulate the new one.
        cache = new Cache();
    }
}
