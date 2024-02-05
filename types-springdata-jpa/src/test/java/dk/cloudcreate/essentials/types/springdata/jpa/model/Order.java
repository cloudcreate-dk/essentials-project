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

package dk.cloudcreate.essentials.types.springdata.jpa.model;

import dk.cloudcreate.essentials.types.*;
import jakarta.persistence.*;

import java.util.Objects;

@Entity
@Table(name = "orders")
public class Order {
    @EmbeddedId
    @Column(name = "order_id") // Aligned with the generated column name from the embeddable id
    public  OrderId                  id;
    public  CustomerId               customerId;
    public  AccountId                accountId;
    /**
     * Does not work at the moment
//     */
//    @ElementCollection(fetch = FetchType.EAGER)
//    @MapKeyColumn(name = "product_id")
//    @Column(name = "quantity")
//    @CollectionTable(name = "order_lines")
//    public  Map<ProductId, Quantity> orderLines;
    private Amount                   amount;
    private Percentage               percentage;

    private CurrencyCode currency;
    private CountryCode  country;
    private EmailAddress email;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "amount", column = @Column(name = "total_price_amount")),
            @AttributeOverride(name = "currency", column = @Column(name = "total_price_currency"))
    })
    private Money totalPrice;

    // Dates
    private Created         created;
    private DueDate         dueDate;
    private LastUpdated     lastUpdated;
    private TimeOfDay       timeOfDay;
    private TransactionTime transactionTime;
    private TransferTime    transferTime;

    public Order() {
    }

    public Order(OrderId id, CustomerId customerId, AccountId accountId,
//                 Map<ProductId, Quantity> orderLines,
                 Amount amount, Percentage percentage,
                 CurrencyCode currency, CountryCode country, EmailAddress email, Money totalPrice, Created created, DueDate dueDate, LastUpdated lastUpdated, TimeOfDay timeOfDay, TransactionTime transactionTime, TransferTime transferTime) {
        this.id = id;
        this.customerId = customerId;
        this.accountId = accountId;
//        this.orderLines = orderLines;
        this.amount = amount;
        this.percentage = percentage;
        this.currency = currency;
        this.country = country;
        this.email = email;
        this.totalPrice = totalPrice;
        this.created = created;
        this.dueDate = dueDate;
        this.lastUpdated = lastUpdated;
        this.timeOfDay = timeOfDay;
        this.transactionTime = transactionTime;
        this.transferTime = transferTime;
    }

    public OrderId getId() {
        return id;
    }

    public CustomerId getCustomerId() {
        return customerId;
    }

    public void setCustomerId(CustomerId customerId) {
        this.customerId = customerId;
    }

    public AccountId getAccountId() {
        return accountId;
    }

    public void setAccountId(AccountId accountId) {
        this.accountId = accountId;
    }

//    public Map<ProductId, Quantity> getOrderLines() {
//        return orderLines;
//    }
//
//    public void setOrderLines(Map<ProductId, Quantity> orderLines) {
//        this.orderLines = orderLines;
//    }

    public Amount getAmount() {
        return amount;
    }

    public void setAmount(Amount amount) {
        this.amount = amount;
    }

    public Percentage getPercentage() {
        return percentage;
    }

    public void setPercentage(Percentage percentage) {
        this.percentage = percentage;
    }

    public CurrencyCode getCurrency() {
        return currency;
    }

    public void setCurrency(CurrencyCode currency) {
        this.currency = currency;
    }

    public CountryCode getCountry() {
        return country;
    }

    public void setCountry(CountryCode country) {
        this.country = country;
    }

    public EmailAddress getEmail() {
        return email;
    }

    public void setEmail(EmailAddress email) {
        this.email = email;
    }

    public Money getTotalPrice() {
        return totalPrice;
    }

    public void setTotalPrice(Money totalPrice) {
        this.totalPrice = totalPrice;
    }

    public Created getCreated() {
        return created;
    }

    public void setCreated(Created created) {
        this.created = created;
    }

    public DueDate getDueDate() {
        return dueDate;
    }

    public void setDueDate(DueDate dueDate) {
        this.dueDate = dueDate;
    }

    public LastUpdated getLastUpdated() {
        return lastUpdated;
    }

    public void setLastUpdated(LastUpdated lastUpdated) {
        this.lastUpdated = lastUpdated;
    }

    public TimeOfDay getTimeOfDay() {
        return timeOfDay;
    }

    public void setTimeOfDay(TimeOfDay timeOfDay) {
        this.timeOfDay = timeOfDay;
    }

    public TransactionTime getTransactionTime() {
        return transactionTime;
    }

    public void setTransactionTime(TransactionTime transactionTime) {
        this.transactionTime = transactionTime;
    }

    public TransferTime getTransferTime() {
        return transferTime;
    }

    public void setTransferTime(TransferTime transferTime) {
        this.transferTime = transferTime;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Order order = (Order) o;
        return Objects.equals(id, order.id) && Objects.equals(customerId, order.customerId) && Objects.equals(accountId, order.accountId) && /*Objects.equals(orderLines, order.orderLines) &&*/ Objects.equals(amount, order.amount) && Objects.equals(percentage, order.percentage) && Objects.equals(currency, order.currency) && Objects.equals(country, order.country) && Objects.equals(email, order.email) && Objects.equals(totalPrice, order.totalPrice) && Objects.equals(created, order.created) && Objects.equals(dueDate, order.dueDate) && Objects.equals(lastUpdated, order.lastUpdated) && Objects.equals(timeOfDay, order.timeOfDay) && Objects.equals(transactionTime, order.transactionTime) && Objects.equals(transferTime, order.transferTime);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, customerId, accountId, /*orderLines,*/ amount, percentage, currency, country, email, totalPrice, created, dueDate, lastUpdated, timeOfDay, transactionTime, transferTime);
    }

    @Override
    public String toString() {
        return "Order{" +
                "id=" + id +
                ", customerId=" + customerId +
                ", accountId=" + accountId +
//                ", orderLines=" + orderLines +
                ", amount=" + amount +
                ", percentage=" + percentage +
                ", currency=" + currency +
                ", country=" + country +
                ", email=" + email +
                ", totalPrice=" + totalPrice +
                ", created=" + created +
                ", dueDate=" + dueDate +
                ", lastUpdated=" + lastUpdated +
                ", timeOfDay=" + timeOfDay +
                ", transactionTime=" + transactionTime +
                ", transferTime=" + transferTime +
                '}';
    }
}
