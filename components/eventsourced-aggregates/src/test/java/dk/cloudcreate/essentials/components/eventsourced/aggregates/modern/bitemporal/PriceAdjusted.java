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

package dk.cloudcreate.essentials.components.eventsourced.aggregates.modern.bitemporal;

import dk.cloudcreate.essentials.types.*;

import static dk.cloudcreate.essentials.shared.FailFast.requireNonNull;

public class PriceAdjusted extends ProductPriceEvent {
    /**
     * The price of the product
     */
    public final Money price;

    public PriceAdjusted(ProductId forProduct, PriceId priceId, Money price, TimeWindow priceValidity) {
        super(forProduct, priceId, priceValidity);
        this.price = requireNonNull(price, "No price provided");
    }

    @Override
    public String toString() {
        return "PriceAdjusted{" +
                "price=" + price +
                ", forProduct=" + forProduct +
                ", priceId=" + priceId +
                ", priceValidityPeriod=" + priceValidityPeriod +
                '}';
    }
}
