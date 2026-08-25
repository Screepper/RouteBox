package com.routebox.util;

import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

/**
 * @Description Spring Bean Tool
 * @Date 2025/5/8
**/
@Component
public class SpringBeanUtil implements ApplicationContextAware {

    private static ApplicationContext context;

    @Override
    public void setApplicationContext(@NonNull ApplicationContext applicationContext) throws BeansException {
        context = applicationContext;
    }

    public static <T> T getBean(String beanName, Class<T> clazz) {
        return (context == null) ? null : context.getBean(beanName, clazz);
    }

    public static <T> T getBean(Class<T> clazz) {
        return (context == null) ? null : context.getBean(clazz);
    }
}