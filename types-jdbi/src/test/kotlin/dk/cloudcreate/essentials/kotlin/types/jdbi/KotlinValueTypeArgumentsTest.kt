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

package dk.cloudcreate.essentials.kotlin.types.jdbi

import dk.cloudcreate.essentials.kotlin.types.*
import dk.cloudcreate.essentials.kotlin.types.jdbi.model.*
import dk.cloudcreate.essentials.shared.functional.tuple.Tuple
import org.assertj.core.api.Assertions
import org.jdbi.v3.core.Handle
import org.jdbi.v3.core.HandleCallback
import org.jdbi.v3.core.Jdbi
import org.jdbi.v3.core.result.ResultIterable
import org.junit.jupiter.api.Test
import java.sql.Date
import java.sql.Time
import java.sql.Timestamp
import java.time.temporal.ChronoUnit
import java.util.List
import java.util.function.Consumer

internal class SingleValueTypeArgumentsTest {
    @Test
    fun test_jdbi_argument_factories() {
        val jdbi = Jdbi.create("jdbc:h2:mem:test")
        jdbi!!.registerArgument(AccountIdArgumentFactory())
        jdbi.registerColumnMapper(AccountIdColumnMapper())
        jdbi.registerArgument(AmountArgumentFactory())
        jdbi.registerColumnMapper(AmountColumnMapper())
        jdbi.registerArgument(CountryCodeArgumentFactory())
        jdbi.registerColumnMapper(CountryCodeColumnMapper())
//        jdbi.registerArgument(CurrencyCodeArgumentFactory())
//        jdbi.registerColumnMapper(CurrencyCodeColumnMapper())
//        jdbi.registerArgument(EmailAddressArgumentFactory())
//        jdbi.registerColumnMapper(EmailAddressColumnMapper())
//        jdbi.registerArgument(PercentageArgumentFactory())
//        jdbi.registerColumnMapper(PercentageColumnMapper())
        jdbi.registerArgument(OrderIdArgumentFactory())
        jdbi.registerColumnMapper(OrderIdColumnMapper())
        jdbi.registerArgument(CustomerIdArgumentFactory())
        jdbi.registerColumnMapper(CustomerIdColumnMapper())
        jdbi.registerArgument(ProductIdArgumentFactory())
        jdbi.registerColumnMapper(ProductIdColumnMapper())
        jdbi.registerArgument(CreatedArgumentFactory())
        jdbi.registerColumnMapper(CreatedColumnMapper())
        jdbi.registerArgument(DueDateArgumentFactory())
        jdbi.registerColumnMapper(DueDateColumnMapper())
        jdbi.registerArgument(LastUpdatedArgumentFactory())
        jdbi.registerColumnMapper(LastUpdatedColumnMapper())
        jdbi.registerArgument(TimeOfDayArgumentFactory())
        jdbi.registerColumnMapper(TimeOfDayColumnMapper())
        jdbi.registerArgument(TransactionTimeArgumentFactory())
        jdbi.registerColumnMapper(TransactionTimeColumnMapper())
        jdbi.registerArgument(TransferTimeArgumentFactory())
        jdbi.registerColumnMapper(TransferTimeColumnMapper())

        jdbi.useHandle<RuntimeException?> { handle: Handle? ->
            handle!!.execute(
                "CREATE TABLE orders (" +
                        "id BIGINT PRIMARY KEY, " +
                        "customer_id VARCHAR, " +
                        "product_id VARCHAR, " +
                        "account_id BIGINT," +
                        "amount NUMERIC(20, 3)," +
                        "country VARCHAR," +
//                        "currency VARCHAR," +
//                        "email VARCHAR," +
//                        "percentage NUMERIC(20, 2)," +
                        "created TIMESTAMP," +
                        "due_date DATE," +
                        "last_updated TIMESTAMP," +
                        "time_of_day TIME," +
                        "transaction_time TIMESTAMP," +
                        "transfer_time TIMESTAMP" +
                        ")"
            )
            val orderId =
                OrderId.random()
            val customerId =
                CustomerId.random()
            val productId =
                ProductId.random()
            val accountId =
                AccountId.random()
            val amount = Amount.of("123.456")
            val country =
                CountryCode.of("DK")
//            val currency = CurrencyCode.of("DKK")
//            val email =
//                EmailAddress.of("john@nonexistingdomain.com")
//            val percentage =
//                Percentage.from("40.5%")
            val created =
                Created.now()
            val dueDate =
                DueDate.now()
            val lastUpdated =
                LastUpdated.now()
            val timeOfDay =
                TimeOfDay.now()
            val transactionTime =
                TransactionTime.now()
            val transferTime =
                TransferTime.now()


            handle.inTransaction<Int?, RuntimeException?> { _handle: Handle? ->
                handle.createUpdate(
                    "INSERT INTO orders(id, customer_id, product_id, account_id, amount, country," +  //" currency, email, percentage," +
                            "created, due_date, last_updated, time_of_day, transaction_time, transfer_time) " +
                            "VALUES (:id, :customer_id, :product_id, :account_id, :amount, :country, " +  //":currency, :email, :percentage," +
                            ":created, :due_date, :last_updated, :time_of_day, :transaction_time, :transfer_time)"
                )
                    .bind("id", orderId)
                    .bind("customer_id", customerId)
                    .bind("product_id", productId)
                    .bind("account_id", accountId)
                    .bind("amount", amount)
                    .bind("country", country)
//                    .bind("currency", currency)
//                    .bind("email", email)
//                    .bind("percentage", percentage)
                    .bind("created", created)
                    .bind("due_date", dueDate)
                    .bind("last_updated", lastUpdated)
                    .bind("time_of_day", timeOfDay)
                    .bind("transaction_time", transactionTime)
                    .bind("transfer_time", transferTime)
                    .execute()
            }
            val results =
                handle.inTransaction<ResultIterable<MutableMap<String?, Any?>?>?, RuntimeException?> { _handle: Handle? ->
                    handle.createQuery("SELECT * from orders WHERE id = :id")
                        .bind("id", orderId)
                        .mapToMap()
                }

            Assertions.assertThat(
                results
            ).isNotNull()

            val result = results!!.one()
            Assertions.assertThat(result!!["id"] as Long).isEqualTo(orderId.value)
            Assertions.assertThat(result["customer_id"])
                .isEqualTo(customerId.value)
            Assertions.assertThat(result["product_id"])
                .isEqualTo(productId.value)
            Assertions.assertThat(result["account_id"] as Long)
                .isEqualTo(accountId.value)
            Assertions.assertThat(result["amount"]).isEqualTo(amount.value)
            Assertions.assertThat(result["country"])
                .isEqualTo(country.value)
//            Assertions.assertThat(result["currency"])
//                .isEqualTo(currency.value)
//            Assertions.assertThat(result["email"]).isEqualTo(email.value)
//            Assertions.assertThat(result["percentage"])
//                .isEqualTo(percentage.value)
            Assertions.assertThat((result["created"] as Timestamp).toLocalDateTime())
                .isCloseTo(
                    created.value, Assertions.within(100, ChronoUnit.MICROS)
                )
            Assertions.assertThat((result["due_date"] as Date).toLocalDate())
                .isEqualTo(
                    dueDate.value
                )
            Assertions.assertThat((result["last_updated"] as Timestamp).toInstant())
                .isCloseTo(
                    lastUpdated.value, Assertions.within(100, ChronoUnit.MICROS)
                )
            Assertions.assertThat((result["time_of_day"] as Time))
                .isEqualTo(
                    Time.valueOf(
                        timeOfDay.value
                    )
                )
            Assertions.assertThat((result["transaction_time"] as Timestamp).toInstant())
                .isCloseTo(
                    transactionTime.value.toInstant(),
                    Assertions.within(100, ChronoUnit.MICROS)
                )
            Assertions.assertThat((result["transfer_time"] as Timestamp).toInstant())
                .isCloseTo(
                    transferTime.value.toInstant(), Assertions.within(100, ChronoUnit.MICROS)
                )


            val columns =
                List.of(
                    Tuple.of(
                        "id",
                        orderId
                    ),
                    Tuple.of(
                        "customer_id",
                        customerId
                    ),
                    Tuple.of(
                        "product_id",
                        productId
                    ),
                    Tuple.of(
                        "account_id",
                        accountId
                    ),
                    Tuple.of(
                        "amount",
                        amount
                    ),
                    Tuple.of(
                        "country",
                        country
                    ),
//                    Tuple.of(
//                        "currency",
//                        currency
//                    ),
//                    Tuple.of(
//                        "email",
//                        email
//                    ),
//                    Tuple.of(
//                        "percentage",
//                        percentage
//                    ),
                    Tuple.of(
                        "created",
                        created
                    ),
                    Tuple.of(
                        "due_date",
                        dueDate
                    ),
                    Tuple.of(
                        "last_updated",
                        lastUpdated
                    ),
                    Tuple.of(
                        "time_of_day",
                        timeOfDay
                    ),
                    Tuple.of(
                        "transaction_time",
                        transactionTime
                    ),
                    Tuple.of(
                        "transfer_time",
                        transferTime
                    )
                )
            columns!!.forEach(Consumer { column ->
                val columnValue = handle.inTransaction(
                    HandleCallback<Any, RuntimeException?> { _handle: Handle ->
                        handle.createQuery("SELECT " + column!!._1 + " from orders WHERE id = :id")
                            .bind("id", orderId)
                            .mapTo(column._2!!.javaClass)!!
                            .one()
                    })
                Assertions.assertThat(columnValue!!.javaClass == column!!._2!!.javaClass)
                    .describedAs(
                        "Column: '%s' of type '%s' with original value: '%s' and returned value '%s' of type '%s'",
                        column._1, column._2!!.javaClass, column._2, columnValue, columnValue.javaClass
                    )
                    .isTrue()
                var compareWithValue: Any? = column._2
                if (column._1 == "time_of_day") {
                    // Time looses precision
                    compareWithValue = TimeOfDay.of(
                        Time.valueOf(
                            (compareWithValue as TimeOfDay?)!!.value
                        ).toLocalTime()
                    )
                }
                if (columnValue is InstantValueType) {
                    Assertions.assertThat(columnValue.value)
                        .describedAs(
                            "Column: '%s' of type '%s' with original value: '%s' and returned value '%s' of type '%s'",
                            column._1, column._2!!.javaClass, compareWithValue, columnValue, columnValue.javaClass
                        )
                        .isCloseTo(
                            (compareWithValue as InstantValueType).value,
                            Assertions.within(100, ChronoUnit.MICROS)
                        )
                } else if (columnValue is LocalDateTimeValueType) {
                    Assertions.assertThat(columnValue.value)
                        .describedAs(
                            "Column: '%s' of type '%s' with original value: '%s' and returned value '%s' of type '%s'",
                            column._1, column._2!!.javaClass, compareWithValue, columnValue, columnValue.javaClass
                        )
                        .isCloseTo(
                            (compareWithValue as LocalDateTimeValueType)!!.value,
                            Assertions.within(100, ChronoUnit.MICROS)
                        )
                } else if (columnValue is OffsetDateTimeValueType) {
                    Assertions.assertThat(columnValue.value)
                        .describedAs(
                            "Column: '%s' of type '%s' with original value: '%s' and returned value '%s' of type '%s'",
                            column._1, column._2!!.javaClass, compareWithValue, columnValue, columnValue.javaClass
                        )
                        .isCloseTo(
                            (compareWithValue as OffsetDateTimeValueType).value,
                            Assertions.within(100, ChronoUnit.MICROS)
                        )
                } else if (columnValue is ZonedDateTimeValueType) {
                    Assertions.assertThat(columnValue.value)
                        .describedAs(
                            "Column: '%s' of type '%s' with original value: '%s' and returned value '%s' of type '%s'",
                            column._1, column._2!!.javaClass, compareWithValue, columnValue, columnValue.javaClass
                        )
                        .isCloseTo(
                            (compareWithValue as ZonedDateTimeValueType).value,
                            Assertions.within(100, ChronoUnit.MICROS)
                        )
                } else {
                    Assertions.assertThat(columnValue == compareWithValue)
                        .describedAs(
                            "Column: '%s' of type '%s' with original value: '%s' and returned value '%s' of type '%s'",
                            column._1, column._2!!.javaClass, compareWithValue, columnValue, columnValue.javaClass
                        )
                        .isTrue()
                }
            })
        }
    }
}