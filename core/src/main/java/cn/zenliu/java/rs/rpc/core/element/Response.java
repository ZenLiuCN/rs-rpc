package cn.zenliu.java.rs.rpc.core.element;

import cn.zenliu.java.rs.rpc.api.Result;
import io.rsocket.Payload;
import org.jetbrains.annotations.Nullable;

/**
 * @author Zen.Liu
 * @apiNote
 * @since 2021-02-02
 */
public interface Response extends Transmit<Payload, Returns> {
    @Nullable Result<Object> getResponse();

    boolean hasElement();

    Response setResponse(Result<Object> res);

    @Nullable Object getElement();

    Response setElement(Object element);
}
