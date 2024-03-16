package com.stonematrix.ticket.persist.mybatis;

import com.stonematrix.ticket.persist.EventsRepository;
import com.stonematrix.ticket.persist.PersistenceException;
import com.stonematrix.ticket.persist.SeatsRepository;
import com.stonematrix.ticket.persist.VenuesRepository;
import jakarta.inject.Inject;
import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

@Configuration
public class MybatisPersistConfiguration {
    @Inject
    private VenuesMapper venuesMapper;
    @Inject
    private SeatsMapper seatsMapper;
    @Inject
    private EventsMapper eventsMapper;
    @Inject
    private SqlSessionFactory sqlSessionFactory;

    @Bean
    public VenuesRepository getVenuesRepository() {
        return (VenuesRepository) Proxy.newProxyInstance(
                VenuesRepository.class.getClassLoader(),
                new Class<?>[]{VenuesRepository.class},
                new MybatisInvocationHandler(venuesMapper, sqlSessionFactory));
    }

    @Bean
    public EventsRepository getEventsRepository() {
        return (EventsRepository) Proxy.newProxyInstance(
                EventsRepository.class.getClassLoader(),
                new Class<?>[]{EventsRepository.class},
                new MybatisInvocationHandler(eventsMapper, sqlSessionFactory));
    }

    @Bean
    public SeatsRepository getSeatsRepository() {
        return (SeatsRepository) Proxy.newProxyInstance(
                SeatsRepository.class.getClassLoader(),
                new Class<?>[]{SeatsRepository.class},
                new MybatisInvocationHandler(seatsMapper, sqlSessionFactory));
    }

    private static class MybatisInvocationHandler implements InvocationHandler {
        private final Object targetObject;
        private final SqlSessionFactory sessionFactory;

        public MybatisInvocationHandler(Object targetObject, SqlSessionFactory sessionFactory) {
            this.targetObject = targetObject;
            this.sessionFactory = sessionFactory;
        }

        private Object invokeWithTxn(Object object, Method method, Object[] args, ExecutorType executorType, Set<Class<? extends Throwable>> rollbackClass) throws Throwable {
            try (SqlSession sqlsession = sessionFactory.openSession(executorType)) {
                try {
                    Object rc = method.invoke(object, args);
                    sqlsession.commit();
                    return rc;
                } catch (InvocationTargetException e) {
                    Throwable targetException = e.getTargetException();
                    for (Class<? extends Throwable> rollbackClazz: rollbackClass) {
                        if (rollbackClazz.isAssignableFrom(targetException.getClass()))
                            sqlsession.rollback();
                    }

                    if (targetException instanceof org.apache.ibatis.exceptions.PersistenceException)
                        throw new PersistenceException(targetException);
                    else
                        throw targetException;
                }
            }
         }

        @Override
        public Object invoke(Object object, Method method, Object[] args) throws Throwable {

            Set<Class<? extends Throwable>> rollbackClass = new HashSet<>();
            rollbackClass.add(SQLException.class);
            rollbackClass.add(org.apache.ibatis.exceptions.PersistenceException.class);

            Method targetMethod = targetObject.getClass().getMethod(method.getName(), method.getParameterTypes());
            if (targetMethod.getAnnotation(Transactional.class) != null) {
                rollbackClass.addAll(Arrays.asList(method.getAnnotation(Transactional.class).rollbackFor()));
                if (targetMethod.getAnnotation(com.stonematrix.ticket.persist.mybatis.ExecutorType.class) != null)
                    return invokeWithTxn(
                            targetObject, targetMethod, args,
                            targetMethod.getAnnotation(com.stonematrix.ticket.persist.mybatis.ExecutorType.class).value(),
                            rollbackClass);
                else
                    return invokeWithTxn(
                            targetObject, targetMethod, args,
                            ExecutorType.SIMPLE,
                            rollbackClass);
            } else {
                try {
                    return targetMethod.invoke(targetObject, args);
                } catch (InvocationTargetException e) {
                    Throwable targetException = e.getTargetException();
                    if (targetException instanceof org.apache.ibatis.exceptions.PersistenceException)
                        throw new PersistenceException(targetException);
                    else
                        throw targetException;
                }
            }
        }
    }
}
