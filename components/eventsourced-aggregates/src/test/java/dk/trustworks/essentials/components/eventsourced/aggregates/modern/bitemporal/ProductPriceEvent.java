/*
 * Copyright 2021-2025 the original author or authors.
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

package dk.trustworks.essentials.components.eventsourced.aggregates.modern.bitemporal;

import dk.trustworks.essentials.types.TimeWindow;

import java.util.Objects;

import static dk.trustworks.essentials.shared.FailFast.requireNonNull;

/**
 * Shared base event class for all VersionedEvents
 */
public abstract class ProductPriceEvent {
    /**
     * The Id of the Product that price relates to
     */
    public final ProductId forProduct;

    /**
     * The unique id for a given price. Updates to {@link ProductPrice} validity for this partical price
     * will contain the same {@link #priceId}
     */
    public final PriceId priceId;


    /**
     * When is this the price/{@link #priceId} combination valid as of the technical time when this event
     * was created.<br>
     * Future {@link ProductPriceEvent}'s with the same {@link PriceId}
     * can adjust the {@link #priceValidityPeriod}
     */
    public final TimeWindow priceValidityPeriod;

    protected ProductPriceEvent(ProductId forProduct,
                                PriceId priceId,
                                TimeWindow priceValidityPeriod) {
        this.forProduct = requireNonNull(forProduct, "No product set");
        this.priceId = requireNonNull(priceId, "No priceId set");
        this.priceValidityPeriod = requireNonNull(priceValidityPeriod, "No priceValidityPeriod provided");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ProductPriceEvent)) return false;
        ProductPriceEvent that = (ProductPriceEvent) o;
        return forProduct.equals(that.forProduct) && priceId.equals(that.priceId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(forProduct, priceId);
    }
}
