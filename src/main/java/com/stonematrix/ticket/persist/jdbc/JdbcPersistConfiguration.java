package com.stonematrix.ticket.persist.jdbc;

import com.stonematrix.ticket.persist.*;
import jakarta.inject.Inject;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.DefaultTransactionDefinition;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.lang.reflect.InvocationTargetException;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

@Configuration
@Profile("production")
public class JdbcPersistConfiguration {

    @Inject
    private JdbcHelper jdbcHelper;

    @Inject
    private PlatformTransactionManager transactionManager;

    @Bean
    public EventsRepository getEventsRepository() {
        return (EventsRepository) Proxy.newProxyInstance(
                EventsRepository.class.getClassLoader(),
                new Class<?>[]{EventsRepository.class},
                new JdbcHelperInvocationHandler(transactionManager, jdbcHelper));
    }

    @Bean
    public SeatsRepository getSeatsRepository() {
        return (SeatsRepository) Proxy.newProxyInstance(
                SeatsRepository.class.getClassLoader(),
                new Class<?>[]{SeatsRepository.class},
                new JdbcHelperInvocationHandler(transactionManager, jdbcHelper));
    }

    @Bean
    public VenuesRepository getVenuesRepository() {
        return (VenuesRepository) Proxy.newProxyInstance(
                VenuesRepository.class.getClassLoader(),
                new Class<?>[]{VenuesRepository.class},
                new JdbcHelperInvocationHandler(transactionManager, jdbcHelper));
    }

    @Bean
    public OrdersRepository getOrdersRepository() {
        return (OrdersRepository) Proxy.newProxyInstance(
                OrdersRepository.class.getClassLoader(),
                new Class<?>[]{OrdersRepository.class},
                new JdbcHelperInvocationHandler(transactionManager, jdbcHelper));
    }

    private static class JdbcHelperInvocationHandler implements InvocationHandler {
        private final JdbcHelper target;
        private final PlatformTransactionManager transactionManager;
        public JdbcHelperInvocationHandler(PlatformTransactionManager transactionManager, JdbcHelper target) {
            this.target = target;
            this.transactionManager = transactionManager;
        }
        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            boolean isTransactional = false;
            Set<Class<? extends Throwable>> rollbackClass = new HashSet<>();
            rollbackClass.add(SQLException.class);

            if (method.getAnnotation(Transactional.class) != null) {
                isTransactional = true;
                rollbackClass.addAll(Arrays.asList(method.getAnnotation(Transactional.class).rollbackFor()));
            }

            // Attempt to call the method on the target object
            Method jdbcMethod = JdbcHelper.class.getMethod(method.getName(), method.getParameterTypes());
            if (isTransactional) {
                TransactionStatus status = transactionManager.getTransaction(new DefaultTransactionDefinition());
                try {
                    Object rc = jdbcMethod.invoke(target, args);
                    transactionManager.commit(status);
                    return rc;
                } catch (InvocationTargetException e) {
                    Throwable targetException = e.getTargetException();
                    if (rollbackClass.contains(targetException.getClass())) {
                        transactionManager.rollback(status);
                        throw new PersistenceException(targetException);
                    } else
                        throw targetException;
                }
            } else {
                try {
                    return jdbcMethod.invoke(target, args);
                } catch (InvocationTargetException e) {
                    Throwable targetException = e.getTargetException();
                    if (rollbackClass.contains(targetException.getClass())) {
                        throw new PersistenceException(targetException);
                    } else
                        throw targetException;
                }
            }
        }
    }
}
