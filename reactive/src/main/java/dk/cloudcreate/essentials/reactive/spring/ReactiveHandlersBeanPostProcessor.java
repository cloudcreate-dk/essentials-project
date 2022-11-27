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

package dk.cloudcreate.essentials.reactive.spring;

import dk.cloudcreate.essentials.reactive.*;
import dk.cloudcreate.essentials.reactive.command.*;
import org.slf4j.*;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.DestructionAwareBeanPostProcessor;
import org.springframework.context.*;

/**
 * When using Spring or Spring Boot it will be easier to register the {@link LocalEventBus}, {@link LocalCommandBus}, {@link CommandHandler} and {@link EventHandler} instances as {@literal @Bean} or {@literal @Component}
 * and automatically have the {@link CommandHandler} beans registered as with the single {@link LocalCommandBus} bean and the {@link EventHandler} beans registered as subscribers with the single {@link LocalEventBus} bean.
 * <br>
 * All you need to do is to add a  {@literal @Bean}  of type {@link ReactiveHandlersBeanPostProcessor} which:
 * Registers {@link CommandHandler} or {@link EventHandler}'s in the application context with the <b>single</b>
 * {@link LocalEventBus} and {@link LocalCommandBus} defined in the application context.<br>
 * {@link EventHandler}'s are registered as synchronous event handler's ({@link LocalEventBus#addSyncSubscriber(EventHandler)})
 * unless it's annotated with the {@link AsyncEventHandler} annotation, in which case it's registered as an asynchronous event handler
 * ({@link LocalEventBus#addAsyncSubscriber(EventHandler)}
 */
public class ReactiveHandlersBeanPostProcessor implements DestructionAwareBeanPostProcessor, ApplicationContextAware {
    private static final Logger log = LoggerFactory.getLogger(ReactiveHandlersBeanPostProcessor.class);
    private ApplicationContext applicationContext;
    private LocalEventBus      eventBus;
    private LocalCommandBus    commandBus;

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (isEventHandler(bean)) {
            var actualHandlerClass = resolveBeanClass(bean);
            if (actualHandlerClass.isAnnotationPresent(AsyncEventHandler.class)) {
                log.debug("Adding asynchronous event handler '{}' of type '{}' to the {}",
                         beanName,
                         actualHandlerClass.getName(),
                         LocalEventBus.class.getSimpleName());
                eventBus().addAsyncSubscriber((EventHandler) bean);
            } else {
                log.debug("Adding synchronous event handler '{}' of type '{}' to the {}",
                         beanName,
                         actualHandlerClass.getName(),
                         LocalEventBus.class.getSimpleName());
                eventBus().addSyncSubscriber((EventHandler) bean);
            }
        } else if (isCommandHandler(bean)) {
            var actualHandlerClass = resolveBeanClass(bean);
            log.debug("Adding command handler '{}' of type '{}' to the {}",
                      beanName,
                      actualHandlerClass.getName(),
                      LocalCommandBus.class.getSimpleName());
            commandBus().addCommandHandler((CommandHandler) bean);
        }
        return bean;
    }
    @Override
    public void postProcessBeforeDestruction(Object bean, String beanName) throws BeansException {
        if (isEventHandler(bean)) {
            var actualHandlerClass = resolveBeanClass(bean);
            if (actualHandlerClass.isAnnotationPresent(AsyncEventHandler.class)) {
                eventBus().removeAsyncSubscriber((EventHandler) bean);
            } else {
                eventBus().removeSyncSubscriber((EventHandler) bean);
            }
        } else if (isCommandHandler(bean)) {
            commandBus().removeCommandHandler((CommandHandler) bean);
        }
    }
    @Override
    public boolean requiresDestruction(Object bean) {
        return isReactiveHandler(bean);
    }


    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    private LocalEventBus eventBus() {
        if (eventBus == null) {
            eventBus = applicationContext.getBean(LocalEventBus.class);
        }
        return eventBus;
    }

    private LocalCommandBus commandBus() {
        if (commandBus == null) {
            commandBus = applicationContext.getBean(LocalCommandBus.class);
        }
        return commandBus;
    }

    private boolean isReactiveHandler(Object bean) {
        var actualBeanClass = resolveBeanClass(bean);
        return EventHandler.class.isAssignableFrom(actualBeanClass) || CommandHandler.class.isAssignableFrom(actualBeanClass);
    }

    private boolean isEventHandler(Object bean) {
        var actualBeanClass = resolveBeanClass(bean);
        return EventHandler.class.isAssignableFrom(actualBeanClass);
    }

    private boolean isCommandHandler(Object bean) {
        var actualBeanClass = resolveBeanClass(bean);
        return CommandHandler.class.isAssignableFrom(actualBeanClass);
    }

    private Class<?> resolveBeanClass(Object bean) {
        return AopUtils.getTargetClass(bean);
    }
}
