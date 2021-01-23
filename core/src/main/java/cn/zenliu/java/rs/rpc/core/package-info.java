/**
 * NOTE<br>
 * 1. Service: means application service.
 * 2. Scope: means a group of Server and Client with shared same group of registered service.
 * 3. XXXMeta: means transmit in meta data
 * 4. Remote: means a RSocketServer or RSocketClient connected into a Scope
 * 5. Domain: means a Unique Service name (current use full name of service class)
 * 6. Handler: a wrapped method of a service instance
 * 7. HandlerSignature: a service method signature {@link cn.zenliu.java.rs.rpc.core.ProxyUtil#signature(java.lang.reflect.Method, java.lang.Class)}.
 */
package cn.zenliu.java.rs.rpc.core;