package dk.trustworks.essentials.components.foundation.lifecycle;

import dk.trustworks.essentials.shared.Lifecycle;
import org.slf4j.*;
import org.springframework.beans.BeansException;
import org.springframework.context.*;

import java.util.Map;
import java.util.function.Consumer;

import static dk.trustworks.essentials.shared.FailFast.requireNonNull;

/**
 * Default {@link LifecycleManager} that integrate with Spring to ensure that {@link ApplicationContext} Beans
 * that registered Beans implementing the {@link Lifecycle} interface are started and stopped
 */
public final class DefaultLifecycleManager implements SmartLifecycle, LifecycleManager, ApplicationContextAware {
    public static final Logger                       log       = LoggerFactory.getLogger(DefaultLifecycleManager.class);
    private             ApplicationContext           applicationContext;
    private             boolean                      hasStartedLifeCycleBeans;
    private             Map<String, Lifecycle>       lifeCycleBeans;
    private final       Consumer<ApplicationContext> contextRefreshedEventConsumer;
    private final       boolean                      isStartLifecycles;
    private volatile    boolean                      isRunning = false;

    /**
     * @param contextRefreshedEventConsumer callback that will be called after all {@link Lifecycle} Beans {@link Lifecycle#start()} has been called
     * @param isStartLifecycles             determines if lifecycle beans should be started automatically
     */
    public DefaultLifecycleManager(Consumer<ApplicationContext> contextRefreshedEventConsumer,
                                   boolean isStartLifecycles) {
        this.contextRefreshedEventConsumer = requireNonNull(contextRefreshedEventConsumer);
        this.isStartLifecycles = isStartLifecycles;
        log.info("Initializing {} with isStartLifecycles = {}", this.getClass().getSimpleName(), isStartLifecycles);
    }

    /**
     * @param isStartLifecycles determines if lifecycle beans should be started automatically
     */
    public DefaultLifecycleManager(boolean isStartLifecycles) {
        this(event -> {
        }, isStartLifecycles);
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    @Override
    public void stop() {
        if (hasStartedLifeCycleBeans) {
            log.info("Stopping Essentials Lifecycle beans");
            lifeCycleBeans.forEach((beanName, lifecycleBean) -> {
                if (lifecycleBean.isStarted()) {
                    log.info("Stopping {} bean '{}' of type '{}'", Lifecycle.class.getSimpleName(), beanName, lifecycleBean.getClass().getName());
                    lifecycleBean.stop();
                }
            });
            hasStartedLifeCycleBeans = false;
            log.info("Essentials Lifecycle beans have been stopped");
        }
        isRunning = false;
    }

    @Override
    public void start() {
        if (!isStartLifecycles) {
            log.info("Start of lifecycle beans is disabled");
            return;
        }
        if (!hasStartedLifeCycleBeans) {
            log.info("Starting Essentials Lifecycle beans");
            hasStartedLifeCycleBeans = true;
            lifeCycleBeans = applicationContext.getBeansOfType(Lifecycle.class);
            lifeCycleBeans.forEach((beanName, lifecycleBean) -> {
                if (!lifecycleBean.isStarted()) {
                    log.info("Starting {} bean '{}' of type '{}'", Lifecycle.class.getSimpleName(), beanName, lifecycleBean.getClass().getName());
                    lifecycleBean.start();
                }
            });
            log.info("Essentials Lifecycle beans have been started");
            log.info("Calling {} contextRefreshedEventConsumer", this.getClass().getSimpleName());
            contextRefreshedEventConsumer.accept(this.applicationContext);
            log.info("Completed calling {} contextRefreshedEventConsumer", this.getClass().getSimpleName());
        }
        isRunning = true;
    }

    @Override
    public boolean isRunning() {
        return isRunning;
    }

    @Override
    public int getPhase() {
        // The higher the phase value the earlier it is shut down and the later it starts
        return Integer.MAX_VALUE - 1;
    }
}
