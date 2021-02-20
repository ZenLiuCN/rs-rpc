package cn.zenliu.java.rs.rpc.core.util;

import cn.zenliu.java.rs.rpc.core.element.Remote;
import org.jooq.lambda.tuple.Tuple2;
import reactor.core.publisher.Sinks;

import static org.jooq.lambda.tuple.Tuple.tuple;

/**
 * @author Zen.Liu
 * @apiNote
 * @since 2021-02-20
 */
public interface RemoteObserverUtil {
    enum ObserverEvent {
        WEIGHT_CHANGE, REMOVED
    }

    Sinks.Many<Tuple2<ObserverEvent, Remote>> observer = Sinks.many().multicast().onBackpressureBuffer();

    static void updateWeight(Remote remote) {
        observer.tryEmitNext(tuple(ObserverEvent.WEIGHT_CHANGE, remote));
    }

    static void removed(Remote remote) {
        observer.tryEmitNext(tuple(ObserverEvent.REMOVED, remote));
    }
}
