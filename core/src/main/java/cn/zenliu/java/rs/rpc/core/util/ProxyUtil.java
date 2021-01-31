package cn.zenliu.java.rs.rpc.core.util;

import cn.zenliu.java.rs.rpc.api.Result;
import cn.zenliu.java.rs.rpc.core.context.ContextScope;
import cn.zenliu.java.rs.rpc.core.context.ContextServices;
import lombok.SneakyThrows;
import org.jetbrains.annotations.Nullable;
import org.jooq.lambda.Seq;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;


public interface ProxyUtil {
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
        ContextScope ctx
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
                            ctx.routeingFNF(signature, args).subscribe();
                            return null;
                        } : args -> {
                            ctx.routeingFNF(signature, processor.apply(args)).subscribe();
                            return null;
                        }) : (processor == null ? args -> {
                            ctx.routeingRR(signature, args).block(ctx.getTimeout().get());
                            return null;
                        } : args -> {
                            ctx.routeingRR(signature, processor.apply(args)).block(ctx.getTimeout().get());
                            return null;
                        });
                    } else if (Result.class.isAssignableFrom(m.getReturnType())) {
                        handle = processor == null ? args -> ctx.routeingRR(signature, args).block(ctx.getTimeout().get())
                            : args -> ctx.routeingRR(signature, processor.apply(args)).block(ctx.getTimeout().get());
                    } else if (Mono.class.isAssignableFrom(m.getReturnType())) {
                        handle = processor == null ? args -> ctx.routeingRR(signature, args).flatMap(x -> {
                            if (x.hasError()) return Mono.error(x.getError());
                            else if (!x.hasResult()) return Mono.empty();
                            else return Mono.just(x.getResult());
                        })
                            : args -> ctx.routeingRR(signature, processor.apply(args)).flatMap(x -> {
                            if (x.hasError()) return Mono.error(x.getError());
                            else if (!x.hasResult()) return Mono.empty();
                            else return Mono.just(x.getResult());
                        });
                    } else if (Flux.class.isAssignableFrom(m.getReturnType())) {
                        handle = processor == null ? args -> Objects.requireNonNull(ctx.routeingRS(signature, args), "request response got null result")
                            : args -> Objects.requireNonNull(ctx.routeingRS(signature, processor.apply(args)), "request response got null result");
                    } else {
                        handle = processor == null ? args -> Objects.requireNonNull(ctx.routeingRR(signature, args).block(ctx.getTimeout().get()), "request response got null result").getOrThrow()
                            : args -> Objects.requireNonNull(ctx.routeingRR(signature, processor.apply(args)).block(ctx.getTimeout().get()), "request response got null result").getOrThrow();
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
        ContextServices ctx
    ) {
        return (service, serviceKlass, resultProcessor) -> {
            final String canonicalName = serviceKlass.getCanonicalName();
            if (!ctx.addService(serviceKlass, service)) {
                throw new IllegalStateException("service is already registered!" + canonicalName + ";");
            }
            for (Method method : serviceKlass.getMethods()) {
                Function<Object[], Result<Object>> invoker = null;
                Function<Object[], Flux<Object>> invokerFlux = null;
                if (resultProcessor != null && resultProcessor.containsKey(method.getName())) {
                    final Function<Object, Object> processor = resultProcessor.get(method.getName());
                    if (Result.class.isAssignableFrom(method.getReturnType())) {
                        invoker = args -> (Result<Object>) processor.apply(sneakyInvoker(service, method, args));
                    } else if (Flux.class.isAssignableFrom(method.getReturnType())) {
                        invokerFlux = args -> (Flux<Object>) processor.apply(sneakyInvoker(service, method, args));
                    } else if (Mono.class.isAssignableFrom(method.getReturnType())) {
                        invoker = args -> Result.wrap(() -> ((Mono<Object>) processor.apply(sneakyInvoker(service, method, args))).block(ctx.getTimeout().get()));
                    } else {
                        invoker = args -> Result.wrap(() -> processor.apply(sneakyInvoker(service, method, args)));
                    }
                } else {
                    if (Result.class.isAssignableFrom(method.getReturnType())) {
                        invoker = args -> (Result<Object>) sneakyInvoker(service, method, args);
                    } else if (Flux.class.isAssignableFrom(method.getReturnType())) {
                        invokerFlux = args -> (Flux<Object>) sneakyInvoker(service, method, args);
                    } else if (Mono.class.isAssignableFrom(method.getReturnType())) {
                        invoker = args -> Result.wrap(() -> ((Mono<Object>) sneakyInvoker(service, method, args)).block(ctx.getTimeout().get()));
                    } else {
                        invoker = args -> Result.wrap(() -> sneakyInvoker(service, method, args));
                    }
                }
                if (invoker != null) {
                    ctx.addHandler(signature(method, serviceKlass), invoker);
                } else {
                    ctx.addStreamHandler(signature(method, serviceKlass), invokerFlux);
                }
            }
        };

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
