package cn.zenliu.java.rs.rpc.core.context;

import cn.zenliu.java.rs.rpc.api.Result;
import cn.zenliu.java.rs.rpc.core.element.Remote;
import cn.zenliu.java.rs.rpc.core.proto.Meta;
import cn.zenliu.java.rs.rpc.core.proto.Request;
import cn.zenliu.java.rs.rpc.core.proto.Response;
import cn.zenliu.java.rs.rpc.core.proto.ServMeta;
import io.rsocket.Payload;
import org.jetbrains.annotations.NotNull;
import org.jooq.lambda.tuple.Tuple2;
import org.jooq.lambda.tuple.Tuple3;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.function.Function;

import static cn.zenliu.java.rs.rpc.core.util.FunctorPayload.mustRequest;
import static cn.zenliu.java.rs.rpc.core.util.ProxyUtil.domainOf;

/**
 * Handle RSocket Methods
 *
 * @author Zen.Liu
 * @apiNote
 * @since 2021-01-23
 */
public interface ContextScope extends ContextCallback {
    String LOG_META = "\n META: {}\n SERVICE: {}";
    String LOG_META_REQUEST = "\n META:{}\n REQUEST: {}\n SERVICE: {}";


    default void onServMeta(Tuple2<@NotNull ServMeta, @NotNull Remote> in) {
        final Tuple3<Remote, @NotNull Remote, Boolean> r = in.map1(in.v2::updateFromMeta).concat(in.v1.isKnown(getRoutes().get()));
        processRemoteUpdate(r.v1, r.v2, r.v3);

    }

    default Mono<Void> onFNF(@NotNull Tuple2<Meta, Payload> in, Remote remote) {
        final Meta meta = in.v1;
        onDebug("begin to process FireAndForget:" + LOG_META, meta, remote);
        final Function<Object[], Result<Object>> handler = findHandler(meta.getSign());
        if (handler != null) {
            final Request request = mustRequest(in.v2);
            onDebugWithTimer(
                x -> x.debug("process FireAndForget:" + LOG_META_REQUEST, meta, request, remote)
                , null
                , x -> {
                    try {
                        handler.apply(request.getArguments(this.argumentPreProcessor(remote, meta, request)));
                        if ((getDebug().get() || getTrace().get()) || meta.isTrace()) {
                            x.info("remote trace \n META: {} \n TIME COST: {}", meta, meta.costNow());
                        }
                    } catch (Exception e) {
                        x.error("error to process FireAndForget:" + LOG_META_REQUEST, meta, request, remote, e);
                    }
                });
            return Mono.empty();
        } else if (isRoute()) {
            final String domain = domainOf(meta.getSign());
            final Remote service = findRemoteService(domain);
            if (service != null) {
                onDebugElse(x -> x.debug("routeing FireAndForget:" + LOG_META + "\n NEXT:{}", meta, remote, service)
                    , x -> x.debug("routeing FireAndForget:" + LOG_META + "\n NEXT:{}", meta, remote.getName(), service.getName()));
                return service.getSocket().fireAndForget(Request.updateMeta(in.v2, meta, meta.isTrace() ? getName() : getNameOnTrace()));
            }
        }
        error("none registered FireAndForget:" + LOG_META, meta, remote);
        return Mono.empty();
    }

    /**
     * method to handle a RR call from remote
     *
     * @param in     input
     * @param remote remote
     * @return Mono result
     */
    default Mono<Payload> onRR(Tuple2<Meta, Payload> in, Remote remote) {
        final Meta meta = in.v1;
        final Payload p = in.v2;
        onDebug("process RequestAndResponse:" + LOG_META, meta, remote);
        //callback handle

        if (meta.isCallback()) {
            final String scopeName = meta.getSign().substring(0, meta.getSign().indexOf(CALLBACK_SCOPE));
            if (scopeName.equals(getName())) {
                final Request request = mustRequest(p);
                return Mono.just(doCallback(meta, request, remote));
            } else {
                final Remote target = findRemoteByName(scopeName);
                if (target == null) {
                    return Mono.just(Response.build(meta, getName(), Result.error(new IllegalStateException("remote has gone!"))));
                }
                return target.getSocket().requestResponse(Request.updateMeta(p, meta, meta.isTrace() ? getName() : getNameOnTrace()));
            }
        }


        final Function<Object[], Result<Object>> handler = findHandler(meta.getSign());
        if (handler != null) {
            final Request request = mustRequest(p);
            return onDebugWithTimerReturns(
                x -> x.debug("process RequestAndResponse:" + LOG_META_REQUEST, meta, request, remote)
                , null
                , x -> {
                    try {
                        final Result<Object> res = handler.apply(request.getArguments(this.argumentPreProcessor(remote, meta, request)));
                        return Mono.just(Response.build(meta, getName(), res != null ? res : Result.ok(null)));
                    } catch (Exception ex) {
                        x.error("error on process RequestAndResponse:" + LOG_META_REQUEST, meta, request, remote, ex);
                        return Mono.just(Response.build(meta, getName(), Result.error(ex)));
                    }
                }
            );
        } else if (isRoute()) {
            final String domain = domainOf(meta.getSign());
            final Remote service = findRemoteService(domain);
            if (service != null) {
                onDebugElse(x -> x.debug("routeing RequestAndResponse:" + LOG_META + "\n NEXT:{}", meta, remote, service)
                    , x -> x.debug("routeing RequestAndResponse:" + LOG_META + "\n NEXT:{}", meta, remote.getName(), service.getName()));
                try {
                    return service.getSocket().requestResponse(Request.updateMeta(p, meta, meta.isTrace() ? getName() : getNameOnTrace()));
                } catch (Exception ex) {
                    error(" on process routeing RequestAndResponse:" + LOG_META, meta, remote, ex);
                    return Mono.just(Response.build(meta, getName(), Result.error(ex)));
                }
            }
        }
        error("none registered RequestAndResponse:" + LOG_META, meta, remote);
        return Mono.just(Response.build(meta, getName(), Result.error(new IllegalStateException("no such method '" + meta.getSign() + "' on " +
            getName() + ",I supports " + getRoutes().get() + " with routeing " + isRoute() + (isRoute() ? (" and with routes " + getDomains()) : "")))));
    }

    /**
     * handle rs request
     */
    default Flux<Payload> onRS(Tuple2<Meta, Payload> in, Remote remote) {
        final Meta meta = in.v1;
        final Payload p = in.v2;
        onDebug("process RequestStream:" + LOG_META, meta, remote);
        final Function<Object[], Flux<Object>> handler = findStreamHandler(meta.getSign());
        if (handler != null) {
            final Request request = mustRequest(p);
            return onDebugWithTimerReturns(
                x -> x.debug("process RequestStream:" + LOG_META_REQUEST, meta, request, remote)
                , null
                , x -> {
                    try {
                        return handler.apply(request.getArguments(this.argumentPreProcessor(remote, meta, request))).switchOnFirst((s, f) ->
                            //switch element process first one have a meta
                            s.hasValue() ?
                                Flux.just(Response.buildFirstElement(meta, meta.isTrace() ? getName() : getNameOnTrace(), s.get()))
                                    .concatWith(f.map(Response::buildElement)) :
                                f.map(Response::buildElement));
                    } catch (Exception ex) {
                        x.error("error on process RequestStream:" + LOG_META_REQUEST, meta, request, remote, ex);
                        return Flux.error(ex);
                    }
                }
            );
        } else if (isRoute()) {
            final String domain = domainOf(meta.getSign());
            final Remote service = findRemoteService(domain);
            if (service != null) {
                onDebugElse(x -> x.debug("routeing RequestStream:" + LOG_META + "\n NEXT:{}", meta, remote, service)
                    , x -> x.debug("routeing RequestStream:" + LOG_META + "\n NEXT:{}", meta, remote.getName(), service.getName()));
                try {
                    return service.getSocket().requestStream(Request.updateMeta(p, meta, meta.isTrace() ? getName() : getNameOnTrace()));
                } catch (Exception ex) {
                    error(" on process routeing RequestStream:" + LOG_META, meta, remote, ex);
                    return Flux.error(ex);
                }
            }
        }
        error("none registered RequestStream:" + LOG_META, meta, remote);
        return Flux.error(new IllegalStateException("no such method '" + meta.getSign() + "' on " +
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
            , x -> remote.getSocket().requestResponse(Request.build(sign, getName(), args, this::argumentPostProcessor, getTrace().get()))
                .map(result -> {
                    if ((getDebug().get() || getTrace().get()) && result != null) {
                        final Meta meta = Response.parseMeta(result);
                        x.info("remote trace \n ARGUMENTS: {} \n META: {} \n  COST: SEND {} ,TOTAL {}", args, meta, meta.cost(), meta.costNow());
                    }
                    if (result == null) {
                        x.error("error to request,got null result. \n DOMAIN:: {} \n ARGUMENTS: {} \n SERVICE {}", sign, args, remote);
                        return Result.error(new IllegalAccessError("error to call remote service " + remote.getName() + " from " + getName()));
                    }
                    final Response response = Response.parse(result);
                    x.debug("remote call \n DOMAIN: {} \n ARGUMENTS: {} \n RESULT: {} \n SERVICE:{}", sign, args, response, remote);
                    return response.getResponse();
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
            , x -> remote.getSocket().requestStream(Request.build(sign, getName(), args, this::argumentPostProcessor, getTrace().get()))
                .switchOnFirst((signal, flux) -> {
                    if (signal.hasValue()) {
                        final Payload result = signal.get();
                        if ((getDebug().get() || getTrace().get()) && result != null) {
                            final Meta meta = Response.parseMeta(result);
                            x.info("remote trace \n ARGUMENTS: {} \n META: {} \n  COST: SEND {} ,TOTAL {}", args, meta, meta.cost(), meta.costNow());
                        }
                        assert result != null;
                        return Flux.just(Response.parseElement(result)).concatWith(flux.map(Response::parseElement));
                    } else return flux.map(Response::parseElement);
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
        return remote.getSocket().fireAndForget(Request.build(sign, getName(), args, this::argumentPostProcessor, getTrace().get()));
    }


}
