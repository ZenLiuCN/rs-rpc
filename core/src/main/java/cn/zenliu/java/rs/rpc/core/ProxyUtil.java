package cn.zenliu.java.rs.rpc.core;

import cn.zenliu.java.rs.rpc.api.Result;
import lombok.SneakyThrows;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jooq.lambda.Seq;
import reactor.core.publisher.Mono;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Function;


interface ProxyUtil {
    char DOMAIN_SPLITTER = '#';
    char PARAMETER_DOMAIN_SPLITTER = '<';
    char PARAMETER_SPLITTER = ',';

    static String signature(Method method, Class<?> service) {
        return service.getCanonicalName() + DOMAIN_SPLITTER + method.getName() + PARAMETER_DOMAIN_SPLITTER +
            Seq.of(method.getParameterTypes()).map(Class::getSimpleName).toString(PARAMETER_SPLITTER + "");
    }

    static String domainOf(String sign) {
        return sign.substring(0, sign.indexOf(ProxyUtil.DOMAIN_SPLITTER));
    }

    Object[] DEFAULT = new Object[0];

    static ClientCreator clientCreatorBuilder(
        ContextServices ctx,
        doAndForget fire,
        doForResponse request
    ) {
        return (clientKlass, argumentProcessor, useFNF) -> {
            Object instance = ctx.validateProxy(clientKlass);
            if (instance != null) return instance;
            final String canonicalName = clientKlass.getCanonicalName();
            final Map<String, Function<Object[], Object>> cache = new ConcurrentHashMap<>();
            instance = Proxy.newProxyInstance(clientKlass.getClassLoader(), new Class[]{clientKlass}, (p, m, a) -> {
                if (m.getName().equals("toString") && m.getParameterCount() == 0) {
                    return canonicalName + "$Proxy$" + ctx.getName();
                }
                final String idx = m.getName() + "<" + m.getParameterCount() + ">";
                final Function<Object[], Object> handle;
                if (cache.containsKey(idx)) {
                    handle = cache.get(idx);
                } else {
                    final String signature = signature(m, clientKlass);
                    final Function<Object[], Object[]> processor = argumentProcessor == null ? null : argumentProcessor.get(m.getName());
                    if (m.getReturnType() == Void.TYPE) {
                        handle = useFNF
                            ? (processor == null ? args -> {
                            fire.fnf(signature, args).subscribe();
                            return null;
                        } : args -> {
                            fire.fnf(signature, processor.apply(args)).subscribe();
                            return null;
                        }) : (processor == null ? args -> {
                            request.rr(signature, args).block(ctx.getTimeout().get());
                            return null;
                        } : args -> {
                            request.rr(signature, processor.apply(args)).block(ctx.getTimeout().get());
                            return null;
                        });
                    } else if (Result.class.isAssignableFrom(m.getReturnType())) {
                        handle = processor == null ? args -> request.rr(signature, args).block(ctx.getTimeout().get())
                            : args -> request.rr(signature, processor.apply(args)).block(ctx.getTimeout().get());
                    } else if (Mono.class.isAssignableFrom(m.getReturnType())) {
                        handle = processor == null ? args -> request.rr(signature, args)
                            : args -> request.rr(signature, processor.apply(args));
                    } else {
                        handle = processor == null ? args -> request.rr(signature, args).block(ctx.getTimeout().get()).getOrThrow()
                            : args -> request.rr(signature, processor.apply(args)).block(ctx.getTimeout().get()).getOrThrow();
                    }
                    cache.put(idx, handle);
                }
                return handle.apply(a == null ? DEFAULT : a);
            });
            ctx.addProxy(clientKlass, instance);
            return instance;
        };
    }

    @SneakyThrows
    static Object sneakyInvoker(Object instance, Method method, Object[] args) {
        return method.invoke(instance, args);
    }

    @SuppressWarnings("unchecked")
    static ServiceRegister serviceRegisterBuilder(
        ContextServices ctx,
        BiConsumer<String, Function<Object[], Result<Object>>> handlerAdder
    ) {
        return (service, serviceKlass, resultProcessor) -> {
            final String canonicalName = serviceKlass.getCanonicalName();
            if (!ctx.addService(serviceKlass, service)) {
                throw new IllegalStateException("service is already registered!" + canonicalName + ";");
            }
            for (Method method : serviceKlass.getMethods()) {
                final Function<Object[], Result<Object>> invoker;
                if (resultProcessor != null && resultProcessor.containsKey(method.getName())) {
                    final Function<Object, Object> processor = resultProcessor.get(method.getName());
                    if (Result.class.isAssignableFrom(method.getReturnType())) {
                        invoker = args -> (Result<Object>) processor.apply(sneakyInvoker(service, method, args));
                    } else {
                        invoker = args -> Result.wrap(() -> processor.apply(sneakyInvoker(service, method, args)));
                    }
                } else {
                    if (Result.class.isAssignableFrom(method.getReturnType())) {
                        invoker = args -> (Result<Object>) sneakyInvoker(service, method, args);
                    } else {
                        invoker = args -> Result.wrap(() -> sneakyInvoker(service, method, args));
                    }
                }
                handlerAdder.accept(signature(method, serviceKlass), invoker);
            }
        };

    }


    @FunctionalInterface
    interface doAndForget {
        Mono<Void> fnf(String domain, Object[] args);
    }

    @FunctionalInterface
    interface doForResponse {
        Mono<@NotNull Result<Object>> rr(String domain, Object[] args);
    }


    @FunctionalInterface
    interface ServiceRegister {
        void register(
            Object service,
            Class<?> serviceKlass,
            @Nullable Map<String, Function<Object, Object>> resultProcessor
        );
    }

    @FunctionalInterface
    interface ClientCreator {
        Object create(
            Class<?> clientKlass,
            @Nullable Map<String, Function<Object[], Object[]>> argumentProcessor,
            boolean useFNF
        );
    }
}
