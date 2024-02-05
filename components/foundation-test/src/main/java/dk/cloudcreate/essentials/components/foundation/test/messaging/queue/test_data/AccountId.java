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

package dk.cloudcreate.essentials.components.foundation.test.messaging.queue.test_data;

import dk.cloudcreate.essentials.shared.FailFast;
import dk.cloudcreate.essentials.types.*;

import java.util.Random;

public class AccountId extends IntegerType<AccountId> implements Identifier {
    private static Random RANDOM_ID_GENERATOR = new Random();

    public AccountId(Integer value) {
        super(value);
    }

    public static AccountId of(int value) {
        return new AccountId(value);
    }

    public static AccountId from(String longValue) {
        FailFast.requireNonNull(longValue, "You must supply a longValue");
        return of(Integer.parseInt(longValue));
    }

    public static AccountId ofNullable(Integer value) {
        return value != null ? new AccountId(value) : null;
    }

    public static AccountId random() {
        return new AccountId(RANDOM_ID_GENERATOR.nextInt());
    }
}
