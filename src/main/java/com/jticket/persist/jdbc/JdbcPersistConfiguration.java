package com.jticket.persist.jdbc;

import com.jticket.persist.*;
import jakarta.inject.Inject;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.SQLException;

@Configuration
@Profile("jdbc")
public class JdbcPersistConfiguration {

    @Inject
    private JdbcHelper jdbcHelper;

    @Bean
    EventsRepository getEventsRepository() {
        return (EventsRepository) Proxy.newProxyInstance(
                EventsRepository.class.getClassLoader(),
                new Class<?>[]{EventsRepository.class},
                new JdbcHelperInvocationHandler(jdbcHelper));
    }

    @Bean
    SeatsRepository getSeatsRepository() {
        return (SeatsRepository) Proxy.newProxyInstance(
                SeatsRepository.class.getClassLoader(),
                new Class<?>[]{SeatsRepository.class},
                new JdbcHelperInvocationHandler(jdbcHelper));
    }

    @Bean
    VenuesRepository getVenuesRepository() {
        return (VenuesRepository) Proxy.newProxyInstance(
                VenuesRepository.class.getClassLoader(),
                new Class<?>[]{VenuesRepository.class},
                new JdbcHelperInvocationHandler(jdbcHelper));
    }

    @Bean
    OrdersRepository getOrdersRepository() {
        return (OrdersRepository) Proxy.newProxyInstance(
                OrdersRepository.class.getClassLoader(),
                new Class<?>[]{OrdersRepository.class},
                new JdbcHelperInvocationHandler(jdbcHelper));
    }

    private static class JdbcHelperInvocationHandler implements InvocationHandler {
        private final JdbcHelper target;

        public JdbcHelperInvocationHandler(JdbcHelper target) {
            this.target = target;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            Method jdbcMethod = JdbcHelper.class.getMethod(method.getName(), method.getParameterTypes());
            try {
                return jdbcMethod.invoke(target, args);
            } catch (InvocationTargetException e) {
                Throwable targetException = e.getTargetException();
                if (targetException instanceof SQLException) {
                    throw new PersistenceException(targetException);
                }
                throw targetException;
            }
        }
    }
}
