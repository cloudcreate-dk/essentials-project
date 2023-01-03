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

/**
 * Due to the <code>avro-maven-plugin</code> running during <code>generate-test-sources</code> phase, and the <code>order.avdl</code> includes {@link org.apache.avro.Conversions}/{@link org.apache.avro.LogicalTypes.LogicalTypeFactory}
 * in the <code>test</code> package, as well as <b>types</b> defined in the <code>test/types</code> subpackage,
 * we need to include these test-classes in the actual source directly.<br>
 * To compensate for this the <code>maven-jar-plugin</code> <b>excludes</b> the <code>test</code> package and all its subpackages
 */
package dk.cloudcreate.essentials.types.avro.test;