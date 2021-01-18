package cn.zenliu.java.rs.rpc.core;

import cn.zenliu.java.rs.rpc.api.Result;
import lombok.SneakyThrows;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;


interface ProxyUtil {


    Object[] DEFAULT = new Object[0];

    static ClientCreator clientCreatorBuilder(
        String scopeName,
        doAndForget fire,
        doForResponse request
    ) {

        return (clientKlass, argumentProcessor) -> {
            final String canonicalName = clientKlass.getCanonicalName();
            final Map<String, Function<Object[], Object>> cache = new ConcurrentHashMap<>();
            return Proxy.newProxyInstance(clientKlass.getClassLoader(), new Class[]{clientKlass}, (p, m, a) -> {
                if (m.getName().equals("toString") && m.getParameterCount() == 0) {
                    return canonicalName + "$Proxy$" + scopeName;
                }
                final String idx = m.getName() + "<" + m.getParameterCount() + ">";
                final Function<Object[], Object> handle;
                if (cache.containsKey(idx)) {
                    handle = cache.get(idx);
                } else {
                    final String domain = canonicalName + "#" + m.getName() + "<" + m.getParameterCount() + ">";
                    final Function<Object[], Object[]> processor = argumentProcessor == null ? null : argumentProcessor.get(m.getName());
                    if (m.getReturnType() == Void.TYPE) {
                        handle = processor == null ? args -> {
                            fire.fire(domain, args);
                            return null;
                        } : args -> {
                            fire.fire(domain, processor.apply(args));
                            return null;
                        };
                    } else if (Result.class.isAssignableFrom(m.getReturnType())) {
                        handle = processor == null ? args -> request.request(domain, args)
                            : args -> request.request(domain, processor.apply(args));

                    } else {
                        handle = processor == null ? args -> request.request(domain, args).getOrThrow()
                            : args -> request.request(domain, processor.apply(args)).getOrThrow();
                    }
                    cache.put(idx, handle);
                }
                return handle.apply(a == null ? DEFAULT : a);
            });
        };
    }

    @SneakyThrows
    static Object sneakyInvoker(Object instance, Method method, Object[] args) {
        return method.invoke(instance, args);
    }

    @SuppressWarnings("unchecked")
    static ServiceRegister serviceRegisterBuilder(
        Set<String> services,
        Map<String, Function<Object[], Result<Object>>> handler
    ) {
        return (service, serviceKlass, resultProcessor) -> {
            final String canonicalName = serviceKlass.getCanonicalName();
            if (!services.add(canonicalName)) {
                throw new IllegalStateException("service is already registered!" + canonicalName + ";all " + services);
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
                handler.put(canonicalName + "#" + method.getName() + "<" + method.getParameterCount() + ">", invoker);
            }
        };

    }


    @FunctionalInterface
    interface doAndForget {
        void fire(String domain, Object[] args);
    }

    @FunctionalInterface
    interface doForResponse {
        Result<Object> request(String domain, Object[] args);
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
            @Nullable Map<String, Function<Object[], Object[]>> argumentProcessor);
    }
}
