package cn.zenliu.java.rs.rpc.core.element;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NonNull;
import org.jooq.lambda.Seq;

import java.util.Arrays;
import java.util.Objects;

/**
 * @author Zen.Liu
 * @apiNote
 * @since 2021-02-20
 */
public interface Route {
    String getService();

    String getMethod();

    String getAddress();

    boolean isRouting();

    int jumps();

    String getSource();

    String toRoute();

    Route append(String scopeName);

    static Route fromRoute(String route) {
        if (route == null || route.isEmpty() || !route.contains(ROUTE_DIVIDER + "") || !route.contains(SERVICE_DIVIDER + ""))
            throw new IllegalArgumentException("route '" + route + "' is invalid .");
        final int idxService = route.indexOf(SERVICE_DIVIDER);
        final int idxRoute = route.indexOf(ROUTE_DIVIDER);
        final String service = route.substring(0, idxService);
        final String method = route.substring(idxService + 1, idxRoute);
        final String[] routes = route.substring(idxRoute + 1).split(ROUTE_SPLITTER + "");
        return RouteImpl.of(service, method, routes);
    }

    char SERVICE_DIVIDER = '#';
    char ROUTE_SPLITTER = '>';
    char ROUTE_DIVIDER = '?';

    @Getter
    @AllArgsConstructor(staticName = "of")
    final class RouteImpl implements Route {
        @NonNull final String service;
        @NonNull final String method;
        @NonNull final String[] routing;

        @Override
        public String getAddress() {
            return service + SERVICE_DIVIDER + method;
        }

        @Override
        public boolean isRouting() {
            return routing.length > 1;
        }

        @Override
        public int jumps() {
            return routing.length;
        }

        @Override
        public String getSource() {
            return routing[0];
        }

        @Override
        public String toRoute() {
            return service + SERVICE_DIVIDER + method + ROUTE_DIVIDER + Seq.of(routing).toString(ROUTE_SPLITTER + "");
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof RouteImpl)) return false;
            RouteImpl route = (RouteImpl) o;
            return getService().equals(route.getService()) && getMethod().equals(route.getMethod());
        }

        @Override
        public int hashCode() {
            int result = Objects.hash(getService(), getMethod());
            result = 31 * result;
            return result;
        }

        @Override
        public Route append(String scopeName) {
            final String[] newRouting = Arrays.copyOf(routing, routing.length + 1);
            newRouting[newRouting.length - 1] = scopeName;
            return RouteImpl.of(service, method, newRouting);
        }
    }
}
