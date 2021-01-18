package cn.zenliu.java.rs.rpc.api;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Range;

import java.net.URI;
import java.time.Duration;

/**
 * @author Zen.Liu
 * @apiNote
 * @since 2021-01-18
 */
public interface Config {
    /**
     * 0 TCP;1 WS
     */
    int getMode();

    /**
     * 0 ZERO_COPY;1 default;
     */
    int getPayloadCodec();

    @Nullable ResumeSetting getResume();


    interface ServerConfig extends Config {
        @Nullable String getBindAddress();

        @Nullable Integer getPort();

        @Nullable @Range(from = 64, to = Integer.MAX_VALUE) Integer getFragment();

        @Nullable @Range(from = 64, to = Integer.MAX_VALUE) Integer getMaxInboundPayloadSize();
    }

    interface ResumeSetting {
        @Nullable Retry getRetry();

        @Nullable Duration getSessionDuration();

        @Nullable Duration getStreamTimeout();

        boolean isCleanupStoreOnKeepAlive();

        @Nullable String getToken();
    }

    interface ClientConfig extends Config {
        @Nullable String getHost();

        @Nullable Integer getPort();

        @Nullable URI getUri();

        @Nullable @Range(from = 64, to = Integer.MAX_VALUE) Integer getFragment();

        @Nullable @Range(from = 64, to = Integer.MAX_VALUE) Integer getMaxInboundPayloadSize();

        @Nullable Duration getKeepAliveInterval();

        @Nullable Duration getKeepAliveMaxLifeTime();

        @Nullable Retry getRetry();

    }

    interface Retry {
        @Getter
        @Setter
        class Max implements Retry {
            long maxAttempts;
        }

        @Getter
        @Setter
        final class FixedDelay extends Max {
            Duration fixedDelay;
        }

        @Getter
        @Setter
        final class Backoff extends Max {
            Duration minDelay;
        }

        final class Indefinitely implements Retry {
        }
    }

    @Getter
    @Builder
    class Resume implements ResumeSetting {
        final Retry retry;
        final Duration sessionDuration;
        final Duration streamTimeout;
        @Builder.Default final boolean cleanupStoreOnKeepAlive = false;
        final String token;
    }

    @Getter
    @Builder
    class Server implements ServerConfig {
        @Builder.Default final int mode = 0;
        @Builder.Default final int payloadCodec = 0;
        final String bindAddress;
        final Integer port;
        final Integer fragment;
        final Integer maxInboundPayloadSize;
        final ResumeSetting resume;
    }

    @Getter
    @Builder
    class Client implements ClientConfig {
        @Builder.Default final int mode = 0;
        @Builder.Default final int payloadCodec = 0;
        final String host;
        final Integer port;
        final URI Uri;
        final Integer fragment;
        final Integer maxInboundPayloadSize;
        final Duration keepAliveInterval;
        final Duration keepAliveMaxLifeTime;
        final Retry retry;
        final ResumeSetting resume;
    }
}
