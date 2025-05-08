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

package dk.trustworks.essentials.components.foundation.transaction.spring.mongo;

import dk.trustworks.essentials.components.foundation.transaction.mongo.ClientSessionAwareUnitOfWork;
import dk.trustworks.essentials.components.foundation.transaction.spring.*;
import org.springframework.data.mongodb.*;
import org.springframework.transaction.TransactionStatus;

import static dk.trustworks.essentials.shared.FailFast.requireNonNull;

public class SpringMongoTransactionAwareUnitOfWorkFactory extends SpringTransactionAwareUnitOfWorkFactory<MongoTransactionManager, SpringMongoTransactionAwareUnitOfWorkFactory.SpringMongoTransactionAwareUnitOfWork> {
    private MongoDatabaseFactory dbFactory;

    public SpringMongoTransactionAwareUnitOfWorkFactory(MongoTransactionManager transactionManager,
                                                        MongoDatabaseFactory dbFactory) {
        super(transactionManager);
        this.dbFactory = requireNonNull(dbFactory, "No dbFactory provided");
    }

    @Override
    protected Class<?> resolveUnitOfWorkType() {
        return SpringMongoTransactionAwareUnitOfWorkFactory.SpringMongoTransactionAwareUnitOfWork.class;
    }

    @Override
    protected SpringMongoTransactionAwareUnitOfWork createUnitOfWorkForFactoryManagedTransaction(TransactionStatus transaction) {
        return new SpringMongoTransactionAwareUnitOfWork(this, transaction);
    }

    @Override
    protected SpringMongoTransactionAwareUnitOfWork createUnitOfWorkForSpringManagedTransaction() {
        return new SpringMongoTransactionAwareUnitOfWork(this);
    }

    public static class SpringMongoTransactionAwareUnitOfWork extends SpringTransactionAwareUnitOfWork<MongoTransactionManager, SpringMongoTransactionAwareUnitOfWork> implements ClientSessionAwareUnitOfWork {


        public SpringMongoTransactionAwareUnitOfWork(SpringTransactionAwareUnitOfWorkFactory<MongoTransactionManager, SpringMongoTransactionAwareUnitOfWork> unitOfWorkFactory) {
            super(unitOfWorkFactory);
        }

        public SpringMongoTransactionAwareUnitOfWork(SpringTransactionAwareUnitOfWorkFactory<MongoTransactionManager, SpringMongoTransactionAwareUnitOfWork> unitOfWorkFactory, TransactionStatus manuallyManagedSpringTransaction) {
            super(unitOfWorkFactory, manuallyManagedSpringTransaction);
        }

        @Override
        protected void onStart() {
        }

        @Override
        protected void onCleanup() {
        }

//        @Override
//        public ClientSession clientSession() {
//            if (!dbFactory.isTransactionActive()) {
//                throw new UnitOfWorkException("No active transaction");
//            }
//            return dbFactory.getSession(CLIENT_SESSION_OPTIONS);
//        }
    }
}
