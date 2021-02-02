package cn.zenliu.java.rs.rpc.core.context;

import cn.zenliu.java.rs.rpc.api.Result;
import cn.zenliu.java.rs.rpc.core.element.*;
import cn.zenliu.java.rs.rpc.core.proto.RequestImpl;
import cn.zenliu.java.rs.rpc.core.proto.ResponseImpl;
import io.rsocket.Payload;
import org.jetbrains.annotations.NotNull;
import org.jooq.lambda.tuple.Tuple2;
import org.jooq.lambda.tuple.Tuple3;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Objects;
import java.util.function.Function;

import static cn.zenliu.java.rs.rpc.core.util.ProxyUtil.domainOf;

/**
 * Handle RSocket Methods
 *
 * @author Zen.Liu
 * @apiNote
 * @since 2021-01-23
 */
public interface ContextScope extends ContextCallback {
    String LOG_META = "\n REQUEST: {}\n SERVICE: {}";


    default void onServMeta(Tuple2<@NotNull RouteMeta, @NotNull Remote> in) {
        final Tuple3<Remote, @NotNull Remote, Boolean> r = in.map1(in.v2::updateRouteMeta).concat(in.v1.isKnown(getRoutes().get()));
        processRemoteUpdate(r.v1, r.v2, r.v3);

    }

    default Mono<Void> onFNF(@NotNull Request r, Remote remote) {
        onDebug("begin to process FireAndForget:" + LOG_META, r, remote);
        final Function<Object[], Result<Object>> handler = findHandler(r.getMeta().getAddress());
        if (handler != null) {
            onDebugWithTimer(
                x -> x.debug("process FireAndForget:" + LOG_META, r, remote)
                , null
                , x -> {
                    try {
                        handler.apply(r.getArguments(this.argumentPreProcessor(remote, r.getMeta(), r)));
                        if ((getDebug().get() || getTrace().get()) || r.getMeta().isTrace()) {
                            x.info("remote trace \n META: {} \n TIME COST: {}", r, r.getMeta().costNow());
                        }
                    } catch (Exception e) {
                        x.error("error to process FireAndForget:" + LOG_META, r, remote, e);
                    }
                });
            return Mono.empty();
        } else if (isRoute()) {
            final String domain = domainOf(r.getMeta().getAddress());
            final Remote service = findRemoteService(domain);
            if (service != null) {
                onDebugElse(x -> x.debug("routeing FireAndForget:" + LOG_META + "\n NEXT:{}", r, remote, service)
                    , x -> x.debug("routeing FireAndForget:" + LOG_META + "\n NEXT:{}", r, remote.getName(), service.getName()));
                return service.getRSocket().fireAndForget(r.addTrace(r.getMeta().isTrace() ? getName() : getNameOnTrace()).build());
            }
        }
        error("none registered FireAndForget:" + LOG_META, r, remote);
        return Mono.empty();
    }

    /**
     * method to handle a RR call from remote
     *
     * @param r      Request
     * @param remote remote
     * @return Mono result
     */
    default Mono<Payload> onRR(Request r, Remote remote) {

        onDebug("process RequestAndResponse:" + LOG_META, r, remote);
        //callback handle

        if (r.getMeta().isCallback()) {
            final String scopeName = r.getMeta().getAddress().substring(0, r.getMeta().getAddress().indexOf(CALLBACK_SCOPE));
            if (scopeName.equals(getName())) {
                return Mono.just(doCallback(r.getMeta(), r, remote));
            } else {
                final Remote target = findRemoteByName(scopeName);
                if (target == null) {
                    return Mono.just(ResponseImpl.build(r.getMeta(), getName(), Result.error(new IllegalStateException("remote has gone!"))));
                }
                return target.getRSocket().requestResponse(r.addTrace(r.getMeta().isTrace() ? getName() : getNameOnTrace()).build());
            }
        }


        final Function<Object[], Result<Object>> handler = findHandler(r.getMeta().getAddress());
        if (handler != null) {
            return onDebugWithTimerReturns(
                x -> x.debug("process RequestAndResponse:" + LOG_META, r, remote)
                , null
                , x -> {
                    try {
                        final Result<Object> res = handler.apply(r.getArguments(this.argumentPreProcessor(remote, r.getMeta(), r)));
                        return Mono.just(ResponseImpl.build(r.getMeta(), getName(), res != null ? res : Result.ok(null)));
                    } catch (Exception ex) {
                        x.error("error on process RequestAndResponse:" + LOG_META, r, remote, ex);
                        return Mono.just(ResponseImpl.build(r.getMeta(), getName(), Result.error(ex)));
                    }
                }
            );
        } else if (isRoute()) {
            final String domain = domainOf(r.getMeta().getAddress());
            final Remote service = findRemoteService(domain);
            if (service != null) {
                onDebugElse(x -> x.debug("routeing RequestAndResponse:" + LOG_META + "\n NEXT:{}", r, remote, service)
                    , x -> x.debug("routeing RequestAndResponse:" + LOG_META + "\n NEXT:{}", r, remote.getName(), service.getName()));
                try {
                    return service.getRSocket().requestResponse(r.addTrace(r.getMeta().isTrace() ? getName() : getNameOnTrace()).build());
                } catch (Exception ex) {
                    error(" on process routeing RequestAndResponse:" + LOG_META, r, remote, ex);
                    return Mono.just(ResponseImpl.build(r.getMeta(), getName(), Result.error(ex)));
                }
            }
        }
        error("none registered RequestAndResponse:" + LOG_META, r, remote);
        return Mono.just(ResponseImpl.build(r.getMeta(), getName(), Result.error(new IllegalStateException("no such method '" + r.getMeta().getAddress() + "' on " +
            getName() + ",I supports " + getRoutes().get() + " with routeing " + isRoute() + (isRoute() ? (" and with routes " + getDomains()) : "")))));
    }

    /**
     * handle rs request
     */
    default Flux<Payload> onRS(Request r, Remote remote) {
        onDebug("process RequestStream:" + LOG_META, r, remote);
        final Function<Object[], Flux<Object>> handler = findStreamHandler(r.getMeta().getAddress());
        if (handler != null) {
            return onDebugWithTimerReturns(
                x -> x.debug("process RequestStream:" + LOG_META, r, remote)
                , null
                , x -> {
                    try {
                        return handler.apply(r.getArguments(this.argumentPreProcessor(remote, r.getMeta(), r))).switchOnFirst((s, f) ->
                            //switch element process first one have a meta
                            s.hasValue() ?
                                Flux.just(ResponseImpl.buildFirstElement(r.getMeta(), r.getMeta().isTrace() ? getName() : getNameOnTrace(), s.get()))
                                    .concatWith(f.map(ResponseImpl::buildElement)) :
                                f.map(ResponseImpl::buildElement));
                    } catch (Exception ex) {
                        x.error("error on process RequestStream:" + LOG_META, r, remote, ex);
                        return Flux.error(ex);
                    }
                }
            );
        } else if (isRoute()) {
            final String domain = domainOf(r.getMeta().getAddress());
            final Remote service = findRemoteService(domain);
            if (service != null) {
                onDebugElse(x -> x.debug("routeing RequestStream:" + LOG_META + "\n NEXT:{}", r, remote, service)
                    , x -> x.debug("routeing RequestStream:" + LOG_META + "\n NEXT:{}", r, remote.getName(), service.getName()));
                try {
                    return service.getRSocket().requestStream(r.addTrace(r.getMeta().isTrace() ? getName() : getNameOnTrace()).build());
                } catch (Exception ex) {
                    error(" on process routeing RequestStream:" + LOG_META, r, remote, ex);
                    return Flux.error(ex);
                }
            }
        }
        error("none registered RequestStream:" + LOG_META, r, remote);
        return Flux.error(new IllegalStateException("no such method '" + r.getMeta().getAddress() + "' on " +
            getName() + ",I supports " + getRoutes().get() + " with routeing " + isRoute() + (isRoute() ? (" and with routes " + getDomains()) : "")));
    }

    /**
     * a method to fire a RR request to remote
     *
     * @param sign handler signature
     * @param args args
     * @return Result
     */
    default Mono<Result<Object>> routeingRR(String sign, Object[] args) {
        final String domain = domainOf(sign);
        final Remote remote = findRemoteService(domain);
        if (remote == null) {
            error("not found remote service of {}[{}]  with routes {}", sign, domain, getDomains());
            throw new IllegalStateException("not exists service for '" + sign + "' in " + getName());
        }
        return onDebugWithTimerReturns(
            x -> x.debug("remote call \n DOMAIN: {} \n ARGUMENTS: {} .", sign, args)
            , null
            , x -> remote.getRSocket().requestResponse(RequestImpl.build(sign, getName(), args, this::argumentPostProcessor, getTrace().get()))
                .map(result -> {
                    final Response response = ResponseImpl.of(result);
                    if ((getDebug().get() || getTrace().get()) && result != null) {
                        final Meta meta = response.getMeta();
                        x.info("remote trace \n ARGUMENTS: {} \n META: {} \n  COST: SEND {} ,TOTAL {}", args, meta, meta.cost(), meta.costNow());
                    }
                    if (result == null) {
                        x.error("error to request,got null result. \n DOMAIN:: {} \n ARGUMENTS: {} \n SERVICE {}", sign, args, remote);
                        return Result.error(new IllegalAccessError("error to call remote service " + remote.getName() + " from " + getName()));
                    }
                    x.debug("remote call \n DOMAIN: {} \n ARGUMENTS: {} \n RESULT: {} \n SERVICE:{}", sign, args, response, remote);
                    return Objects.requireNonNull(response.getResponse(), "RPC response is null !");
                })
        );
    }

    /**
     * a method to fire a RS request to remote
     *
     * @param sign handler signature
     * @param args args
     * @return Result
     */
    default Flux<Object> routeingRS(String sign, Object[] args) {
        final String domain = domainOf(sign);
        final Remote remote = findRemoteService(domain);
        if (remote == null) {
            error("not found remote service of {}[{}]  with routes {}", sign, domain, getDomains());
            throw new IllegalStateException("not exists service for '" + sign + "' in " + getName());
        }
        return onDebugWithTimerReturns(
            x -> x.debug("remote call \n DOMAIN: {} \n ARGUMENTS: {} .", sign, args)
            , null
            , x -> remote.getRSocket().requestStream(RequestImpl.build(sign, getName(), args, this::argumentPostProcessor, getTrace().get()))
                .switchOnFirst((signal, flux) -> {
                    if (signal.hasValue()) {
                        final Payload result = signal.get();
                        final Response res = ResponseImpl.of(result);
                        if ((getDebug().get() || getTrace().get()) && result != null) {
                            final Meta meta = res.getMeta();
                            x.info("remote trace \n ARGUMENTS: {} \n META: {} \n  COST: SEND {} ,TOTAL {}", args, meta, meta.cost(), meta.costNow());
                        }
                        assert result != null;
                        return Flux.just(Objects.requireNonNull(res.getElement())).concatWith(flux.map(ResponseImpl::parseElement));
                    } else return flux.map(ResponseImpl::parseElement);
                })
        );
    }

    /**
     * a method to fire a FNF Request to Remote
     *
     * @param sign handler signature
     * @param args args
     */
    default Mono<Void> routeingFNF(String sign, Object[] args) {
        final String domain = domainOf(sign);
        final Remote remote = findRemoteService(domain);
        if (null == remote) {
            throw new IllegalStateException("not exists service for " + sign);
        }
        debug("do FNF with {} ,{} =>{}", sign, args, remote);
        return remote.getRSocket().fireAndForget(RequestImpl.build(sign, getName(), args, this::argumentPostProcessor, getTrace().get()));
    }


}
