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

package dk.cloudcreate.essentials.types.avro;

import dk.cloudcreate.essentials.types.*;
import dk.cloudcreate.essentials.types.avro.test.Money;
import dk.cloudcreate.essentials.types.avro.test.*;
import dk.cloudcreate.essentials.types.avro.test.types.*;
import org.apache.avro.io.*;
import org.apache.avro.specific.*;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

public class CustomConversionsTest {

    /**
     * Test of the {@link Order} class generated by the <code>avro-maven-plugin</code> based on the <code>src/test/avro/order.avdl</code> file
     *
     * @throws IOException in case anything goes wrong during serialization and deserialization
     */
    @Test
    void test_Order_Serialization_and_Deserialization() throws IOException {
        // Given
        var totalAmountWithoutSalesTax = Amount.of("100.5");
        var salesTax                   = Percentage.from("25");
        var totalPrice                 = totalAmountWithoutSalesTax.add(salesTax.of(totalAmountWithoutSalesTax));
        var order = Order.newBuilder()
                         .setId(OrderId.of("TestOrderId"))
                         .setTotalAmountWithoutSalesTax(totalAmountWithoutSalesTax)
                         .setCurrency(CurrencyCode.of("DKK"))
                         .setCountry(CountryCode.of("DK"))
                         .setSalesTax(salesTax)
                         .setEmail(EmailAddress.of("john@nonexistingdomain.com"))
                         .setTotalPrice(Money.newBuilder()
                                             .setAmount(totalPrice)
                                             .setCurrency(CurrencyCode.DKK)
                                             .build())
                         .setArrayOfCurrencies(List.of(CurrencyCode.of("USD"), CurrencyCode.of("DKK"), CurrencyCode.of("EUR")))
                         .setMapOfCurrencyValues(Map.of("USD", CurrencyCode.of("USD"),
                                                        "DKK", CurrencyCode.of("DKK"),
                                                        "EUR", CurrencyCode.of("EUR")
                                                       ))
                         .setOptionalCurrency(CurrencyCode.of("EUR"))
                         .setDueDate(DueDate.now())
                         .setTimeOfDay(TimeOfDay.now())
                         .setCreated(Created.now())
                         .setLastUpdated(LastUpdated.now())
                         .setTransactionTime(TransactionTime.now())
                         .setTransferTime(TransferTime.now())
                         .build();

        // When
        var serializedValue = serialize(order);
        // And
        var deserializedOrder = deserialize(serializedValue, Order.class);

        // Then
        assertThat((CharSequence) deserializedOrder.getId()).isEqualTo(order.getId());
        assertThat(deserializedOrder.getTotalAmountWithoutSalesTax()).isEqualTo(order.getTotalAmountWithoutSalesTax());
        assertThat((CharSequence) deserializedOrder.getCurrency()).isEqualTo(order.getCurrency());
        assertThat((CharSequence) deserializedOrder.getCountry()).isEqualTo(order.getCountry());
        assertThat(deserializedOrder.getSalesTax()).isEqualTo(order.getSalesTax());
        assertThat((CharSequence) deserializedOrder.getEmail()).isEqualTo(order.getEmail());
        assertThat(deserializedOrder.getTotalPrice()).isEqualTo(order.getTotalPrice());
        assertThat(deserializedOrder.getTotalPrice().getAmount()).isEqualTo(totalPrice);
        assertThat((CharSequence) deserializedOrder.getTotalPrice().getCurrency()).isEqualTo(CurrencyCode.DKK);
        assertThat(deserializedOrder.getArrayOfCurrencies()).isEqualTo(order.getArrayOfCurrencies());
        assertThat(deserializedOrder.getMapOfCurrencyValues()).isEqualTo(order.getMapOfCurrencyValues());
        assertThat((CharSequence) deserializedOrder.getOptionalCurrency()).isEqualTo(order.getOptionalCurrency());
        assertThat(deserializedOrder.getDueDate()).isEqualTo(order.getDueDate());
        assertThat(deserializedOrder.getTimeOfDay()).isEqualTo(order.getTimeOfDay());
        assertThat(deserializedOrder.getCreated()).isEqualTo(order.getCreated());
        assertThat(deserializedOrder.getLastUpdated()).isEqualTo(order.getLastUpdated());
        assertThat(deserializedOrder.getTransactionTime()).isEqualTo(order.getTransactionTime());
        assertThat(deserializedOrder.getTransferTime()).isEqualTo(order.getTransferTime());
        assertThat(deserializedOrder).isEqualTo(order);
    }

    @SuppressWarnings("unchecked")
    private <T> byte[] serialize(T object) throws IOException {
        var outputStream = new ByteArrayOutputStream();
        new SpecificDatumWriter<>((Class<T>) object.getClass())
                .write(object, EncoderFactory.get().directBinaryEncoder(outputStream, null));
        return outputStream.toByteArray();
    }

    private <T> T deserialize(byte[] serializedValue, Class<T> type) throws IOException {
        var inputStream = new ByteArrayInputStream(serializedValue);
        return new SpecificDatumReader<>(type)
                .read(null, DecoderFactory.get().directBinaryDecoder(inputStream, null));
    }
}
