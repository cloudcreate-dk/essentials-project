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

package dk.trustworks.essentials.shared.functional;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.function.BiFunction;

import static org.assertj.core.api.Assertions.*;

class CheckedBiFunctionTest {

    @Test
    void test_the_CheckedBiFunctions_apply_method_works_like_BiFunctions_apply_method_when_no_exception_has_been_thrown() {
        var control = new Object() {
            boolean applyCalled = false;
            Integer argument1Value = null;
            Boolean argument2Value = null;
        };

        var result = runTester(100, true, CheckedBiFunction.safe((arg1, arg2) -> {
            control.applyCalled = true;
            control.argument1Value = arg1;
            control.argument2Value = arg2;
            return "test";
        }));

        assertThat(control.applyCalled).isTrue();
        assertThat(control.argument1Value).isEqualTo(100);
        assertThat(control.argument2Value).isTrue();
        assertThat(result).isEqualTo("test");

    }

    @Test
    void test_the_CheckedBiFunctions_apply_method_with_contextmessage_works_like_BiFunctions_apply_method_when_no_exception_has_been_thrown() {
        var control = new Object() {
            boolean applyCalled = false;
            Integer argument1Value = null;
            Boolean argument2Value = null;
        };

        var result = runTester(100, true, CheckedBiFunction.safe("context-message",
                                                                 (arg1, arg2) -> {
                                                                     control.applyCalled = true;
                                                                     control.argument1Value = arg1;
                                                                     control.argument2Value = arg2;
                                                                     return "test";
                                                                 }));

        assertThat(control.applyCalled).isTrue();
        assertThat(control.argument1Value).isEqualTo(100);
        assertThat(control.argument2Value).isTrue();
        assertThat(result).isEqualTo("test");
    }

    @Test
    void test_the_CheckedBiFunctions_apply_method_catches_and_rethrows_a_checked_exception() {
        var control = new Object() {
            boolean applyCalled = false;
            Integer argument1Value = null;
            Boolean argument2Value = null;
        };

        var rootCause = new IOException("Test");
        assertThatThrownBy(() ->
                                   runTester(100, true, CheckedBiFunction.safe((arg1, arg2) -> {
                                       control.applyCalled = true;
                                       control.argument1Value = arg1;
                                       control.argument2Value = arg2;
                                       throw rootCause;
                                   }))
                          ).hasCause(rootCause);
        assertThat(control.applyCalled).isTrue();
        assertThat(control.argument1Value).isEqualTo(100);
        assertThat(control.argument2Value).isTrue();
    }

    @Test
    void test_the_CheckedBiFunctions_apply_method_with_contextmessage_catches_and_rethrows_a_checked_exception() {
        var control = new Object() {
            boolean applyCalled = false;
            Integer argument1Value = null;
            Boolean argument2Value = null;
        };

        var rootCause = new IOException("Test");
        assertThatThrownBy(() ->
                                   runTester(100, true, CheckedBiFunction.safe("context-message",
                                                                               (arg1, arg2) -> {
                                                                                   control.applyCalled = true;
                                                                                   control.argument1Value = arg1;
                                                                                   control.argument2Value = arg2;
                                                                                   throw rootCause;
                                                                               }))
                          ).isInstanceOf(CheckedExceptionRethrownException.class)
                           .hasMessage("context-message")
                           .hasRootCause(rootCause);
        assertThat(control.applyCalled).isTrue();
        assertThat(control.argument1Value).isEqualTo(100);
        assertThat(control.argument2Value).isTrue();
    }

    @Test
    void test_the_CheckedBiFunctions_apply_method_does_not_catch_a_runtime_exception() {
        var control = new Object() {
            boolean applyCalled = false;
            Integer argument1Value = null;
            Boolean argument2Value = null;
        };

        var rootCause = new RuntimeException("Test");
        assertThatThrownBy(() ->
                                   runTester(100, true, CheckedBiFunction.safe((arg1, arg2) -> {
                                       control.applyCalled = true;
                                       control.argument1Value = arg1;
                                       control.argument2Value = arg2;
                                       throw rootCause;
                                   }))
                          ).isEqualTo(rootCause);
        assertThat(control.applyCalled).isTrue();
        assertThat(control.argument1Value).isEqualTo(100);
        assertThat(control.argument2Value).isTrue();
    }

    @Test
    void test_the_CheckedBiFunctions_apply_method_with_contextmessage_does_not_catch_a_runtime_exception() {
        var control = new Object() {
            boolean applyCalled = false;
            Integer argument1Value = null;
            Boolean argument2Value = null;
        };

        var rootCause = new RuntimeException("Test");
        assertThatThrownBy(() ->
                                   runTester(100, true, CheckedBiFunction.safe("context-message",
                                                                               (arg1, arg2) -> {
                                                                                   control.applyCalled = true;
                                                                                   control.argument1Value = arg1;
                                                                                   control.argument2Value = arg2;
                                                                                   throw rootCause;
                                                                               }))
                          ).isEqualTo(rootCause);
        assertThat(control.applyCalled).isTrue();
        assertThat(control.argument1Value).isEqualTo(100);
        assertThat(control.argument2Value).isTrue();
    }

    private static <T1, T2, R> R runTester(T1 t1, T2 t2, BiFunction<T1, T2, R> function) {
        return function.apply(t1, t2);
    }

}