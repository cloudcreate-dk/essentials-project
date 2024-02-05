/*
 * Copyright 2021-2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dk.cloudcreate.essentials.components.foundation.messaging.queue;

import dk.cloudcreate.essentials.components.foundation.fencedlock.*;
import dk.cloudcreate.essentials.components.foundation.messaging.eip.store_and_forward.*;

import java.io.Serializable;
import java.util.*;
import java.util.function.*;

import static dk.cloudcreate.essentials.shared.FailFast.requireNonNull;

/**
 * Encapsulated Metadata (like headers, correlation id, tracing id's, etc.) associated with a {@link Message}
 */
public class MessageMetaData implements Map<String, String>, Serializable {
    /**
     * If the message is delivered via either an {@link Inbox}
     * or {@link Outbox} using a {@link FencedLock}, such as {@link Inboxes#durableQueueBasedInboxes(DurableQueues, FencedLockManager)})
     * or {@link Outboxes#durableQueueBasedOutboxes(DurableQueues, FencedLockManager)},
     * to coordinate message consumption, then you can find the {@link FencedLock#getCurrentToken()}
     * of the consumer under this key
     */
    public static String              FENCED_LOCK_TOKEN   = "FENCED_LOCK_TOKEN";
    private final Map<String, String> metaData;

    public MessageMetaData(Map<String, String> metaData) {
        this.metaData = requireNonNull(metaData, "You must provide a Map<String, String> instance");
    }

    public MessageMetaData() {
        this(new HashMap<>());
    }

    @Override
    public int size() {
        return metaData.size();
    }

    @Override
    public boolean isEmpty() {
        return metaData.isEmpty();
    }

    @Override
    public boolean containsKey(Object key) {
        return metaData.containsKey(key);
    }

    @Override
    public boolean containsValue(Object value) {
        return metaData.containsValue(value);
    }

    @Override
    public String get(Object key) {
        return metaData.get(key);
    }


    @Override
    public String put(String key, String value) {
        return metaData.put(key, value);
    }

    @Override
    public String remove(Object key) {
        return metaData.remove(key);
    }

    @Override
    public void putAll(Map<? extends String, ? extends String> m) {
        metaData.putAll(m);
    }

    @Override
    public void clear() {
        metaData.clear();
    }


    @Override
    public Set<String> keySet() {
        return metaData.keySet();
    }


    @Override
    public Collection<String> values() {
        return metaData.values();
    }


    @Override
    public Set<Entry<String, String>> entrySet() {
        return metaData.entrySet();
    }

    @Override
    public String getOrDefault(Object key, String defaultValue) {
        return metaData.getOrDefault(key, defaultValue);
    }

    @Override
    public void forEach(BiConsumer<? super String, ? super String> action) {
        metaData.forEach(action);
    }

    @Override
    public void replaceAll(BiFunction<? super String, ? super String, ? extends String> function) {
        metaData.replaceAll(function);
    }


    @Override
    public String putIfAbsent(String key, String value) {
        return metaData.putIfAbsent(key, value);
    }

    @Override
    public boolean remove(Object key, Object value) {
        return metaData.remove(key, value);
    }

    @Override
    public boolean replace(String key, String oldValue, String newValue) {
        return metaData.replace(key, oldValue, newValue);
    }


    @Override
    public String replace(String key, String value) {
        return metaData.replace(key, value);
    }

    @Override
    public String computeIfAbsent(String key, Function<? super String, ? extends String> mappingFunction) {
        return metaData.computeIfAbsent(key, mappingFunction);
    }

    @Override
    public String computeIfPresent(String key, BiFunction<? super String, ? super String, ? extends String> remappingFunction) {
        return metaData.computeIfPresent(key, remappingFunction);
    }

    @Override
    public String compute(String key, BiFunction<? super String, ? super String, ? extends String> remappingFunction) {
        return metaData.compute(key, remappingFunction);
    }

    @Override
    public String merge(String key, String value, BiFunction<? super String, ? super String, ? extends String> remappingFunction) {
        return metaData.merge(key, value, remappingFunction);
    }

    public static MessageMetaData empty() {
        return new MessageMetaData();
    }

    public static MessageMetaData of() {
        return new MessageMetaData();
    }

    public static MessageMetaData of(String k1, Object v1) {
        return new MessageMetaData(Map.of(k1, v1 == null ? null : v1.toString()));
    }

    public static MessageMetaData of(String k1, Object v1, String k2, Object v2) {
        return new MessageMetaData(Map.of(k1, v1 == null ? null : v1.toString(), k2, v2 == null ? null : v2.toString()));
    }

    public static MessageMetaData of(String k1, Object v1, String k2, Object v2, String k3, Object v3) {
        return new MessageMetaData(Map.of(k1, v1 == null ? null : v1.toString(), k2, v2 == null ? null : v2.toString(), k3, v3 == null ? null : v3.toString()));
    }

    public static MessageMetaData of(String k1, Object v1, String k2, Object v2, String k3, Object v3, String k4, Object v4) {
        return new MessageMetaData(Map.of(k1, v1 == null ? null : v1.toString(), k2, v2 == null ? null : v2.toString(), k3, v3 == null ? null : v3.toString(), k4, v4 == null ? null : v4.toString()));
    }

    public static MessageMetaData of(String k1, Object v1, String k2, Object v2, String k3, Object v3, String k4, Object v4, String k5, Object v5) {
        return new MessageMetaData(Map.of(k1, v1 == null ? null : v1.toString(), k2, v2 == null ? null : v2.toString(), k3, v3 == null ? null : v3.toString(), k4, v4 == null ? null : v4.toString(), k5, v5 == null ? null : v5.toString()));
    }

    public static MessageMetaData of(String k1, Object v1, String k2, Object v2, String k3, Object v3, String k4, Object v4, String k5, Object v5, String k6, Object v6) {
        return new MessageMetaData(Map.of(k1, v1 == null ? null : v1.toString(), k2, v2 == null ? null : v2.toString(), k3, v3 == null ? null : v3.toString(), k4, v4 == null ? null : v4.toString(), k5, v5 == null ? null : v5.toString(), k6, v6 == null ? null : v6.toString()));
    }

    public static MessageMetaData of(String k1, Object v1, String k2, Object v2, String k3, Object v3, String k4, Object v4, String k5, Object v5, String k6, Object v6, String k7, Object v7) {
        return new MessageMetaData(Map.of(k1, v1 == null ? null : v1.toString(), k2, v2 == null ? null : v2.toString(), k3, v3 == null ? null : v3.toString(), k4, v4 == null ? null : v4.toString(), k5, v5 == null ? null : v5.toString(), k6, v6 == null ? null : v6.toString(), k7, v7 == null ? null : v7.toString()));
    }

    public static MessageMetaData of(String k1, Object v1, String k2, Object v2, String k3, Object v3, String k4, Object v4, String k5, Object v5, String k6, Object v6, String k7, Object v7, String k8, Object v8) {
        return new MessageMetaData(Map.of(k1, v1 == null ? null : v1.toString(), k2, v2 == null ? null : v2.toString(), k3, v3 == null ? null : v3.toString(), k4, v4 == null ? null : v4.toString(), k5, v5 == null ? null : v5.toString(), k6, v6 == null ? null : v6.toString(), k7, v7 == null ? null : v7.toString(), k8, v8 == null ? null : v8.toString()));
    }

    public static MessageMetaData of(String k1, Object v1, String k2, Object v2, String k3, Object v3, String k4, Object v4, String k5, Object v5, String k6, Object v6, String k7, Object v7, String k8, Object v8, String k9, Object v9) {
        return new MessageMetaData(Map.of(k1, v1 == null ? null : v1.toString(), k2, v2 == null ? null : v2.toString(), k3, v3 == null ? null : v3.toString(), k4, v4 == null ? null : v4.toString(), k5, v5 == null ? null : v5.toString(), k6, v6 == null ? null : v6.toString(), k7, v7 == null ? null : v7.toString(), k8, v8 == null ? null : v8.toString(), k9, v9 == null ? null : v9.toString()));
    }

    public static MessageMetaData of(String k1, Object v1, String k2, Object v2, String k3, Object v3, String k4, Object v4, String k5, Object v5, String k6, Object v6, String k7, Object v7, String k8, Object v8, String k9, Object v9, String k10, Object v10) {
        return new MessageMetaData(Map.of(k1, v1 == null ? null : v1.toString(), k2, v2 == null ? null : v2.toString(), k3, v3 == null ? null : v3.toString(), k4, v4 == null ? null : v4.toString(), k5, v5 == null ? null : v5.toString(), k6, v6 == null ? null : v6.toString(), k7, v7 == null ? null : v7.toString(), k8, v8 == null ? null : v8.toString(), k9, v9 == null ? null : v9.toString(), k10, v10 == null ? null : v10.toString()));
    }

    @SafeVarargs
    public static MessageMetaData ofEntries(Entry<String, String>... entries) {
        return new MessageMetaData(Map.ofEntries(entries));
    }

    @Override
    public boolean equals(Object o) {
        return metaData.equals(o);
    }

    @Override
    public int hashCode() {
        return metaData.hashCode();
    }

    @Override
    public String toString() {
        return "MessageMetaData{" +
                metaData +
                '}';
    }

    public static MessageMetaData copyOf(MessageMetaData map) {
        return new MessageMetaData(Map.copyOf(map));
    }
}
