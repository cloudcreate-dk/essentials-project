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

package dk.trustworks.essentials.types.spring.web.controllers;

import dk.trustworks.essentials.types.*;
import dk.trustworks.essentials.types.spring.web.model.*;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
public class WebFluxController {

    @GetMapping("/reactive-orders/by-due-date/{dueDate}")
    public Mono<DueDate> getOrdersWithParam(@PathVariable DueDate dueDate) {
        return Mono.just(dueDate);
    }

    @GetMapping("/reactive-orders")
    public Mono<DueDate> getOrders(@RequestParam("dueDate") DueDate dueDate) {
        return Mono.just(dueDate);
    }

    @GetMapping("/reactive-order/for-customer/{customerId}")
    public Mono<Order> getOrderForCustomer(@PathVariable CustomerId customerId) {
        var currencyCode = CurrencyCode.of("DKK");
        var amount       = Amount.of("123.456");
        var percentage   = Percentage.from("40.5%");
        return Mono.just(new Order(OrderId.random(),
                                   customerId,
                                   AccountId.random(),
                                   Map.of(ProductId.random(), Quantity.of(10),
                                          ProductId.random(), Quantity.of(5),
                                          ProductId.random(), Quantity.of(1)),
                                   amount,
                                   percentage,
                                   currencyCode,
                                   CountryCode.of("DK"),
                                   EmailAddress.of("john@nonexistingdomain.com"),
                                   new Money(amount.add(percentage.of(amount)), currencyCode),
                                   Created.now(),
                                   DueDate.now(),
                                   LastUpdated.now(),
                                   TimeOfDay.now(),
                                   TransactionTime.now(),
                                   TransferTime.now()));
    }

    @GetMapping("/reactive-order/{id}")
    public Mono<Order> findById(@PathVariable OrderId id) {
        var currencyCode = CurrencyCode.of("DKK");
        var amount       = Amount.of("123.456");
        var percentage   = Percentage.from("40.5%");
        return Mono.just(new Order(id,
                                   CustomerId.random(),
                                   AccountId.random(),
                                   Map.of(ProductId.random(), Quantity.of(10),
                                          ProductId.random(), Quantity.of(5),
                                          ProductId.random(), Quantity.of(1)),
                                   amount,
                                   percentage,
                                   currencyCode,
                                   CountryCode.of("DK"),
                                   EmailAddress.of("john@nonexistingdomain.com"),
                                   new Money(amount.add(percentage.of(amount)), currencyCode),
                                   Created.now(),
                                   DueDate.now(),
                                   LastUpdated.now(),
                                   TimeOfDay.now(),
                                   TransactionTime.now(),
                                   TransferTime.now()));
    }

    @PostMapping("/reactive-order/for-customer/{customerId}/update/total-price")
    public Mono<Order> updatePrice(@PathVariable CustomerId customerId,
                                   @RequestParam("price") Amount price) {
        return Mono.just(new Order(OrderId.random(),
                                   customerId,
                                   AccountId.random(),
                                   Map.of(ProductId.random(), Quantity.of(10),
                                          ProductId.random(), Quantity.of(5),
                                          ProductId.random(), Quantity.of(1)),
                                   Amount.of("123.456"),
                                   Percentage.from("40.5%"),
                                   CurrencyCode.of("DKK"),
                                   CountryCode.of("DK"),
                                   EmailAddress.of("john@nonexistingdomain.com"),
                                   Money.of(price, CurrencyCode.EUR),
                                   Created.now(),
                                   DueDate.now(),
                                   LastUpdated.now(),
                                   TimeOfDay.now(),
                                   TransactionTime.now(),
                                   TransferTime.now()));
    }

    @PutMapping(value = "/reactive-order")
    public Mono<OrderId> addOrder(@RequestBody Order order) {
        return Mono.just(order.getId());
    }
}
