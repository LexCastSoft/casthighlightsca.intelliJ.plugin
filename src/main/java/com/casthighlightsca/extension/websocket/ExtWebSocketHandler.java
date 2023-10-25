package com.casthighlightsca.extension.websocket;

import com.casthighlightsca.extension.connection.ConnectionService;
import com.casthighlightsca.extension.connection.ConnexionDialog;
import com.casthighlightsca.extension.entrypoint.ExtensionInstance;
import com.casthighlightsca.extension.menu.MenuBuilder;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketClose;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketConnect;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketMessage;
import org.eclipse.jetty.websocket.api.annotations.WebSocket;
import org.eclipse.jetty.websocket.server.WebSocketHandler;
import org.eclipse.jetty.websocket.servlet.WebSocketServletFactory;


/**
 * Listen to every action to catch and trigger the right treatment
 */
@WebSocket
public class ExtWebSocketHandler extends WebSocketHandler {

    @OnWebSocketConnect
    public void onConnect(Session user) throws Exception {
        System.out.println("WebSocket Connected: " + user);
    }

    @OnWebSocketClose
    public void onClose(Session user, int statusCode, String reason) {
        System.out.println("WebSocket Closed. Code:" + statusCode);
    }


    @OnWebSocketMessage
    public void onMessage(Session user, String message) {
        Gson gson = new Gson();
        JsonObject jsonObject = gson.fromJson(message, JsonObject.class);

        String type = jsonObject.get("type").getAsString();

        switch (type) {
            case "login":
                handleLogin(jsonObject);
                break;
            case "dateList":
                handleAnalysis(jsonObject);
                break;
            default:
                System.out.println("Unknown type: " + type);
                break;
        }

    }

    private void handleLogin(JsonObject jsonObject) {

        String login = jsonObject.get("login").getAsString();
        String password = jsonObject.get("password").getAsString();
        String token = jsonObject.get("token").getAsString();
        String url = jsonObject.get("url").getAsString();


        /* Attempts connection  */
        if(ConnectionService.Connect(login,password,token,url)){
            /* First close the ConnexionDialog*/
            ConnexionDialog dialogConnexion = ConnexionDialog.getInstance();
            dialogConnexion.close();
            ExtensionInstance.getInstance().setConnected(true);
            MenuBuilder menu = new MenuBuilder();
            menu.buildMenuConnected();

        }

    }

    private void handleAnalysis(JsonObject jsonObject) {
        // TODO
    }


    @Override
    public void configure(WebSocketServletFactory factory) {
        // nothing for now
    }

    @Override
    public boolean isDumpable(Object o) {
        return super.isDumpable(o);
    }

    @Override
    public String dumpSelf() {
        return super.dumpSelf();
    }
}
