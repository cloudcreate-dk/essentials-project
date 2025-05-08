package dk.trustworks.essentials.components.foundation.lifecycle;

import dk.trustworks.essentials.shared.Lifecycle;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;

import java.util.Map;
import java.util.function.Consumer;

import static org.mockito.Mockito.*;

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

        manager.start();
        verify(lifeCycleBean, times(0)).stop();

        when(lifeCycleBean.isStarted()).thenReturn(true);
        manager.stop();
    }

    @Test
    void lifeCycleBeanAlreadyStartedTest() {
        var applicationContext = mock(ApplicationContext.class);
        var lifecycleBean = mock(Lifecycle.class);
        when(lifecycleBean.isStarted()).thenReturn(true);
        when(applicationContext.getBeansOfType(Lifecycle.class)).thenReturn(Map.of(BEAN_NAME, lifecycleBean));

        var manager = new DefaultLifecycleManager(true);
        manager.setApplicationContext(applicationContext);

        manager.start();
        verify(lifecycleBean, times(0)).start();
    }

    @Test
    void onStartCustomConsumerTest() {
        var applicationContext = mock(ApplicationContext.class);
        when(applicationContext.getBeansOfType(Lifecycle.class)).thenReturn(Map.of());

        Consumer<ApplicationContext> consumer = mock(Consumer.class);
        var manager = new DefaultLifecycleManager(consumer, true);
        manager.setApplicationContext(applicationContext);

        manager.start();
        verify(consumer).accept(applicationContext);
    }

    @Test
    void dontStartLifeCycleBeansTest() {
        var applicationContext = mock(ApplicationContext.class);
        var manager = new DefaultLifecycleManager(false);
        manager.setApplicationContext(applicationContext);
        manager.start();

        verifyNoInteractions(applicationContext);

        manager.stop();

        verifyNoInteractions(applicationContext);
    }

}
