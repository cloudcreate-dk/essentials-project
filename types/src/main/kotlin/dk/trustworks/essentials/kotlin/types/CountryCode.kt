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
import dk.trustworks.essentials.shared.MessageFormatter
import java.util.*

/**
 * Immutable ISO-3166 2 character country code. Any values provided to the constructor or [.of]
 * will be validated for length, will ensure that the code is in UPPER CASE and finally validate that the country code is known
 * by performing a lookup in the set returned from [Locale.getISOCountries] using
 * [java.util.Locale.IsoCountryCode.PART1_ALPHA2]
 */
@JvmInline
value class CountryCode
/**
 * Create a typed [CountryCode] from a String ISO-3166 2 character country code
 *
 * @param countryCode the ISO-3166 2 character country code with the `countryCode` as UPPER CASE value
 * @throws IllegalArgumentException in case the  ISO-3166 2 character country code is not known or otherwise invalid.
 */
    (override val value: String) : StringValueType<CountryCode> {

    init {
        validate(value)
    }

    companion object {
        /**
         * Get the Set of String based ISO-3166 2 character country code's
         * @return the Set of String based ISO-3166 2 character country code's
         */
        val allCountryCodes: Set<String> = Locale.getISOCountries(Locale.IsoCountryCode.PART1_ALPHA2)

        private fun validate(countryCode: CharSequence): String {
            FailFast.requireNonNull(countryCode, "countryCode is null")
            require(countryCode.length == 2) {
                MessageFormatter.msg(
                    "CountryCode is invalid (must be 2 characters): '{}'",
                    countryCode
                )
            }

            val upperCaseCountryCode = countryCode.toString().uppercase(Locale.getDefault())
            require(allCountryCodes.contains(upperCaseCountryCode)) {
                MessageFormatter.msg(
                    "CountryCode '{}' is not known",
                    countryCode
                )
            }
            return upperCaseCountryCode
        }

        /**
         * Convert a String ISO-3166 2 character country code to a typed [CountryCode]
         *
         * @param countryCode the ISO-3166 2 character country code
         * @return the typed [CountryCode] with the `countryCode` as UPPER CASE value
         * @throws IllegalArgumentException in case the  ISO-3166 2 character country code is not known or otherwise invalid.
         */
        fun of(countryCode: String): CountryCode {
            return CountryCode(countryCode)
        }
    }
}