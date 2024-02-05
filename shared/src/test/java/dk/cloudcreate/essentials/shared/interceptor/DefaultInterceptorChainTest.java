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

package dk.cloudcreate.essentials.shared.interceptor;

import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultInterceptorChainTest {
    @Test
    void verifyInterceptorOrdering() {
        var defaultOrder = new DefaultOrderInterceptor();
        var defaultOrder2 = new DefaultOrder2Interceptor();
        var interceptors = new ArrayList<>(List.of(new LowImportanceInterceptor(),
                                                   defaultOrder2,
                                                   new MostImportantInterceptor(),
                                                   defaultOrder,
                                                   new MediumImportantInterceptor()));

        DefaultInterceptorChain.sortInterceptorsByOrder(interceptors);

        assertThat(interceptors.get(0)).isInstanceOf(MostImportantInterceptor.class);
        assertThat(interceptors.get(1)).isInstanceOf(MediumImportantInterceptor.class);
        assertThat(interceptors.get(2)).isIn(defaultOrder, defaultOrder2);
        assertThat(interceptors.get(3)).isIn(defaultOrder, defaultOrder2);
        assertThat(interceptors.get(2)).isNotEqualTo(interceptors.get(3));
        assertThat(interceptors.get(4)).isInstanceOf(LowImportanceInterceptor.class);
    }

    @InterceptorOrder(1)
    static class MostImportantInterceptor implements Interceptor {

    }

    @InterceptorOrder(5)
    static class MediumImportantInterceptor implements Interceptor {

    }

    @InterceptorOrder
    static class DefaultOrderInterceptor implements Interceptor {

    }

    static class DefaultOrder2Interceptor implements Interceptor {

    }

    @InterceptorOrder(20)
    static class LowImportanceInterceptor implements Interceptor {

    }

}