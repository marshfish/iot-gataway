package com.hc.dispatch.event;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 实现LRU的hashMap,预防内存泄漏
 */
public class LRUHashMap<K, V> extends LinkedHashMap<K, V> {

    protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
        return true;
    }

    public LRUHashMap() {
        this(100, 0.75f);
    }

    public LRUHashMap(int initialCapacity, float loadFactor) {
        super(initialCapacity, loadFactor, true);
    }
}
