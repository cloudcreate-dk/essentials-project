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

package dk.cloudcreate.essentials.types.springdata.jpa.model;


import jakarta.persistence.*;

import java.util.Objects;

@Entity
@Table(name = "products")
public class Product {
    @EmbeddedId
    @Column(name = "product_id")  // Aligned with the generated column name from the embeddable id
    private ProductId id;
    private String    name;
    @Embedded
    private Price     price;
    private OrderId   orderId;

    public Product() {
    }

    public Product(ProductId id, String name, Price price, OrderId orderId) {
        this.id = id;
        this.name = name;
        this.price = price;
        this.orderId = orderId;
    }

    public OrderId getOrderId() {
        return orderId;
    }

    public void setOrderId(OrderId orderId) {
        this.orderId = orderId;
    }

    public ProductId getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Price getPrice() {
        return price;
    }

    public void setPrice(Price price) {
        this.price = price;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Product product)) return false;
        return Objects.equals(id, product.id) && Objects.equals(name, product.name) && Objects.equals(price, product.price) && Objects.equals(orderId, product.orderId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, price, orderId);
    }
}
