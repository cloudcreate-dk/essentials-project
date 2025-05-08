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

package dk.trustworks.essentials.components.document_db.postgresql

import dk.trustworks.essentials.components.document_db.postgresql.EntityConfiguration.Companion.isKotlinOrJavaBuiltInType
import dk.trustworks.essentials.components.foundation.postgresql.PostgresqlUtil
import dk.trustworks.essentials.components.foundation.types.RandomIdGenerator
import dk.trustworks.essentials.types.CharSequenceType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.*
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZonedDateTime
import kotlin.reflect.KProperty1
import kotlin.reflect.full.memberProperties

class EntityConfigurationTest {
    @Test
    fun `Test with Kotlin built-in types`() {
        assertTrue(isKotlinOrJavaBuiltInType(String::class))
        assertTrue(isKotlinOrJavaBuiltInType(Int::class))
    }

    @Test
    fun `Test with Java built-in types`() {
        assertTrue(isKotlinOrJavaBuiltInType(java.lang.Integer::class))
        assertTrue(isKotlinOrJavaBuiltInType(java.lang.String::class))
    }

    @Test
    fun `Test with collections and maps`() {
        assertTrue(isKotlinOrJavaBuiltInType(List::class))
        assertTrue(isKotlinOrJavaBuiltInType(Map::class))
        assertTrue(isKotlinOrJavaBuiltInType(ArrayList::class))
        assertTrue(isKotlinOrJavaBuiltInType(HashMap::class))
    }

    @Test
    fun `Test with number and char sequence types`() {
        assertTrue(isKotlinOrJavaBuiltInType(Double::class))
        assertTrue(isKotlinOrJavaBuiltInType(CharSequence::class))
        assertTrue(isKotlinOrJavaBuiltInType(CustomerId::class))
        assertTrue(OrderId::class.isValue)
    }

    @Test
    fun `Test with java math package`() {
        assertTrue(isKotlinOrJavaBuiltInType(BigDecimal::class))
    }

    @Test
    fun `Test with custom class`() {
        assertFalse(isKotlinOrJavaBuiltInType(MyCustomClass::class))
    }

    @Test
    fun `Test with boxed and primitive types`() {
        assertTrue(isKotlinOrJavaBuiltInType(java.lang.Integer::class)) // Example for boxed type
        assertTrue(isKotlinOrJavaBuiltInType(Int::class)) // Example for primitive type (handled by Kotlin)
    }

    // --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------
    private lateinit var mockedStatic: MockedStatic<PostgresqlUtil>

    // Initialize Mockito annotations and captors
    @Captor
    lateinit var argumentCaptorContext: ArgumentCaptor<String>

    private lateinit var checkedContexts : MutableList<String>

    @BeforeEach
    fun setUp() {
        checkedContexts = mutableListOf<String>()
        MockitoAnnotations.openMocks(this)
        mockedStatic = Mockito.mockStatic(PostgresqlUtil::class.java)
        // Setup the static mock behavior
        mockedStatic.`when`<Any> { PostgresqlUtil.checkIsValidTableOrColumnName(Mockito.anyString(), Mockito.anyString()) }
            .thenAnswer { invocation ->
                val context = invocation.getArgument<String>(1)
                checkedContexts.add(context) // Capture the context for each call
                null // Since the original method is void, return null
            }
    }

    @AfterEach
    fun tearDown() {
        mockedStatic.close()
    }

    @Test
    fun `checkPropertyNames - test property name checking`() {
        // Execute
        Person::class.memberProperties.forEach {
            EntityConfiguration.checkPropertyNames(it as KProperty1<Any, *>)
        }

        // Verification
        val expectedCheckedNames = listOf("name", "address", "address.street", "address.city", "phoneNumbers")
        assertThat(checkedContexts).containsAll(expectedCheckedNames)
    }

    @Test
    fun `checkPropertyNames - data class with Java semantic type`() {
        // Execute
        Customer::class.memberProperties.forEach {
            EntityConfiguration.checkPropertyNames(it as KProperty1<Any, *>)
        }

        // Verification
        val expectedCheckedNames = listOf("id", "name", "address", "address.street", "address.city", "phoneNumbers")
        assertThat(checkedContexts).containsAll(expectedCheckedNames)
    }

    @Test
    fun `checkPropertyNames - test recursive data structure`() {
        class RecursiveEntity(val id: Int, val parent: RecursiveEntity?)

        RecursiveEntity::class.memberProperties.forEach {
            EntityConfiguration.checkPropertyNames(it as KProperty1<Any, *>)
        }

        // Verification
        assertThat(checkedContexts).containsAll(listOf("id", "parent.id"))
    }

    @Test
    fun `checkPropertyNames - test built-in types`() {
        class BuiltInTypesEntity(val id: Int, val name: String)

        BuiltInTypesEntity::class.memberProperties.forEach {
            EntityConfiguration.checkPropertyNames(it as KProperty1<Any, *>)
        }

        // Verification
        assertThat(checkedContexts).containsAll(listOf("id", "name"))
    }

    @Test
    fun `checkPropertyNames - test value class`() {
        class Order(val id: OrderId)

        Order::class.memberProperties.forEach {
            EntityConfiguration.checkPropertyNames(it as KProperty1<Any, *>)
        }

        // Verification
        assertThat(checkedContexts).containsAll(listOf("id"))
    }

    @Test
    fun `checkPropertyNames - test collections and maps`() {
        class CollectionsEntity(val tags: List<String>, val attributes: Map<String, String>)


        CollectionsEntity::class.memberProperties.forEach {
            EntityConfiguration.checkPropertyNames(it as KProperty1<Any, *>)
        }

        // Verification
        assertThat(checkedContexts).containsAll(listOf("tags", "attributes"))
    }

    @Test
    fun `checkPropertyNames - test BigDecimal`() {
        class FinancialRecord(val amount: BigDecimal, val quantity: Int)


        FinancialRecord::class.memberProperties.forEach {
            EntityConfiguration.checkPropertyNames(it as KProperty1<Any, *>)
        }

        // Verification
        assertThat(checkedContexts).containsAll(listOf("amount", "quantity"))
    }


    @Test
    fun `checkPropertyNames - Java time types`() {
        class TimeEntity(
            val createdDate: LocalDate,
            val createdDateTime: LocalDateTime,
            val createdInstant: Instant,
            val createdZonedDateTime: ZonedDateTime
        )

        TimeEntity::class.memberProperties.forEach {
            EntityConfiguration.checkPropertyNames(it as KProperty1<Any, *>)
        }

        // Verification
        assertThat(checkedContexts).containsAll(listOf("createdDate", "createdDateTime", "createdInstant", "createdZonedDateTime"))
    }

    // --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------

    class MyCustomClass

    class CustomerId protected constructor(value: CharSequence) : CharSequenceType<CustomerId>(value) {
        companion object {
            fun random(): CustomerId {
                return CustomerId(RandomIdGenerator.generate())
            }

            fun of(id: CharSequence): CustomerId {
                return CustomerId(id)
            }
        }
    }

    data class Customer(val id: CustomerId, val name: String, val address: Address, val phoneNumbers: List<String>)
    class Person(val name: String, val address: Address, val phoneNumbers: List<String>)
    class Address(val street: String, val city: String)
}