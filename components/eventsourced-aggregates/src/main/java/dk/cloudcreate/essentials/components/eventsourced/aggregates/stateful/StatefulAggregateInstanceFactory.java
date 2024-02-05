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

package dk.cloudcreate.essentials.components.eventsourced.aggregates.stateful;

import dk.cloudcreate.essentials.components.eventsourced.aggregates.Aggregate;
import dk.cloudcreate.essentials.components.eventsourced.aggregates.stateful.classic.AggregateRoot;
import dk.cloudcreate.essentials.shared.reflection.Reflector;
import org.objenesis.*;
import org.objenesis.instantiator.ObjectInstantiator;

import java.lang.reflect.Modifier;
import java.util.concurrent.*;

import static dk.cloudcreate.essentials.shared.FailFast.requireNonNull;
import static dk.cloudcreate.essentials.shared.MessageFormatter.msg;

/**
 * Factory that helps the {@link StatefulAggregateRepository}/{@link StatefulAggregateInMemoryProjector} to create an instance of a given {@link Aggregate}.
 *
 * @see #reflectionBasedAggregateRootFactory()
 * @see ReflectionBasedAggregateInstanceFactory
 * @see #objenesisAggregateRootFactory()
 * @see ObjenesisAggregateInstanceFactory
 */
public interface StatefulAggregateInstanceFactory {
    /**
     * Create an instance of the {@link Aggregate}
     *
     * @param id            the id value
     * @param aggregateType the {@link Aggregate} implementation type
     * @param <ID>          the type of aggregate id
     * @param <AGGREGATE>   the {@link Aggregate} implementation type
     * @return new aggregate instance
     */
    <ID, AGGREGATE> AGGREGATE create(ID id, Class<AGGREGATE> aggregateType);

    /**
     * Returns a {@link StatefulAggregateInstanceFactory} that calls the default no-arguments constructor on the concrete {@link Aggregate} type to
     * create a new instance of the {@link Aggregate}
     *
     * @return a {@link StatefulAggregateInstanceFactory} that calls the default no-arguments constructor on the concrete {@link Aggregate} type
     */
    static StatefulAggregateInstanceFactory reflectionBasedAggregateRootFactory() {
        return new ReflectionBasedAggregateInstanceFactory();
    }

    /**
     * Returns a {@link StatefulAggregateInstanceFactory} that uses {@link Objenesis} to create a new instance of the {@link Aggregate}<br>
     * <b>Please note: Objenesis doesn't initialize fields nor call any constructors</b>, so you {@link Aggregate} design needs to take
     * this into consideration.<br>
     * All concrete aggregates that extends {@link AggregateRoot} have been prepared to be initialized by {@link Objenesis}
     *
     * @return a {@link StatefulAggregateInstanceFactory} that uses {@link Objenesis} to create a new instance of the {@link Aggregate}
     */
    static StatefulAggregateInstanceFactory objenesisAggregateRootFactory() {
        return new ObjenesisAggregateInstanceFactory();
    }


    /**
     * {@link StatefulAggregateInstanceFactory} that calls the default no-arguments constructor on the concrete {@link Aggregate} type to
     * create a new instance of the {@link Aggregate}
     */
    class ReflectionBasedAggregateInstanceFactory implements StatefulAggregateInstanceFactory {
        @Override
        public <ID, AGGREGATE> AGGREGATE create(ID id, Class<AGGREGATE> aggregateType) {
            requireNonNull(id, "You must provide an id value");
            requireNonNull(aggregateType, "You must provide an aggregateTypeClass");
            var reflector                = Reflector.reflectOn(aggregateType);
            var defaultNoArgsConstructor = reflector.getDefaultConstructor();
            if (defaultNoArgsConstructor.isPresent() && Modifier.isPublic(defaultNoArgsConstructor.get().getModifiers())) {
                return reflector.newInstance();
            } else if (reflector.hasMatchingConstructorBasedOnArguments(id)) {
                return reflector.newInstance(id);
            } else {
                throw new IllegalArgumentException(msg("Couldn't find default constructor nor a constructor that accepts an aggregateId of type {}", id.getClass().getName()));
            }
        }
    }

    /**
     * {@link StatefulAggregateInstanceFactory} that uses {@link Objenesis} to create a new instance of the {@link Aggregate}<br>
     * <b>Please note: Objenesis doesn't initialize fields nor call any constructors</b>, so you {@link Aggregate} design needs to take
     * this into consideration.<br>
     * All concrete aggregates that extends {@link AggregateRoot} have been prepared to be initialized by {@link Objenesis}
     */
    class ObjenesisAggregateInstanceFactory implements StatefulAggregateInstanceFactory {
        private       Objenesis                                      objenesis;
        private final ConcurrentMap<Class<?>, ObjectInstantiator<?>> instantiatorMap = new ConcurrentHashMap<>();

        @SuppressWarnings("unchecked")
        @Override
        public <ID, AGGREGATE> AGGREGATE create(ID id, Class<AGGREGATE> aggregateType) {
            requireNonNull(aggregateType, "You must provide an aggregateType");
            if (objenesis == null) {
                objenesis = new ObjenesisStd();
            }
            return (AGGREGATE) instantiatorMap.computeIfAbsent(aggregateType,
                                                               objenesis::getInstantiatorOf)
                                              .newInstance();
        }
    }
}
