package dk.cloudcreate.essentials.components.foundation.lifecycle;

import java.util.Map;
import java.util.function.Consumer;

import dk.cloudcreate.essentials.components.foundation.Lifecycle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ApplicationContextEvent;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.ContextRefreshedEvent;

import static dk.cloudcreate.essentials.shared.FailFast.requireNonNull;

/**
 * Default {@link LifecycleManager} that integrate with Spring to ensure that {@link ApplicationContext} Beans that implement the {@link Lifecycle}
 * interface are started and stopped in response to {@link ContextRefreshedEvent} and {@link ContextClosedEvent}
 */
public final class DefaultLifecycleManager implements LifecycleManager, ApplicationListener<ApplicationContextEvent>, ApplicationContextAware {
    public static final Logger log = LoggerFactory.getLogger(DefaultLifecycleManager.class);
    private ApplicationContext applicationContext;
    private boolean hasStartedLifeCycleBeans;
    private Map<String, Lifecycle> lifeCycleBeans;
    private final Consumer<ApplicationContext> contextRefreshedEventConsumer;
    private final boolean isStartLifecycles;

    /**
     *
     * @param contextRefreshedEventConsumer callback that will be called after all {@link Lifecycle} Beans {@link Lifecycle#start()} has been called
     * @param isStartLifecycles determines if lifecycle beans should be started automatically
     */
    public DefaultLifecycleManager(Consumer<ApplicationContext> contextRefreshedEventConsumer,
                                   boolean isStartLifecycles) {
        this.contextRefreshedEventConsumer = requireNonNull(contextRefreshedEventConsumer);
        this.isStartLifecycles = isStartLifecycles;
        log.info("Initializing {} with isStartLifecycles = {}", this.getClass().getSimpleName(), isStartLifecycles);
    }

    /**
     *
     * @param isStartLifecycles determines if lifecycle beans should be started automatically
     */
    public DefaultLifecycleManager(boolean isStartLifecycles) {
        this(event -> {}, isStartLifecycles);
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    @Override
    public void onApplicationEvent(ApplicationContextEvent event) {
        if (event instanceof ContextRefreshedEvent) {
            log.info(event.getClass().getSimpleName());
            startLifecycleBeans();
        } else if (event instanceof ContextClosedEvent) {
            log.info("{} - has started life cycle beans: {}", event.getClass().getSimpleName(), hasStartedLifeCycleBeans);
            onContextClosed();
        }
    }

    private void onContextClosed() {
        if (hasStartedLifeCycleBeans) {
            lifeCycleBeans.forEach((beanName, lifecycleBean) -> {
                if (lifecycleBean.isStarted()) {
                    log.info("Stopping {} bean '{}' of type '{}'", Lifecycle.class.getSimpleName(), beanName, lifecycleBean.getClass().getName());
                    lifecycleBean.stop();
                }
            });
            hasStartedLifeCycleBeans = false;
        }
    }

    private void startLifecycleBeans() {
        if (!isStartLifecycles) {
            log.debug("Start of lifecycle beans is disabled");
            contextRefreshedEventConsumer.accept(this.applicationContext);
            return;
        }
        if (!hasStartedLifeCycleBeans) {
            contextRefreshedEventConsumer.accept(this.applicationContext);
            hasStartedLifeCycleBeans = true;
            lifeCycleBeans = applicationContext.getBeansOfType(Lifecycle.class);
            lifeCycleBeans.forEach((beanName, lifecycleBean) -> {
                if (!lifecycleBean.isStarted()) {
                    log.info("Starting {} bean '{}' of type '{}'", Lifecycle.class.getSimpleName(), beanName, lifecycleBean.getClass().getName());
                    lifecycleBean.start();
                }
            });
        }
    }

}
