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