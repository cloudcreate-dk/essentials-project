/*
 * Copyright 2021-2022 the original author or authors.
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

package dk.cloudcreate.essentials.jackson.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import dk.cloudcreate.essentials.types.*;

import java.util.*;

public class SerializationTestSubject {
    private CustomerId customerId;
    private OrderId    orderId;
    private ProductId  productId;
    private AccountId  accountId;
    private Amount     amount;
    private Amount     longAmount;
    private Percentage percentage;

    private CurrencyCode currency;
    private CountryCode  country;
    private EmailAddress email;

    @JsonDeserialize(keyUsing = ProductIdKeyDeserializer.class)
    public Map<ProductId, Quantity> orderLines;

    private Money totalPrice;

    // Dates
    private Created created;
    private DueDate dueDate;
    private LastUpdated lastUpdated;
    private TimeOfDay timeOfDay;
    private TransactionTime transactionTime;
    private TransferTime transferTime;


    public SerializationTestSubject(CustomerId customerId, OrderId orderId, ProductId productId, AccountId accountId, Amount amount, Amount longAmount, Percentage percentage, CurrencyCode currency, CountryCode country, EmailAddress email,
                                    Map<ProductId, Quantity> orderLines, Money totalPrice, Created created, DueDate dueDate, LastUpdated lastUpdated, TimeOfDay timeOfDay, TransactionTime transactionTime, TransferTime transferTime) {
        this.customerId = customerId;
        this.orderId = orderId;
        this.productId = productId;
        this.accountId = accountId;
        this.amount = amount;
        this.longAmount = longAmount;
        this.percentage = percentage;
        this.currency = currency;
        this.country = country;
        this.email = email;
        this.orderLines = orderLines;
        this.totalPrice = totalPrice;
        this.created = created;
        this.dueDate = dueDate;
        this.lastUpdated = lastUpdated;
        this.timeOfDay = timeOfDay;
        this.transactionTime = transactionTime;
        this.transferTime = transferTime;
    }

    public SerializationTestSubject() {
    }

    public CustomerId getCustomerId() {
        return customerId;
    }

    public void setCustomerId(CustomerId customerId) {
        this.customerId = customerId;
    }

    public OrderId getOrderId() {
        return orderId;
    }

    public void setOrderId(OrderId orderId) {
        this.orderId = orderId;
    }

    public ProductId getProductId() {
        return productId;
    }

    public void setProductId(ProductId productId) {
        this.productId = productId;
    }

    public AccountId getAccountId() {
        return accountId;
    }

    public void setAccountId(AccountId accountId) {
        this.accountId = accountId;
    }

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

    public Map<ProductId, Quantity> getOrderLines() {
        return orderLines;
    }

    public void setOrderLines(Map<ProductId, Quantity> orderLines) {
        this.orderLines = orderLines;
    }

    public Amount getLongAmount() {
        return longAmount;
    }

    public void setLongAmount(Amount longAmount) {
        this.longAmount = longAmount;
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
        SerializationTestSubject that = (SerializationTestSubject) o;
        return Objects.equals(customerId, that.customerId) && Objects.equals(orderId, that.orderId) && Objects.equals(productId, that.productId) &&
                Objects.equals(accountId, that.accountId) && Objects.equals(amount, that.amount) && Objects.equals(longAmount, that.longAmount) &&
                Objects.equals(percentage, that.percentage) && Objects.equals(currency, that.currency) && Objects.equals(country, that.country) &&
                Objects.equals(email, that.email) && Objects.equals(created, that.created) && Objects.equals(dueDate, that.dueDate) &&
                Objects.equals(lastUpdated, that.lastUpdated) && Objects.equals(timeOfDay, that.timeOfDay) && Objects.equals(transactionTime, that.transactionTime) &&
                Objects.equals(transferTime, that.transferTime) && Objects.equals(orderLines, that.orderLines) && Objects.equals(totalPrice, that.totalPrice);
    }

    @Override
    public int hashCode() {
        return Objects.hash(customerId, orderId, productId, accountId, amount, longAmount, percentage, currency, country, email, created, dueDate, lastUpdated, timeOfDay, transactionTime, transferTime, orderLines, totalPrice);
    }

    @Override
    public String toString() {
        return "SerializationTestSubject{" +
                "customerId=" + customerId +
                ", orderId=" + orderId +
                ", productId=" + productId +
                ", accountId=" + accountId +
                ", amount=" + amount +
                ", longAmount=" + longAmount +
                ", percentage=" + percentage +
                ", currency=" + currency +
                ", country=" + country +
                ", email=" + email +
                ", orderLines=" + orderLines +
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
