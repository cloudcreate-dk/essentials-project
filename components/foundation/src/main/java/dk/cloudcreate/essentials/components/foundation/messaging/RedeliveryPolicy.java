/*
 * Copyright 2021-2022 the original author or authors.
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

package dk.cloudcreate.essentials.components.foundation.messaging;

import java.time.Duration;
import java.util.Objects;

import static dk.cloudcreate.essentials.shared.FailFast.*;

public class RedeliveryPolicy {
    public final Duration initialRedeliveryDelay;
    public final Duration followupRedeliveryDelay;
    public final double   followupRedeliveryDelayMultiplier;
    public final Duration maximumFollowupRedeliveryThreshold;
    public final int      maximumNumberOfRedeliveries;

    public RedeliveryPolicy(Duration initialRedeliveryDelay,
                            Duration followupRedeliveryDelay,
                            double followupRedeliveryDelayMultiplier,
                            Duration maximumFollowupRedeliveryDelayThreshold,
                            int maximumNumberOfRedeliveries) {
        this.initialRedeliveryDelay = requireNonNull(initialRedeliveryDelay, "You must specify an initialRedeliveryDelay");
        this.followupRedeliveryDelay = requireNonNull(followupRedeliveryDelay, "You must specify an followupRedeliveryDelay");
        this.followupRedeliveryDelayMultiplier = followupRedeliveryDelayMultiplier;
        this.maximumFollowupRedeliveryThreshold = requireNonNull(maximumFollowupRedeliveryDelayThreshold, "You must specify an maximumFollowupRedeliveryDelayThreshold");
        this.maximumNumberOfRedeliveries = maximumNumberOfRedeliveries;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RedeliveryPolicy that = (RedeliveryPolicy) o;
        return Double.compare(that.followupRedeliveryDelayMultiplier, followupRedeliveryDelayMultiplier) == 0 &&
                maximumNumberOfRedeliveries == that.maximumNumberOfRedeliveries &&
                Objects.equals(initialRedeliveryDelay, that.initialRedeliveryDelay) &&
                Objects.equals(followupRedeliveryDelay, that.followupRedeliveryDelay) &&
                Objects.equals(maximumFollowupRedeliveryThreshold, that.maximumFollowupRedeliveryThreshold);
    }

    @Override
    public int hashCode() {
        return Objects.hash(initialRedeliveryDelay, followupRedeliveryDelay, followupRedeliveryDelayMultiplier,
                            maximumFollowupRedeliveryThreshold, maximumNumberOfRedeliveries);
    }

    @Override
    public String toString() {
        return "RedeliveryPolicy{" +
                "initialRedeliveryDelay=" + initialRedeliveryDelay +
                ", followupRedeliveryDelay=" + followupRedeliveryDelay +
                ", followupRedeliveryDelayMultiplier=" + followupRedeliveryDelayMultiplier +
                ", maximumFollowupRedeliveryThreshold=" + maximumFollowupRedeliveryThreshold +
                ", maximumNumberOfRedeliveries=" + maximumNumberOfRedeliveries +
                '}';
    }

    public Duration calculateNextRedeliveryDelay(int currentNumberOfRedeliveryAttempts) {
        requireTrue(currentNumberOfRedeliveryAttempts >= 0, "currentNumberOfRedeliveryAttempts must be 0 or larger");
        if (currentNumberOfRedeliveryAttempts == 0) {
            return initialRedeliveryDelay;
        }
        var calculatedRedeliveryDelay = initialRedeliveryDelay.plus(Duration.ofMillis((long) (followupRedeliveryDelay.toMillis() * followupRedeliveryDelayMultiplier)));
        if (calculatedRedeliveryDelay.compareTo(maximumFollowupRedeliveryThreshold) >= 0) {
            return maximumFollowupRedeliveryThreshold;
        } else {
            return calculatedRedeliveryDelay;
        }
    }

    public static RedeliveryPolicy fixedBackoff(Duration redeliveryDelay,
                                                int maximumNumberOfRedeliveries) {
        return new RedeliveryPolicy(redeliveryDelay,
                                    redeliveryDelay,
                                    1.0d,
                                    redeliveryDelay,
                                    maximumNumberOfRedeliveries);
    }

    public static RedeliveryPolicy linearBackoff(Duration redeliveryDelay,
                                                 Duration maximumFollowupRedeliveryDelayThreshold,
                                                 int maximumNumberOfRedeliveries) {
        return new RedeliveryPolicy(redeliveryDelay,
                                    redeliveryDelay,
                                    1.0d,
                                    maximumFollowupRedeliveryDelayThreshold,
                                    maximumNumberOfRedeliveries);
    }

    public static RedeliveryPolicy exponentialBackoff(Duration initialRedeliveryDelay,
                                                      Duration followupRedeliveryDelay,
                                                      double followupRedeliveryDelayMultiplier,
                                                      Duration maximumFollowupRedeliveryDelayThreshold,
                                                      int maximumNumberOfRedeliveries) {
        return new RedeliveryPolicy(initialRedeliveryDelay,
                                    followupRedeliveryDelay,
                                    followupRedeliveryDelayMultiplier,
                                    maximumFollowupRedeliveryDelayThreshold,
                                    maximumNumberOfRedeliveries);
    }

}
