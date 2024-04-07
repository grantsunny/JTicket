package com.jticket.persist.mybatis;

import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * This is to workaround (or enhance) the situation that Ignite does not support view.
 * So we are using the way of embedded table to simulate this
 */
@Component
@ConfigurationProperties("ticket")
@Intercepts({
        @Signature(type = Executor.class, method = "query", args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class})
})
public class ExternalParamInterceptor implements Interceptor {

    private Map<String, String> sqlParams;

    public void setSqlParams(Map<String, String> setSqlParams) {
        this.sqlParams = setSqlParams;
    }

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        Object paramsRaw = invocation.getArgs()[1];
        if (paramsRaw != null)
            if (paramsRaw instanceof Map) {
                Map<String, Object> params = (Map<String, Object>) paramsRaw;
                params.putAll(sqlParams);
            }

        return invocation.proceed();
    }
}
