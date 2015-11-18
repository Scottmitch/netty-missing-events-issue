package io.gatling.issues;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.security.ConstraintMapping;
import org.eclipse.jetty.security.ConstraintSecurityHandler;
import org.eclipse.jetty.security.HashLoginService;
import org.eclipse.jetty.security.LoginService;
import org.eclipse.jetty.security.authentication.BasicAuthenticator;
import org.eclipse.jetty.security.authentication.LoginAuthenticator;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.eclipse.jetty.util.security.Constraint;

public class JettyServer implements Closeable {

    public static final String USER = "user";
    public static final String ADMIN = "admin";

    private final Server server;

    public JettyServer(int port) throws Exception {
        server = new Server();
        addHttpConnector(server, port);
        addAuthHandler(server, Constraint.__BASIC_AUTH, new BasicAuthenticator(), new SimpleHandler());
        server.start();

    }

    @Override
    public void close() throws IOException {
        try {
            server.stop();
        } catch (Exception e) {
            throw new IOException("Couldn't stop Jetty server", e);
        }
    }

    private final void addHttpConnector(Server server, int port) {
        ServerConnector connector = new ServerConnector(server);
        connector.setPort(port);
        server.addConnector(connector);
    }

    private final void addAuthHandler(Server server, String auth, LoginAuthenticator authenticator, Handler handler) {

        LoginService loginService = new HashLoginService("MyRealm", Thread.currentThread().getContextClassLoader().getResource("realm.properties").toString());

        server.addBean(loginService);

        Constraint constraint = new Constraint();
        constraint.setName(auth);
        constraint.setRoles(new String[] { USER, ADMIN });
        constraint.setAuthenticate(true);

        ConstraintMapping mapping = new ConstraintMapping();
        mapping.setConstraint(constraint);
        mapping.setPathSpec("/*");

        Set<String> knownRoles = new HashSet<>();
        knownRoles.add(USER);
        knownRoles.add(ADMIN);

        List<ConstraintMapping> cm = new ArrayList<>();
        cm.add(mapping);

        ConstraintSecurityHandler security = new ConstraintSecurityHandler();
        security.setConstraintMappings(cm, knownRoles);
        security.setAuthenticator(authenticator);
        security.setLoginService(loginService);
        security.setHandler(handler);
        server.setHandler(security);
    }

    private static class SimpleHandler extends AbstractHandler {

        public void handle(String s, Request r, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {

            if (request.getHeader("X-401") != null) {
                response.setStatus(401);
                response.setContentLength(0);

            } else {
                response.addHeader("X-Auth", request.getHeader("Authorization"));
                response.addHeader("X-Content-Length", String.valueOf(request.getContentLength()));
                response.setStatus(200);

                int size = 10 * 1024;
                if (request.getContentLength() > 0) {
                    size = request.getContentLength();
                }
                byte[] bytes = new byte[size];
                int contentLength = 0;
                if (bytes.length > 0) {
                    int read = request.getInputStream().read(bytes);
                    if (read > 0) {
                        contentLength = read;
                        response.getOutputStream().write(bytes, 0, read);
                    }
                }
                response.setContentLength(contentLength);
            }
            response.getOutputStream().flush();
            response.getOutputStream().close();
        }
    }
}
