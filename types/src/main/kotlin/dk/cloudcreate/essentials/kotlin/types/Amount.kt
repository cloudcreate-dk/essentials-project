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

package dk.cloudcreate.essentials.kotlin.types

import dk.cloudcreate.essentials.shared.FailFast
import dk.cloudcreate.essentials.types.CurrencyCode
import dk.cloudcreate.essentials.types.Money
import java.math.BigDecimal
import java.math.MathContext

/**
 * Represents an immutable Monetary Amount without any [CurrencyCode]
 *
 * @see Money
 */
@JvmInline
value class Amount(override val value: BigDecimal) : BigDecimalValueType {

    constructor(value: Long) : this(BigDecimal.valueOf(value))

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