/*
 * Copyright 2021-2022 the original author or authors.
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

package dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.persistence;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.eventstream.PersistedEvent;

import java.io.Serializable;
import java.util.*;
import java.util.function.*;

import static dk.cloudcreate.essentials.shared.FailFast.requireNonNull;

/**
 * Base metadata implementation for {@link PersistableEvent} and {@link PersistedEvent}
 */
public class EventMetaData implements Map<String, Object>, Serializable {
    @JsonTypeInfo(
            use = Id.CLASS,
            include = As.PROPERTY,
            property = "class")
    private final Map<String, Object> metaData;

    public EventMetaData(Map<String, Object> metaData) {
        this.metaData = requireNonNull(metaData, "You must provide a Map<String, Object> instance");
    }

    public EventMetaData() {
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
    public Object get(Object key) {
        return metaData.get(key);
    }

    @Override
    public Object put(String key, Object value) {
        return metaData.put(key, value);
    }

    @Override
    public Object remove(Object key) {
        return metaData.remove(key);
    }

    @Override
    public void putAll(Map<? extends String, ?> m) {
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
    public Collection<Object> values() {
        return metaData.values();
    }

    @Override
    public Set<Entry<String, Object>> entrySet() {
        return metaData.entrySet();
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
    public Object getOrDefault(Object key, Object defaultValue) {
        return metaData.getOrDefault(key, defaultValue);
    }

    @Override
    public void forEach(BiConsumer<? super String, ? super Object> action) {
        metaData.forEach(action);
    }

    @Override
    public void replaceAll(BiFunction<? super String, ? super Object, ?> function) {
        metaData.replaceAll(function);
    }

    @Override
    public Object putIfAbsent(String key, Object value) {
        return metaData.putIfAbsent(key, value);
    }

    @Override
    public boolean remove(Object key, Object value) {
        return metaData.remove(key, value);
    }

    @Override
    public boolean replace(String key, Object oldValue, Object newValue) {
        return metaData.replace(key, oldValue, newValue);
    }

    @Override
    public Object replace(String key, Object value) {
        return metaData.replace(key, value);
    }

    @Override
    public Object computeIfAbsent(String key, Function<? super String, ?> mappingFunction) {
        return metaData.computeIfAbsent(key, mappingFunction);
    }

    @Override
    public Object computeIfPresent(String key, BiFunction<? super String, ? super Object, ?> remappingFunction) {
        return metaData.computeIfPresent(key, remappingFunction);
    }

    @Override
    public Object compute(String key, BiFunction<? super String, ? super Object, ?> remappingFunction) {
        return metaData.compute(key, remappingFunction);
    }

    @Override
    public Object merge(String key, Object value, BiFunction<? super Object, ? super Object, ?> remappingFunction) {
        return metaData.merge(key, value, remappingFunction);
    }

    public static EventMetaData empty() {
        return new EventMetaData();
    }

    public static EventMetaData of() {
        return new EventMetaData(Map.of());
    }

    public static EventMetaData of(String k1, Object v1) {
        return new EventMetaData(Map.of(k1, v1));
    }

    public static EventMetaData of(String k1, Object v1, String k2, Object v2) {
        return new EventMetaData(Map.of(k1, v1, k2, v2));
    }

    public static EventMetaData of(String k1, Object v1, String k2, Object v2, String k3, Object v3) {
        return new EventMetaData(Map.of(k1, v1, k2, v2, k3, v3));
    }

    public static EventMetaData of(String k1, Object v1, String k2, Object v2, String k3, Object v3, String k4, Object v4) {
        return new EventMetaData(Map.of(k1, v1, k2, v2, k3, v3, k4, v4));
    }

    public static EventMetaData of(String k1, Object v1, String k2, Object v2, String k3, Object v3, String k4, Object v4, String k5, Object v5) {
        return new EventMetaData(Map.of(k1, v1, k2, v2, k3, v3, k4, v4, k5, v5));
    }

    public static EventMetaData of(String k1, Object v1, String k2, Object v2, String k3, Object v3, String k4, Object v4, String k5, Object v5, String k6, Object v6) {
        return new EventMetaData(Map.of(k1, v1, k2, v2, k3, v3, k4, v4, k5, v5, k6, v6));
    }

    public static EventMetaData of(String k1, Object v1, String k2, Object v2, String k3, Object v3, String k4, Object v4, String k5, Object v5, String k6, Object v6, String k7, Object v7) {
        return new EventMetaData(Map.of(k1, v1, k2, v2, k3, v3, k4, v4, k5, v5, k6, v6, k7, v7));
    }

    public static EventMetaData of(String k1, Object v1, String k2, Object v2, String k3, Object v3, String k4, Object v4, String k5, Object v5, String k6, Object v6, String k7, Object v7, String k8, Object v8) {
        return new EventMetaData(Map.of(k1, v1, k2, v2, k3, v3, k4, v4, k5, v5, k6, v6, k7, v7, k8, v8));
    }

    public static EventMetaData of(String k1, Object v1, String k2, Object v2, String k3, Object v3, String k4, Object v4, String k5, Object v5, String k6, Object v6, String k7, Object v7, String k8, Object v8, String k9, Object v9) {
        return new EventMetaData(Map.of(k1, v1, k2, v2, k3, v3, k4, v4, k5, v5, k6, v6, k7, v7, k8, v8, k9, v9));
    }

    public static EventMetaData of(String k1, Object v1, String k2, Object v2, String k3, Object v3, String k4, Object v4, String k5, Object v5, String k6, Object v6, String k7, Object v7, String k8, Object v8, String k9, Object v9, String k10, Object v10) {
        return new EventMetaData(Map.of(k1, v1, k2, v2, k3, v3, k4, v4, k5, v5, k6, v6, k7, v7, k8, v8, k9, v9, k10, v10));
    }

    @SafeVarargs
    public static EventMetaData ofEntries(Entry<String, ? extends Object>... entries) {
        return new EventMetaData(Map.ofEntries(entries));
    }

    public static EventMetaData copyOf(EventMetaData map) {
        return new EventMetaData(Map.copyOf(map));
    }

}
