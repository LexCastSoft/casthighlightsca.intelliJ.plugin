package com.casthighlightsca.extension.websocket;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.websocket.server.WebSocketHandler;
import org.eclipse.jetty.websocket.servlet.WebSocketServletFactory;

public class WebSocketServer {

    public void launchWebSocketServer() throws Exception {
        new Thread(() -> {
            Server server = new Server(8080);
            WebSocketHandler wsHandler = new WebSocketHandler() {
                @Override
                public void configure(WebSocketServletFactory factory) {
                    factory.register(ExtWebSocketHandler.class);
                }
            };
            server.setHandler(wsHandler);
            try {
                server.start();
                server.join();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

}
