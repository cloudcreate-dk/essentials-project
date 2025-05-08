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

package dk.trustworks.essentials.kotlin.types

import dk.trustworks.essentials.shared.FailFast
import dk.trustworks.essentials.types.CurrencyCode
import dk.trustworks.essentials.types.Money
import java.math.BigDecimal
import java.math.MathContext

/**
 * Represents an immutable Monetary Amount without any [CurrencyCode]
 *
 * @see Money
 */
@JvmInline
value class Amount(override val value: BigDecimal) : BigDecimalValueType<Amount> {

    constructor(value: Long) : this(BigDecimal.valueOf(value))
    constructor(value: Double) : this(BigDecimal.valueOf(value))
    constructor(value: String) : this(BigDecimal(value))

    operator fun plus(other: Amount) = Amount(value + other.value)
    operator fun minus(other: Amount) = Amount(value - other.value)
    operator fun times(other: Amount) = Amount(value * other.value)
    operator fun div(other: Amount) = Amount(value / other.value)
    operator fun unaryPlus() = this
    operator fun unaryMinus() = Amount(-value)
    fun abs() = Amount(value.abs())

    companion object {
        val ZERO: Amount = of(BigDecimal.ZERO)

        fun zero(): Amount {
            return ZERO
        }

        fun of(value: String): Amount {
            return Amount(BigDecimal(value))
        }

        fun of(value: String, mathContext: MathContext): Amount {
            return Amount(
                BigDecimal(
                    FailFast.requireNonNull(value, "value is null"),
                    FailFast.requireNonNull(mathContext, "mathContext is null")
                )
            )
        }

        fun ofNullable(value: String?): Amount? {
            return if (value != null) Amount(BigDecimal(value)) else null
        }

        fun ofNullable(value: String?, mathContext: MathContext): Amount? {
            return if (value != null) Amount(
                BigDecimal(
                    value,
                    FailFast.requireNonNull(mathContext, "mathContext is null")
                )
            ) else null
        }

        fun of(value: BigDecimal): Amount {
            return Amount(value)
        }

        fun ofNullable(value: BigDecimal?): Amount? {
            return if (value != null) Amount(value) else null
        }
    }
}