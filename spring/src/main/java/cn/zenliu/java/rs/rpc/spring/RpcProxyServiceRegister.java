package cn.zenliu.java.rs.rpc.spring;

import cn.zenliu.java.rs.rpc.api.Config;
import cn.zenliu.java.rs.rpc.api.Scope;
import cn.zenliu.java.rs.rpc.core.Rpc;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.context.ApplicationContext;
import org.springframework.core.Ordered;
import org.springframework.core.PriorityOrdered;

import java.util.function.Supplier;

/**
 * @author Zen.Liu
 * @apiNote
 * @since 2021-01-28
 */
@Slf4j
public class RpcProxyServiceRegister implements BeanDefinitionRegistryPostProcessor, PriorityOrdered {
    final Supplier<Scope> scopeGetter;
    final Supplier<RsRpcServiceBeanNameProcessor> beanNameProcessorSupplier;
    final Supplier<RsRpcServiceImport> importSupplier;

    public RpcProxyServiceRegister(ApplicationContext ctx) {
        scopeGetter = () -> ctx.getBean(Scope.class);
        beanNameProcessorSupplier = () -> ctx.getBean(RsRpcServiceBeanNameProcessor.class);
        importSupplier = () -> ctx.getBean(RsRpcServiceImport.class);

    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }


    @SuppressWarnings("unchecked")
    @Override
    public void postProcessBeanDefinitionRegistry(@NotNull BeanDefinitionRegistry registry) throws BeansException {
        final RsRpcServiceBeanNameProcessor processor = beanNameProcessorSupplier.get();
        for (Class<?> m : importSupplier.get().imported()) {
            registry.registerBeanDefinition(processor.decide(m),
                new RootBeanDefinition(
                    (Class<Object>) m,
                    () -> registerClientService(scopeGetter.get(), m)));
        }
    }

    @Override
    public void postProcessBeanFactory(@NotNull ConfigurableListableBeanFactory beanFactory) throws BeansException {

    }

    public static <T> T registerClientService(Scope scope, Class<T> service) {
        log.info("RPC[{}] will register rpc proxy service {} into Context", scope.getName(), service);
        return scope.createClientService(service, null, false);
    }

    public static <T> void registerService(Scope scope, Class<T> service, ListableBeanFactory ctx) {
        log.info("RPC[{}] will register local service {} into rpc", scope.getName(), service);
        scope.registerService(ctx.getBean(service), service, null);
    }

    /**
     * simple build Scope with a Server or Client from properties
     *
     * @param properties properties
     * @return a Scope
     */
    public static Scope propertyBuildScope(RpcProperties properties) {
        Rpc.setAutoDelegate(properties.isDelegate());
        Rpc.setDelegateMode(properties.isFluent());
        final Scope scope;
        if (properties.isNewScope()) {
            scope = Rpc.newScope(properties.getName() + "Scope", properties.isRouteing());
        } else {
            scope = Rpc.Global;
        }
        scope.setDebug(properties.isDebug());
        scope.setTrace(properties.isTrace());
        if (properties.isClient()) {
            scope.startClient(properties.getName() + "Client", Config.Client.builder().host(properties.getHost()).port(properties.getPort()).build());
        } else {
            scope.startServer(properties.getName() + "Server", Config.Server.builder().port(properties.getPort()).build());
        }
        return scope;
    }
}
