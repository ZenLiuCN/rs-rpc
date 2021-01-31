package cn.zenliu.java.rs.rpc.core.context;

import cn.zenliu.java.rs.rpc.api.Result;
import cn.zenliu.java.rs.rpc.core.element.Remote;
import cn.zenliu.java.rs.rpc.core.proto.Meta;
import cn.zenliu.java.rs.rpc.core.proto.Request;
import cn.zenliu.java.rs.rpc.core.proto.Response;
import io.rsocket.Payload;
import mimic.Invokable;
import mimic.MimicLambda;

import java.util.Map;

/**
 * @author Zen.Liu
 * @apiNote
 * @since 2021-01-31
 */
public interface ContextCallback extends ContextRoutes {
    char CALLBACK_SCOPE = '@';

    Map<String, Invokable> getCallbackPool();

    default Payload doCallback(Meta meta, Request request, Remote remote) {
        final Invokable invokable = getCallbackPool().get(meta.getSign());
        if (invokable == null)
            return Response.build(meta, getName(), Result.error(new IllegalStateException("invokable missed")));
        return Response.build(meta, getName(), Result.wrap(() -> invokable.invoke(request.getArguments())));
    }

    default MimicLambda<?> prepareLocalCallback(
        Request request,
        MimicLambda<?> mimic) {
        getCallbackPool().put(request.getTick() + '#' + mimic.getMethodName(), mimic.getInvoker());
        return mimic;
    }

    default Object prepareRemoteCallback(Remote remote,
                                         Meta requestMeta,
                                         Request request,
                                         MimicLambda<?> mimic) {
        if (mimic.isSupplier()) { //just in case.
            mimic.setInvoker(o -> mimic.getValue());
            return mimic.disguise();
        }
        mimic.setInvoker(o -> remote.getSocket()
            .requestResponse(Request.buildCallback(requestMeta.getFrom() + CALLBACK_SCOPE + request.getTick() + '#' + mimic.getMethodName(),
                getName(),
                o,
                getTrace().get()))
        );
        return mimic.disguise();
    }

}
