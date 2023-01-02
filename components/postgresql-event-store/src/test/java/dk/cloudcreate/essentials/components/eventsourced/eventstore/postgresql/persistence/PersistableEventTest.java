/*
 * Copyright 2021-2023 the original author or authors.
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

import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.test_data.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.types.EventRevision;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

class PersistableEventTest {
    @Test
    void no_revision_annotation_returns_revision_1() {
        assertThat(PersistableEvent.resolveEventRevision(ProductEvent.ProductAdded.class))
                .isEqualTo(EventRevision.FIRST);
        assertThat(PersistableEvent.resolveEventRevision(new ProductEvent.ProductAdded(ProductId.random())))
                .isEqualTo(EventRevision.FIRST);
    }

    @Test
    void revision_annotation_on_sub_class_without_annotation_on_super_class_returns_the_revision() {
        assertThat(PersistableEvent.resolveEventRevision(ProductEvent.ProductDiscontinued.class))
                .isEqualTo(EventRevision.of(2));
        assertThat(PersistableEvent.resolveEventRevision(new ProductEvent.ProductDiscontinued(ProductId.random())))
                .isEqualTo(EventRevision.of(2));
    }

    @Test
    void no_revision_annotation_on_sub_class_with_annotation_on_super_class_returns_the_superclass_revision() {
        assertThat(PersistableEvent.resolveEventRevision(OrderEvent.ProductAddedToOrder.class))
                .isEqualTo(EventRevision.of(2));
        assertThat(PersistableEvent.resolveEventRevision(new OrderEvent.ProductAddedToOrder(OrderId.random(), ProductId.random(), 10)))
                .isEqualTo(EventRevision.of(2));
    }

    @Test
    void revision_annotation_on_sub_class_with_annotation_on_super_class_returns_the_superclass_revision() {
        assertThat(PersistableEvent.resolveEventRevision(OrderEvent.OrderAdded.class))
                .isEqualTo(EventRevision.of(3));
        assertThat(PersistableEvent.resolveEventRevision(new OrderEvent.OrderAdded(OrderId.random(), CustomerId.random(), 1234)))
                .isEqualTo(EventRevision.of(3));
    }
}