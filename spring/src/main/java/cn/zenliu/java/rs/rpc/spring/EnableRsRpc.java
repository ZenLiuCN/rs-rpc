package cn.zenliu.java.rs.rpc.spring;

import cn.zenliu.java.rs.rpc.api.Scope;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.EventListener;

import java.lang.annotation.*;

import static cn.zenliu.java.rs.rpc.spring.RpcProxyServiceRegister.propertyBuildScope;
import static cn.zenliu.java.rs.rpc.spring.RpcProxyServiceRegister.registerService;

/**
 * @author Zen.Liu
 * @apiNote
 * @since 2021-01-28
 */

@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@ImportAutoConfiguration({EnableRsRpc.RsRpcServiceAutoConfigure.class})
public @interface EnableRsRpc {
    @EnableConfigurationProperties(RpcProperties.class)
    @Slf4j
    class RsRpcServiceAutoConfigure {
        @Bean
        BeanDefinitionRegistryPostProcessor clientRegister(ApplicationContext ctx) {
            return new RpcProxyServiceRegister(ctx);
        }

        @Bean(destroyMethod = "release")
        @Lazy
        Scope defaultScope(@Autowired RpcProperties property) {
            return propertyBuildScope(property);
        }

        @EventListener
        public void onStartedRegisterServices(ApplicationStartedEvent event) {
            final ConfigurableApplicationContext ctx = event.getApplicationContext();
            final RsRpcServiceExpose services = ctx.getBean(RsRpcServiceExpose.class);
            final Scope scope = ctx.getBean("defaultScope", Scope.class);
            services.expose().forEach(s -> registerService(scope, s, ctx));
        }
    }
}
