package com.jticket.persist.mybatis;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface ExecutorType {
    org.apache.ibatis.session.ExecutorType value() default org.apache.ibatis.session.ExecutorType.SIMPLE;
}