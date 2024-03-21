package dk.cloudcreate.essentials.components.foundation.lifecycle;

import java.util.Map;
import java.util.function.Consumer;

import dk.cloudcreate.essentials.components.foundation.Lifecycle;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.ContextRefreshedEvent;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DefaultLifecycleManagerTest {
    private static final String BEAN_NAME = "beanName";

    @Test
    void startLifeCycleBeansTest() {
        var applicationContext = mock(ApplicationContext.class);
        var lifeCycleBean = mock(Lifecycle.class);
        when(lifeCycleBean.isStarted()).thenReturn(false);
        when(applicationContext.getBeansOfType(Lifecycle.class)).thenReturn(Map.of(BEAN_NAME, lifeCycleBean));

        var manager = new DefaultLifecycleManager(true);
        manager.setApplicationContext(applicationContext);

        manager.onApplicationEvent(mock(ContextRefreshedEvent.class));
        verify(lifeCycleBean).start();
        verify(lifeCycleBean, times(0)).stop();

        when(lifeCycleBean.isStarted()).thenReturn(true);
        manager.onApplicationEvent(mock(ContextClosedEvent.class));
        verify(lifeCycleBean).stop();
    }

    @Test
    void lifeCycleBeanAlreadyStartedTest() {
        var applicationContext = mock(ApplicationContext.class);
        var lifecycleBean = mock(Lifecycle.class);
        when(lifecycleBean.isStarted()).thenReturn(true);
        when(applicationContext.getBeansOfType(Lifecycle.class)).thenReturn(Map.of(BEAN_NAME, lifecycleBean));

        var manager = new DefaultLifecycleManager(true);
        manager.setApplicationContext(applicationContext);

        manager.onApplicationEvent(mock(ContextRefreshedEvent.class));
        verify(lifecycleBean, times(0)).start();
    }

    @Test
    void onContextRefreshedEventCustomConsumerTest() {
        var applicationContext = mock(ApplicationContext.class);
        when(applicationContext.getBeansOfType(Lifecycle.class)).thenReturn(Map.of());

        Consumer<ApplicationContext> consumer = mock(Consumer.class);
        var manager = new DefaultLifecycleManager(consumer, true);
        manager.setApplicationContext(applicationContext);

        manager.onApplicationEvent(mock(ContextRefreshedEvent.class));
        verify(consumer).accept(applicationContext);
    }

    @Test
    void dontStartLifeCycleBeansTest() {
        var applicationContext = mock(ApplicationContext.class);
        var manager = new DefaultLifecycleManager(false);
        manager.setApplicationContext(applicationContext);
        manager.onApplicationEvent(mock(ContextRefreshedEvent.class));

        verifyNoInteractions(applicationContext);

        manager.onApplicationEvent(mock(ContextClosedEvent.class));

        verifyNoInteractions(applicationContext);
    }

}
