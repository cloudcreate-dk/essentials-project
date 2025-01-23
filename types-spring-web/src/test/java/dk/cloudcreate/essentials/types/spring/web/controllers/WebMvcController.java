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

package dk.cloudcreate.essentials.types.spring.web.controllers;

import dk.cloudcreate.essentials.types.*;
import dk.cloudcreate.essentials.types.spring.web.model.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
public class WebMvcController {

    @GetMapping("/orders")
    public DueDate getOrdersWithParam(@RequestParam("dueDate") DueDate dueDate) {
        return dueDate;
    }

    @GetMapping("/orders/by-due-date/{dueDate}")
    public DueDate getOrders(@PathVariable DueDate dueDate) {
        return dueDate;
    }

    @GetMapping("/orders/by-created/{created}")
    public Created getOrders(@PathVariable Created created) {
        return created;
    }

    @GetMapping("/orders/by-last-updated/{lastUpdated}")
    public LastUpdated getOrders(@PathVariable LastUpdated lastUpdated) {
        return lastUpdated;
    }

    @GetMapping("/orders/by-time-of-day/{timeOfDay}")
    public TimeOfDay getOrders(@PathVariable TimeOfDay timeOfDay) {
        return timeOfDay;
    }

    @GetMapping("/orders/by-transfer-time/{transferTime}")
    public TransferTime getOrders(@PathVariable TransferTime transferTime) {
        return transferTime;
    }

    @GetMapping("/orders/by-transaction-time/{transactionTime}")
    public TransactionTime getOrders(@PathVariable TransactionTime transactionTime) {
        return transactionTime;
    }

    @GetMapping("/order/for-customer/{customerId}")
    public Order getOrderForCustomer(@PathVariable CustomerId customerId) {
        var currencyCode = CurrencyCode.of("DKK");
        var amount       = Amount.of("123.456");
        var percentage   = Percentage.from("40.5%");
        return new Order(OrderId.random(),
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
                         TransferTime.now());
    }

    @GetMapping("/order/{id}")
    public Order findById(@PathVariable OrderId id) {
        var currencyCode = CurrencyCode.of("DKK");
        var amount       = Amount.of("123.456");
        var percentage   = Percentage.from("40.5%");
        return new Order(id,
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
                         TransferTime.now());
    }

    @PostMapping("/order/for-customer/{customerId}/update/total-price")
    public ResponseEntity<Order> updatePrice(@PathVariable CustomerId customerId,
                                             @RequestParam("price") Amount price) {
        return ResponseEntity.ok(
                new Order(OrderId.random(),
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

    @PutMapping(value = "/order")
    public OrderId addOrder(@RequestBody Order order) {
        return order.getId();
    }
}
