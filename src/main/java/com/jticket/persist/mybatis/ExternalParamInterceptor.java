package com.jticket.persist.mybatis;

import java.util.HashMap;
import java.util.Map;

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
        if (paramsRaw instanceof Map<?, ?> params) {
            Map<String, Object> sqlParameters = new HashMap<>();
            for (var entry : params.entrySet()) {
                if (!(entry.getKey() instanceof String key))
                    throw new IllegalArgumentException("MyBatis parameter names must be strings");
                sqlParameters.put(key, entry.getValue());
            }
            if (sqlParams != null)
                sqlParameters.putAll(sqlParams);
            invocation.getArgs()[1] = sqlParameters;
        }

        return invocation.proceed();
    }
}
