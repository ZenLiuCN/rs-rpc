package cn.zenliu.java.rs.rpc.core.context;

import cn.zenliu.java.rs.rpc.api.Result;
import cn.zenliu.java.rs.rpc.core.element.Meta;
import cn.zenliu.java.rs.rpc.core.element.Remote;
import cn.zenliu.java.rs.rpc.core.element.Request;
import cn.zenliu.java.rs.rpc.core.proto.RequestImpl;
import cn.zenliu.java.rs.rpc.core.proto.ResponseImpl;
import io.rsocket.Payload;
import mimic.Invokable;
import mimic.MimicLambda;

import java.util.Map;
import java.util.function.Function;

/**
 * @author Zen.Liu
 * @apiNote
 * @since 2021-01-31
 */
public interface ContextCallback extends ContextRoutes {
    char CALLBACK_SCOPE = '@';

    Map<String, Invokable> getCallbackPool();

    default Payload doCallback(Meta meta, Request request, Remote remote) {
        final Invokable invokable = getCallbackPool().get(meta.getAddress());
        if (invokable == null)
            return ResponseImpl.build(meta, getName(), Result.error(new IllegalStateException("invokable missed")));
        return ResponseImpl.build(meta, getName(), Result.wrap(() -> invokable.invoke(request.getArguments(this.argumentPreProcessor(remote, meta, request)))));
    }

    default Object argumentPostProcessor(Object arg, Long tick) {
        if (arg instanceof MimicLambda) return prepareLocalCallback(tick, (MimicLambda<?>) arg);
        return arg;
    }

    default Function<Object, Object> argumentPreProcessor(
        Remote remote,
        Meta meta,
        Request request
    ) {
        return x -> {
            if (x instanceof MimicLambda)
                return prepareRemoteCallback(remote, meta, request, (MimicLambda<?>) x);
            return x;
        };
    }

    default MimicLambda<?> prepareLocalCallback(
        long tick,
        MimicLambda<?> mimic) {
        getCallbackPool().put(tick + '#' + mimic.getMethodName(), mimic.getInvoker());
        return mimic;
    }

    default Object prepareRemoteCallback(Remote remote,
                                         Meta meta,
                                         Request request,
                                         MimicLambda<?> mimic) {
        if (mimic.isSupplier()) { //just in case.
            mimic.setInvoker(o -> mimic.getValue());
            return mimic.disguise();
        }
        mimic.setInvoker(o -> remote.getRSocket()
            .requestResponse(RequestImpl.buildCallback(meta.getFrom() + CALLBACK_SCOPE + request.getTick() + '#' + mimic.getMethodName(),
                getName(),
                o,
                this::argumentPostProcessor,
                getTrace().get()))
        );
        return mimic;
    }

}
