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

package dk.cloudcreate.essentials.components.eventsourced.aggregates.modern.bitemporal;

import dk.cloudcreate.essentials.components.eventsourced.aggregates.EventHandler;
import dk.cloudcreate.essentials.components.eventsourced.aggregates.stateful.modern.AggregateRoot;
import dk.cloudcreate.essentials.shared.functional.tuple.*;
import dk.cloudcreate.essentials.types.*;

import java.time.*;
import java.util.*;

import static dk.cloudcreate.essentials.shared.FailFast.requireNonNull;

/**
 * Simple bi-temporal product price aggregate example.<br>
 * A {@link ProductPrice} is always linked to a {@link ProductId} as its aggregate id.<br>
 * The {@link ProductPrice} aggregate tracks prices over time. Each price entry is associated with {@link PriceId}.<br>
 * The logic works as described below:
 * <ul>
 * <li>Calls to {@link ProductPrice#ProductPrice(ProductId, Money, Instant)} and {@link #adjustPrice(Money, Instant)} automatically creates
 * a new Price entry and assigns it a unique {@link PriceId}</li>
 * <li>{@link ProductPrice#ProductPrice(ProductId, Money, Instant)} applies a {@link InitialPriceSet} event</li>
 * <li>{@link #adjustPrice(Money, Instant)} applies a {@link PriceAdjusted} event
 * <ul><li>
 * Additionally {@link #adjustPrice(Money, Instant)} will adjustment the {@link ProductPriceEvent#priceValidityPeriod} of existing price entries
 * that overlap with a new price. These adjustments are applied as {@link PriceValidityAdjusted} events.
 * </li></ul>
 * </li>
 * </ul>
 */
public class ProductPrice extends AggregateRoot<ProductId, ProductPriceEvent, ProductPrice> {
    public static Instant PRESENT = null;

    /**
     * Simple bi-temporal query example where all prices are kept in a map according to the TimeWindow they're valid within<br>
     * This map supports both {@link #getPriceAt(Instant)} and updates/adjustments to prices in order to figure out which prices (as indicated by their {@link PriceId})
     * needs the {@link TimeWindow} based validity period adjusted
     */
    private Map<TimeWindow, Price> priceOverTime;

    /**
     * Used for rehydration
     */
    public ProductPrice(ProductId forProduct) {
        super(forProduct);
    }

    /**
     * Command method for creating a new {@link ProductPrice} aggregate with an initial initialPrice
     *
     * @param forProduct            the id of the product we're defining prices for over time
     * @param initialPrice          the initial initialPrice
     * @param initialPriceValidFrom when the <code>initialPrice</code> is valid from
     */
    public ProductPrice(ProductId forProduct,
                        Money initialPrice,
                        Instant initialPriceValidFrom) {
        this(forProduct);
        requireNonNull(initialPriceValidFrom, "initialPriceValidFrom is null");
        apply(new InitialPriceSet(forProduct,
                                  PriceId.random(),
                                  initialPrice,
                                  TimeWindow.from(initialPriceValidFrom)));
    }

    /**
     * Update the newPrice from the timestamp provided in <code>newPriceValidFrom</code>
     *
     * @param newPrice          the new newPrice from timestamp <code>newPriceValidFrom</code>
     * @param newPriceValidFrom when the <code>newPrice</code> is valid from
     */
    public void adjustPrice(Money newPrice, Instant newPriceValidFrom) {
        requireNonNull(newPrice, "No newPrice provided");
        requireNonNull(newPriceValidFrom, "No newPriceValidFrom provided");

        var existingPriceAt = getPricePairAt(newPriceValidFrom);
        if (existingPriceAt.isEmpty()) {
            // We're setting newPrice prior to the initial newPrice
            var initialPrice = findTheValidityPeriodOfTheNextPriceAfter(newPriceValidFrom).get();
            apply(new PriceAdjusted(aggregateId(),
                                    PriceId.random(),
                                    newPrice,
                                    TimeWindow.between(newPriceValidFrom,
                                                       initialPrice.priceValidity().fromInclusive)
            ));
        } else {
            var previousPrice = findTheValidityPeriodOfThePreviousPriceBefore(newPriceValidFrom);
            var nextPrice     = findTheValidityPeriodOfTheNextPriceAfter(newPriceValidFrom);

            // TODO: The example can easily be expanded with cancellation of price entries in cases where a new entry is identical to an existing entry (or has the same validFrom date as an existing entry)

            // Adjust the validity of the previous price
            previousPrice.ifPresent(previousPriceValidity -> apply(new PriceValidityAdjusted(aggregateId(),
                                                                                             previousPriceValidity.priceId(),
                                                                                             previousPriceValidity.priceValidity().close(newPriceValidFrom))));
            if (nextPrice.isPresent() && !nextPrice.equals(previousPrice)) {
                // Insert the price between the previous and the next price
                apply(new PriceAdjusted(aggregateId(),
                                        PriceId.random(),
                                        newPrice,
                                        TimeWindow.between(newPriceValidFrom,
                                                           nextPrice.get().priceValidity().fromInclusive)
                ));
            } else {
                // This is the latest price
                apply(new PriceAdjusted(aggregateId(),
                                        PriceId.random(),
                                        newPrice,
                                        TimeWindow.from(newPriceValidFrom)
                ));
            }

        }

    }


    @EventHandler
    private void on(InitialPriceSet e) {
        // To support using Objenesis we initialize fields here
        priceOverTime = new HashMap<>();

        priceOverTime.put(e.priceValidityPeriod, Price.of(e.priceId, e.price));
    }

    @EventHandler
    private void on(PriceAdjusted e) {
        // Remove an old entry with the same priceId (in case the priceId has had it validity period adjusted)
        var existingPriceIdEntry = priceOverTime.entrySet()
                                                .stream()
                                                .filter(entry -> entry.getValue().priceId().equals(e.priceId))
                                                .findFirst();
        existingPriceIdEntry.ifPresent(timeWindowPriceEntry -> priceOverTime.remove(timeWindowPriceEntry.getKey()));
        // Add new priceId entry
        priceOverTime.put(e.priceValidityPeriod, Price.of(e.priceId, e.price));
    }

    @EventHandler
    private void on(PriceValidityAdjusted e) {
        var existingPriceIdEntry = priceOverTime.entrySet()
                                                .stream()
                                                .filter(entry -> entry.getValue().priceId().equals(e.priceId))
                                                .findFirst();
        existingPriceIdEntry.ifPresent(timeWindowPriceEntry -> {
            // Remove old entry and add it again using a new validity
            var price = priceOverTime.remove(timeWindowPriceEntry.getKey());
            priceOverTime.put(e.priceValidityPeriod, price);
        });

    }

    /**
     * For testing purpose we make the query method public to this aggregate to learn what the price was at a given timestamp -
     * this will normally be placed in a designated view model, but to keep the example simple we add it to the aggregate
     *
     * @param timestamp the timestamp for which we want the price for the given product. Use value <code>null</code> (use {@link #PRESENT})
     *                  means get the current price
     * @return the product price at <code>timestamp</code> wrapped in an {@link Optional} or {@link Optional#empty()} if the product
     * didn't have a price specified at the given time
     */
    public Optional<Money> getPriceAt(Instant timestamp) {
        var matchingTimeWindow = getPricePairAt(timestamp);

        return matchingTimeWindow.map(timeWindow -> priceOverTime.get(timeWindow)._2);
    }

    /**
     * Return the  {@link Price} that is valid at the given timestamp
     *
     * @param timestamp the timestamp for which we want the price for the given product. Use value <code>null</code> (use {@link #PRESENT})
     *                  means get the current price
     * @return the {@link Price} at <code>timestamp</code> wrapped in an {@link Optional} or {@link Optional#empty()} if the product
     * didn't have a price specified at the given time
     */
    private Optional<TimeWindow> getPricePairAt(Instant timestamp) {
        return priceOverTime.keySet()
                            .stream()
                            .filter(timeWindow -> timeWindow.covers(timestamp))
                            .findFirst();
    }

    private Optional<PriceValidity> findTheValidityPeriodOfTheNextPriceAfter(Instant fromIncluding) {
        return priceOverTime.entrySet()
                            .stream()
                            .map(entry -> Pair.of(Duration.between(fromIncluding, entry.getKey().fromInclusive),
                                                  PriceValidity.of(entry.getValue()._1,
                                                                   entry.getKey())))
                            .sorted((o1, o2) -> o2._1.compareTo(o1._1))
                            .map(pair -> pair._2)
                            .findFirst();
    }

    private Optional<PriceValidity> findTheValidityPeriodOfThePreviousPriceBefore(Instant fromIncluding) {
        return priceOverTime.entrySet()
                            .stream()
                            .map(entry -> Pair.of(Duration.between(fromIncluding, entry.getKey().fromInclusive),
                                                  PriceValidity.of(entry.getValue()._1,
                                                                   entry.getKey())))

                            .sorted(Comparator.comparing(o -> o._1))
                            .map(pair -> pair._2)
                            .findFirst();
    }

    /**
     * {@link PriceId}/{@link Money} pair for internal storage of prices and their validity
     */
    private static class Price extends Pair<PriceId, Money> {

        /**
         * Construct a new {@link Tuple} with 2 elements
         *
         * @param priceId the first element
         * @param price   the second element
         */
        public Price(PriceId priceId, Money price) {
            super(priceId, price);
        }

        public static Price of(PriceId priceId, Money price) {
            return new Price(priceId, price);
        }

        public PriceId priceId() {
            return _1;
        }

        public Money price() {
            return _2;
        }
    }

    /**
     * {@link PriceId}/{@link TimeWindow} pair for capturing when a given {@link PriceId} is valid
     */
    private static class PriceValidity extends Pair<PriceId, TimeWindow> {

        /**
         * Construct a new {@link Tuple} with 2 elements
         *
         * @param priceId       the first element
         * @param priceValidity the second element
         */
        public PriceValidity(PriceId priceId, TimeWindow priceValidity) {
            super(priceId, priceValidity);
        }

        public static PriceValidity of(PriceId priceId, TimeWindow priceValidity) {
            return new PriceValidity(priceId, priceValidity);
        }

        public PriceId priceId() {
            return _1;
        }

        public TimeWindow priceValidity() {
            return _2;
        }
    }
}
