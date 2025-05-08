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

package dk.trustworks.essentials.components.distributed.fencedlock.springdata.mongo;

import com.mongodb.*;
import com.mongodb.client.*;
import dk.trustworks.essentials.components.foundation.fencedlock.LockName;
import dk.trustworks.essentials.types.springdata.mongo.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.data.mongodb.*;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;

import java.util.List;

import static java.util.concurrent.TimeUnit.SECONDS;

@SpringBootApplication
public class ApplicationTests {
    @Bean
    public SingleValueTypeRandomIdGenerator registerIdGenerator() {
        return new SingleValueTypeRandomIdGenerator();
    }

    @Bean
    public MongoCustomConversions mongoCustomConversions() {
        return new MongoCustomConversions(List.of(
                new SingleValueTypeConverter(LockName.class)));
    }

    @Bean
    public MongoTransactionManager transactionManager(MongoDatabaseFactory databaseFactory) {
        return new MongoTransactionManager(databaseFactory);
    }

    @Value("${spring.data.mongodb.uri}")
    private String mongoUri;

    @Bean
    public MongoClient mongoClient() {
        var settings = MongoClientSettings.builder()
                                          .applyConnectionString(new ConnectionString(mongoUri))
                                          .applyToSocketSettings(builder ->
                                                                         builder.connectTimeout(1, SECONDS)
                                                                                .readTimeout(1, SECONDS))
                                          .writeConcern(WriteConcern.MAJORITY.withWTimeout(1, SECONDS))
                                          .readConcern(ReadConcern.LOCAL)
                                          .build();

        // Create and return the MongoClient with custom settings
        return MongoClients.create(settings);
    }
}
