package cn.zenliu.java.rs.rpc.core;

import cn.zenliu.java.rs.rpc.api.Result;
import cn.zenliu.java.rs.rpc.api.Ticks;
import io.rsocket.Payload;
import org.jetbrains.annotations.NotNull;
import org.jooq.lambda.tuple.Tuple2;
import org.jooq.lambda.tuple.Tuple3;
import reactor.core.publisher.Mono;

import java.util.function.Function;

/**
 * @author Zen.Liu
 * @apiNote
 * @since 2021-01-23
 */
public interface ContextScope extends ContextRoutes {
    String LOG_META = "\n META: {}\n SERVICE: {}";
    String LOG_META_REQUEST = "\n META:{}\n REQUEST: {}\n SERVICE: {}";

    default void onServMeta(Tuple2<@NotNull ServMeta, @NotNull Remote> in) {
        final Tuple3<Remote, @NotNull Remote, Boolean> r = in.map1(in.v2::updateFromMeta).concat(in.v1.isKnown(getRoutes().get()));
        processRemoteUpdate(r.v1, r.v2, r.v3);

    }

    default void onFNF(@NotNull Tuple2<Meta, Payload> in, Remote remote) {
        final Meta meta = in.v1;
        onDebug(log -> log.debug("[{}] begin to process FireAndForget:" + LOG_META, getName(), meta, remote));
        final Function<Object[], Result<Object>> handler = findHandler(meta.sign);
        if (handler != null) {
            final Request request = Request.parseRequest(in.v2);
            onDebugWithTimer(
                log -> log.debug("[{}] process FireAndForget:" + LOG_META_REQUEST, getName(), meta, request, remote)
                , null
                , log -> {
                    try {
                        handler.apply(request.arguments);
                        if ((getDebug().get() || getTrace().get())) {
                            final Long keys = meta.trace.keySet().stream().sorted().findFirst().get();
                            log.info("[{}] remote trace \n META: {} \n TIME COST: {} μs", getName(), meta, Ticks.betweenNow(keys).getNano() / 1000.0);

                        }
                    } catch (Exception e) {
                        log.error("[{}] error to process FireAndForget:" + LOG_META_REQUEST, getName(), meta, request, remote, e);
                    }
                });
        } else if (isRoute()) {
            final String domain = meta.sign.substring(0, ProxyUtil.DOMAIN_SPLITTER);
            final Remote service = findRemoteService(domain);
            if (service != null) {
                onDebugElse(log -> log.debug("[{}] routeing FireAndForget:" + LOG_META + "\n NEXT:{}", getName(), meta, remote, service)
                    , log -> log.debug("[{}] routeing FireAndForget:" + LOG_META + "\n NEXT:{}", getName(), meta, remote.name, service.name));
                service.socket.fireAndForget(Request.updateMeta(in.v2, meta, (getDebug().get() || getTrace().get() || meta.track) ? getName() : null));
                return;
            }
        }
        withLog(log -> log.error("[{}] none registered FireAndForget:" + LOG_META, getName(), meta, remote));
    }

    default Mono<Payload> onRR(Tuple2<Meta, Payload> in, Remote remote) {
        final Meta meta = in.v1;
        final Payload p = in.v2;
        onDebug(log -> log.debug("[{}] process RequestAndResponse:" + LOG_META, getName(), meta, remote));
        final Function<Object[], Result<Object>> handler = findHandler(meta.sign);
        if (handler != null) {
            final Request request = Request.parseRequest(p);
            return onDebugWithTimerReturns(
                log -> log.debug("[{}] process RequestAndResponse:" + LOG_META_REQUEST, getName(), meta, request, remote)
                , null
                , log -> {
                    try {
                        final Result<Object> res = handler.apply(request.arguments);
                        return Mono.just(Response.build(meta, (getDebug().get() || getTrace().get() || meta.track) ? getName() : null, res != null ? res : Result.ok(null)));
                    } catch (Exception ex) {
                        log.error("[{}] error on process RequestAndResponse:" + LOG_META_REQUEST, getName(), meta, request, remote, ex);
                        return Mono.just(Response.build(meta, getName(), Result.error(ex)));
                    }
                }
            );
        } else if (isRoute()) {
            final String domain = meta.sign.substring(0, meta.sign.indexOf(ProxyUtil.DOMAIN_SPLITTER));
            final Remote service = findRemoteService(domain);
            if (service != null) {
                onDebugElse(log -> log.debug("[{}] routeing RequestAndResponse:" + LOG_META + "\n NEXT:{}", getName(), meta, remote, service)
                    , log -> log.debug("[{}] routeing RequestAndResponse:" + LOG_META + "\n NEXT:{}", getName(), meta, remote.name, service.name));
                try {
                    return service.socket.requestResponse(Request.updateMeta(p, meta, (getDebug().get() || getTrace().get() || meta.track) ? getName() : null));
                } catch (Exception ex) {
                    withLog(log -> log.error("[{}] error on process routeing RequestAndResponse:" + LOG_META, getName(), meta, remote, ex));
                    return Mono.just(Response.build(meta, getName(), Result.error(ex)));
                }
            }
        }
        withLog(log -> log.error("[{}] none registered RequestAndResponse:" + LOG_META, getName(), meta, remote));
        return Mono.just(Response.build(meta, getName(), Result.error(new IllegalStateException("no such method '" + meta.sign + "' on " +
            getName() + ",I supports " + getRoutes().get() + " with routeing " + isRoute() + (isRoute() ? (" and with routes " + getDomains()) : "")))));
    }

    default Result<Object> routeingRR(String sign, Object[] args) {
        final String domain = sign.substring(0, sign.indexOf(ProxyUtil.DOMAIN_SPLITTER));
        final Remote remote = findRemoteService(domain);
        if (remote == null) {
            withLog(log -> log.error("[{}] not found remote service of {}[{}]  with routes {}", getName(), sign, domain, getDomains()));
            throw new IllegalStateException("not exists service for '" + sign + "' in " + getName());
        }
        return onDebugWithTimerReturns(
            log -> log.debug("[{}] remote call \n DOMAIN: {} \n ARGUMENTS: {} .", getName(), sign, args)
            , null
            , log -> {
                final Payload result = remote.socket.requestResponse(Request.build(sign, getName(), args, true))  //requester always in trace
                    .block(getTimeout().get());
                if ((getDebug().get() || getTrace().get()) && result != null) {
                    final Meta meta = Response.parseMeta(result);
                    final long[] keys = meta.trace.keySet().stream().mapToLong(x -> x).sorted().toArray();
                    log.info("[{}] remote trace \n DOMAIN: {} \n ARGUMENTS: {} \n META: {} \n TIME COST: {} μs", getName(), sign, args, meta, Ticks.between(keys[0], keys[keys.length - 1]).getNano() / 1000.0);

                }
                if (result == null) {
                    log.error("[{}] error to request,got null result. \n DOMAIN:: {} \n ARGUMENTS: {} \n SERVICE {}", getName(), sign, args, remote);
                    return Result.error(new IllegalAccessError("error to call remote service " + remote.name + " from " + getName()));
                }
                final Response response = Response.parse(result);
                log.debug("[{}] remote call \n DOMAIN: {} \n ARGUMENTS: {} \n RESULT: {} \n SERVICE:{}", getName(), sign, args, response, remote);
                return response.response;
            }
        );
    }

    default void routeingFNF(String sign, Object[] args) {
        final String domain = sign.substring(0, sign.indexOf(ProxyUtil.DOMAIN_SPLITTER));
        final Remote remote = findRemoteService(domain);
        if (null == remote) {
            throw new IllegalStateException("not exists service for " + sign);
        }
        remote.socket.fireAndForget(Request.build(sign, getName(), args, true)); //requester always in trace
    }

}
