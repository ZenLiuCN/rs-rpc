package cn.zenliu.java.rs.rpc.core;

import cn.zenliu.java.rs.rpc.api.Result;
import lombok.SneakyThrows;
import org.jetbrains.annotations.Nullable;
import org.jooq.lambda.Seq;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;


interface ProxyUtil {
    char DOMAIN_SPLITTER = '#';
    char PARAMETER_DOMAIN_SPLITTER = '<';
    char PARAMETER_SPLITTER = ',';

    static String handlerSignature(Method method, Class<?> service) {
        return service.getCanonicalName() + DOMAIN_SPLITTER + method.getName() + PARAMETER_DOMAIN_SPLITTER +
            Seq.of(method.getParameterTypes()).map(Class::getSimpleName).toString(PARAMETER_SPLITTER + "");
    }

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
                    final String signature = handlerSignature(m, clientKlass);
                    final Function<Object[], Object[]> processor = argumentProcessor == null ? null : argumentProcessor.get(m.getName());
                    if (m.getReturnType() == Void.TYPE) {
                        handle = processor == null ? args -> {
                            fire.fnf(signature, args);
                            return null;
                        } : args -> {
                            fire.fnf(signature, processor.apply(args));
                            return null;
                        };
                    } else if (Result.class.isAssignableFrom(m.getReturnType())) {
                        handle = processor == null ? args -> request.rr(signature, args)
                            : args -> request.rr(signature, processor.apply(args));

                    } else {
                        handle = processor == null ? args -> request.rr(signature, args).getOrThrow()
                            : args -> request.rr(signature, processor.apply(args)).getOrThrow();
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
        Predicate<String> serviceAdder,
        BiConsumer<String, Function<Object[], Result<Object>>> handlerAdder
    ) {
        return (service, serviceKlass, resultProcessor) -> {
            final String canonicalName = serviceKlass.getCanonicalName();
            if (!serviceAdder.test(canonicalName)) {
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
                handlerAdder.accept(handlerSignature(method, serviceKlass), invoker);
            }
        };

    }


    @FunctionalInterface
    interface doAndForget {
        void fnf(String domain, Object[] args);
    }

    @FunctionalInterface
    interface doForResponse {
        Result<Object> rr(String domain, Object[] args);
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
