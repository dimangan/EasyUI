package ru.dimangan.easy_ui.events;

import lombok.NonNull;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
public class EventPublisher implements ApplicationContextAware {
    private static ApplicationEventPublisher eventPublisher;

    @Override
    public void setApplicationContext(@NonNull ApplicationContext applicationContext) throws BeansException {
        eventPublisher = applicationContext;
    }

    public static void publishEvent(ApplicationEvent event){
        eventPublisher.publishEvent(event);
    }
}
