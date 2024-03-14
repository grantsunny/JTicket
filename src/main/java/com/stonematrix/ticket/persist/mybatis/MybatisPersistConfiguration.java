package com.stonematrix.ticket.persist.mybatis;

import com.stonematrix.ticket.persist.PersistenceException;
import com.stonematrix.ticket.persist.VenuesRepository;
import jakarta.inject.Inject;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

@Configuration
public class MybatisPersistConfiguration {
    @Inject
    private VenuesMapper venuesMapper;
    @Bean
    public VenuesRepository getVenuesRepository() {
        return (VenuesRepository) Proxy.newProxyInstance(
                VenuesRepository.class.getClassLoader(),
                new Class<?>[]{VenuesRepository.class},
                new InvocationHandler() {
                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                        Method targetMethod = venuesMapper.getClass().getMethod(method.getName(), method.getParameterTypes());
                        try {
                            return targetMethod.invoke(venuesMapper, args);
                        } catch (InvocationTargetException e) {
                            Throwable targetException = e.getTargetException();
                            if (targetException instanceof org.apache.ibatis.exceptions.PersistenceException)
                                throw new PersistenceException(targetException);
                            else
                                throw targetException;
                        }
                    }
                });
    }
}
