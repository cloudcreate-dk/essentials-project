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

package dk.trustworks.essentials.shared.measurement;

import java.util.*;

import static dk.trustworks.essentials.shared.FailFast.requireNonNull;

/**
 * Immutable context for a measurement that encapsulates metric name, description, and associated tags.
 */
public class MeasurementContext {
    private final String              metricName;
    private final String              description;
    private final Map<String, String> tags;

    private MeasurementContext(Builder builder) {
        this.metricName = builder.metricName;
        this.description = builder.description;
        this.tags = Collections.unmodifiableMap(new HashMap<>(builder.tags));
    }

    /**
     * @return the metric name for this measurement
     */
    public String getMetricName() {
        return metricName;
    }

    /**
     * @return the description of this measurement
     */
    public String getDescription() {
        return description;
    }

    /**
     * @return an unmodifiable map of tags associated with this measurement
     */
    public Map<String, String> getTags() {
        return tags;
    }

    /**
     * Creates a new builder for a measurement context with the specified metric name.
     *
     * @param metricName the metric name
     * @return a builder instance
     */
    public static Builder builder(String metricName) {
        return new Builder(metricName);
    }

    /**
     * Builder for creating instances of {@link MeasurementContext}.
     */
    public static class Builder {
        private final String              metricName;
        private       String              description = "";
        private final Map<String, String> tags        = new HashMap<>();

        /**
         * Creates a new Builder with the given metric name.
         *
         * @param metricName the metric name
         */
        public Builder(String metricName) {
            this.metricName = requireNonNull(metricName, "No metricName provided");
        }

        /**
         * Sets the description for the measurement.
         *
         * @param description the description text
         * @return the builder instance
         */
        public Builder description(String description) {
            this.description = requireNonNull(description, "No description provided");
            return this;
        }

        /**
         * Adds a tag to the measurement.
         *
         * @param key   the tag key
         * @param value the tag value
         * @return the builder instance
         */
        public Builder tag(String key, CharSequence value) {
            this.tags.put(requireNonNull(key, "No key provided"),
                          requireNonNull(value, "No value provided").toString());
            return this;
        }

        /**
         * Adds a tag to the measurement.
         *
         * @param key   the tag key
         * @param value the tag value
         * @return the builder instance
         */
        public Builder tag(String key, String value) {
            this.tags.put(requireNonNull(key, "No key provided"),
                          requireNonNull(value, "No value provided"));
            return this;
        }

        /**
         * Adds an optional tag to the measurement. If the value is null then the tag isn't added
         *
         * @param key   the tag key
         * @param value the tag value
         * @return the builder instance
         */
        public Builder optionalTag(String key, String value) {
            if (value != null) {
                this.tags.put(requireNonNull(key, "No key provided"),
                              value);
            }
            return this;
        }

        /**
         * Adds a tag to the measurement.
         *
         * @param key   the tag key
         * @param value the tag value
         * @return the builder instance
         */
        public Builder tag(String key, int value) {
            this.tags.put(requireNonNull(key, "No key provided"),
                          Integer.toString(value));
            return this;
        }

        /**
         * Builds the {@link MeasurementContext} instance.
         *
         * @return the immutable MeasurementContext
         */
        public MeasurementContext build() {
            return new MeasurementContext(this);
        }
    }
}
