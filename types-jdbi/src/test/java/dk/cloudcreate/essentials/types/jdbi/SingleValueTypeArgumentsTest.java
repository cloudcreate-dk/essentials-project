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

package dk.cloudcreate.essentials.types.jdbi;

import dk.cloudcreate.essentials.shared.functional.tuple.Tuple;
import dk.cloudcreate.essentials.types.*;
import dk.cloudcreate.essentials.types.jdbi.model.*;
import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.Test;

import java.sql.*;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

class SingleValueTypeArgumentsTest {
    @Test
    void test_jdbi_argument_factories() {
        Jdbi jdbi = Jdbi.create("jdbc:h2:mem:test");
        jdbi.registerArgument(new AccountIdArgumentFactory());
        jdbi.registerColumnMapper(new AccountIdColumnMapper());
        jdbi.registerArgument(new AmountArgumentFactory());
        jdbi.registerColumnMapper(new AmountColumnMapper());
        jdbi.registerArgument(new CountryCodeArgumentFactory());
        jdbi.registerColumnMapper(new CountryCodeColumnMapper());
        jdbi.registerArgument(new CurrencyCodeArgumentFactory());
        jdbi.registerColumnMapper(new CurrencyCodeColumnMapper());
        jdbi.registerArgument(new EmailAddressArgumentFactory());
        jdbi.registerColumnMapper(new EmailAddressColumnMapper());
        jdbi.registerArgument(new PercentageArgumentFactory());
        jdbi.registerColumnMapper(new PercentageColumnMapper());
        jdbi.registerArgument(new OrderIdArgumentFactory());
        jdbi.registerColumnMapper(new OrderIdColumnMapper());
        jdbi.registerArgument(new CustomerIdArgumentFactory());
        jdbi.registerColumnMapper(new CustomerIdColumnMapper());
        jdbi.registerArgument(new ProductIdArgumentFactory());
        jdbi.registerColumnMapper(new ProductIdColumnMapper());
        jdbi.registerArgument(new CreatedArgumentFactory());
        jdbi.registerColumnMapper(new CreatedColumnMapper());
        jdbi.registerArgument(new DueDateArgumentFactory());
        jdbi.registerColumnMapper(new DueDateColumnMapper());
        jdbi.registerArgument(new LastUpdatedArgumentFactory());
        jdbi.registerColumnMapper(new LastUpdatedColumnMapper());
        jdbi.registerArgument(new TimeOfDayArgumentFactory());
        jdbi.registerColumnMapper(new TimeOfDayColumnMapper());
        jdbi.registerArgument(new TransactionTimeArgumentFactory());
        jdbi.registerColumnMapper(new TransactionTimeColumnMapper());
        jdbi.registerArgument(new TransferTimeArgumentFactory());
        jdbi.registerColumnMapper(new TransferTimeColumnMapper());

        jdbi.useHandle(handle -> {
            handle.execute("CREATE TABLE orders (" +
                                   "id BIGINT PRIMARY KEY, " +
                                   "customer_id VARCHAR, " +
                                   "product_id VARCHAR, " +
                                   "account_id BIGINT," +
                                   "amount NUMERIC(20, 3)," +
                                   "country VARCHAR," +
                                   "currency VARCHAR," +
                                   "email VARCHAR," +
                                   "percentage NUMERIC(20, 2)," +
                                   "created TIMESTAMP," +
                                   "due_date DATE," +
                                   "last_updated TIMESTAMP," +
                                   "time_of_day TIME," +
                                   "transaction_time TIMESTAMP," +
                                   "transfer_time TIMESTAMP" +
                                   ")");

            var orderId         = OrderId.random();
            var customerId      = CustomerId.random();
            var productId       = ProductId.random();
            var accountId       = AccountId.random();
            var amount          = Amount.of("123.456");
            var country         = CountryCode.of("DK");
            var currency        = CurrencyCode.of("DKK");
            var email           = EmailAddress.of("john@nonexistingdomain.com");
            var percentage      = Percentage.from("40.5%");
            var created         = Created.now();
            var dueDate         = DueDate.now();
            var lastUpdated     = LastUpdated.now();
            var timeOfDay       = TimeOfDay.now();
            var transactionTime = TransactionTime.now();
            var transferTime    = TransferTime.now();


            handle.inTransaction(_handle -> handle.createUpdate("INSERT INTO orders(id, customer_id, product_id, account_id, amount, country, currency, email, percentage," +
                                                                        "created, due_date, last_updated, time_of_day, transaction_time, transfer_time) " +
                                                                        "VALUES (:id, :customer_id, :product_id, :account_id, :amount, :country, :currency, :email, :percentage," +
                                                                        ":created, :due_date, :last_updated, :time_of_day, :transaction_time, :transfer_time)")
                                                  .bind("id", orderId)
                                                  .bind("customer_id", customerId)
                                                  .bind("product_id", productId)
                                                  .bind("account_id", accountId)
                                                  .bind("amount", amount)
                                                  .bind("country", country)
                                                  .bind("currency", currency)
                                                  .bind("email", email)
                                                  .bind("percentage", percentage)
                                                  .bind("created", created)
                                                  .bind("due_date", dueDate)
                                                  .bind("last_updated", lastUpdated)
                                                  .bind("time_of_day", timeOfDay)
                                                  .bind("transaction_time", transactionTime)
                                                  .bind("transfer_time", transferTime)
                                                  .execute());
            var results = handle.inTransaction(_handle -> handle.createQuery("SELECT * from orders WHERE id = :id")
                                                                .bind("id", orderId)
                                                                .mapToMap());

            assertThat(results).isNotNull();

            var result = results.one();
            assertThat((long) result.get("id")).isEqualTo(orderId.value());
            assertThat(result.get("customer_id")).isEqualTo(customerId.value());
            assertThat(result.get("product_id")).isEqualTo(productId.value());
            assertThat((long) result.get("account_id")).isEqualTo(accountId.value());
            assertThat(result.get("amount")).isEqualTo(amount.value());
            assertThat(result.get("country")).isEqualTo(country.value());
            assertThat(result.get("currency")).isEqualTo(currency.value());
            assertThat(result.get("email")).isEqualTo(email.value());
            assertThat(result.get("percentage")).isEqualTo(percentage.value());
            assertThat(((Timestamp) result.get("created")).toLocalDateTime()).isCloseTo(created.value(), within(100, ChronoUnit.MICROS));
            assertThat(((Date) result.get("due_date")).toLocalDate()).isEqualTo(dueDate.value());
            assertThat(((Timestamp) result.get("last_updated")).toInstant()).isCloseTo(lastUpdated.value(), within(100, ChronoUnit.MICROS));
            assertThat(((Time) result.get("time_of_day"))).isEqualTo(Time.valueOf(timeOfDay.value()));
            assertThat(((Timestamp) result.get("transaction_time")).toInstant()).isCloseTo(transactionTime.value().toInstant(), within(100, ChronoUnit.MICROS));
            assertThat(((Timestamp) result.get("transfer_time")).toInstant()).isCloseTo(transferTime.value().toInstant(), within(100, ChronoUnit.MICROS));


            var columns = List.of(Tuple.of("id", orderId),
                                  Tuple.of("customer_id", customerId),
                                  Tuple.of("product_id", productId),
                                  Tuple.of("account_id", accountId),
                                  Tuple.of("amount", amount),
                                  Tuple.of("country", country),
                                  Tuple.of("currency", currency),
                                  Tuple.of("email", email),
                                  Tuple.of("percentage", percentage),
                                  Tuple.of("created", created),
                                  Tuple.of("due_date", dueDate),
                                  Tuple.of("last_updated", lastUpdated),
                                  Tuple.of("time_of_day", timeOfDay),
                                  Tuple.of("transaction_time", transactionTime),
                                  Tuple.of("transfer_time", transferTime));

            columns.forEach(column -> {
                var columnValue = handle.inTransaction(_handle -> handle.createQuery("SELECT " + column._1 + " from orders WHERE id = :id")
                                                                        .bind("id", orderId)
                                                                        .mapTo(column._2.getClass()))
                                        .one();
                assertThat(columnValue.getClass().equals(column._2.getClass()))
                        .describedAs("Column: '%s' of type '%s' with original value: '%s' and returned value '%s' of type '%s'",
                                     column._1, column._2.getClass(), column._2, columnValue, columnValue.getClass())
                        .isTrue();
                Object compareWithValue = column._2;
                if (column._1.equals("time_of_day")) {
                    // Time looses precision
                    compareWithValue = TimeOfDay.of(Time.valueOf(((TimeOfDay)compareWithValue).value()).toLocalTime());
                }
                if (columnValue instanceof InstantType<?> c) {
                    assertThat(c.value())
                            .describedAs("Column: '%s' of type '%s' with original value: '%s' and returned value '%s' of type '%s'",
                                         column._1, column._2.getClass(), compareWithValue, columnValue, columnValue.getClass())
                            .isCloseTo(((InstantType<?>) compareWithValue).value(), within(100, ChronoUnit.MICROS));

                } else if (columnValue instanceof LocalDateTimeType<?> c) {
                    assertThat(c.value())
                            .describedAs("Column: '%s' of type '%s' with original value: '%s' and returned value '%s' of type '%s'",
                                         column._1, column._2.getClass(), compareWithValue, columnValue, columnValue.getClass())
                            .isCloseTo(((LocalDateTimeType<?>) compareWithValue).value(), within(100, ChronoUnit.MICROS));

                } else if (columnValue instanceof OffsetDateTimeType<?> c) {
                    assertThat(c.value())
                            .describedAs("Column: '%s' of type '%s' with original value: '%s' and returned value '%s' of type '%s'",
                                         column._1, column._2.getClass(), compareWithValue, columnValue, columnValue.getClass())
                            .isCloseTo(((OffsetDateTimeType<?>) compareWithValue).value(), within(100, ChronoUnit.MICROS));

                } else if (columnValue instanceof ZonedDateTimeType<?> c) {
                    assertThat(c.value())
                            .describedAs("Column: '%s' of type '%s' with original value: '%s' and returned value '%s' of type '%s'",
                                         column._1, column._2.getClass(), compareWithValue, columnValue, columnValue.getClass())
                            .isCloseTo(((ZonedDateTimeType<?>) compareWithValue).value(), within(100, ChronoUnit.MICROS));

                } else {
                    assertThat(columnValue.equals(compareWithValue))
                            .describedAs("Column: '%s' of type '%s' with original value: '%s' and returned value '%s' of type '%s'",
                                         column._1, column._2.getClass(), compareWithValue, columnValue, columnValue.getClass())
                            .isTrue();
                }
            });
        });

    }
}