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

package dk.cloudcreate.essentials.kotlin.types.jdbi.model

import dk.cloudcreate.essentials.kotlin.types.StringValueType
import dk.cloudcreate.essentials.kotlin.types.jdbi.StringValueTypeArgumentFactory
import dk.cloudcreate.essentials.kotlin.types.jdbi.StringValueTypeColumnMapper
import dk.cloudcreate.essentials.types.Identifier
import java.util.*

@JvmInline
value class ProductId(override val value: String) : StringValueType, Identifier {
    companion object {
        fun of(value: String): ProductId {
            return ProductId(value)
        }

        fun random(): ProductId {
            return ProductId(UUID.randomUUID().toString())
        }
    }
}

class ProductIdArgumentFactory : StringValueTypeArgumentFactory<ProductId>() {
}

class ProductIdColumnMapper : StringValueTypeColumnMapper<ProductId>() {
}
