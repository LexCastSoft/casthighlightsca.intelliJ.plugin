package com.casthighlightsca.extension.entrypoint;

import com.casthighlightsca.extension.menu.MenuBuilder;
import com.casthighlightsca.extension.websocket.WebSocketServer;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import org.jetbrains.annotations.NotNull;


public class extensionLauncher implements ToolWindowFactory {

    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {

        /* Extension Instance Init */

        ExtensionInstance.init(false,toolWindow);

        /* Menu Building */
        MenuBuilder builder = new MenuBuilder();
        builder.buildMenuLogin();

        /* Init Web socket for communication between Javascript and Java */
        WebSocketServer webSocketServer = new WebSocketServer();
        try {
            webSocketServer.launchWebSocketServer();
        } catch (Exception e) {
            throw new RuntimeException("Error while lauching the Websocket Server : "  + e);
        }


    }

}





