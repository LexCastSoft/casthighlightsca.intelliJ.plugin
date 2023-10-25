package com.casthighlightsca.extension.entrypoint;

import com.intellij.openapi.wm.ToolWindow;

public class ExtensionInstance {

    private static ExtensionInstance instance;

    private boolean isConnected;
    private ToolWindow toolWindow;

    private ExtensionInstance(boolean isConnected, ToolWindow toolWindow) {
        this.isConnected = isConnected;
        this.toolWindow = toolWindow;
    }

    public static void init(boolean isConnected, ToolWindow toolWindow) {
        if(instance == null) {
            instance = new ExtensionInstance(isConnected, toolWindow);
        }
    }

    public static ExtensionInstance getInstance() {
        if(instance == null) {
            throw new IllegalStateException("SingletonClass not initialized, call init() first!");
        }
        return instance;
    }

    public boolean isConnected() {
        return isConnected;
    }

    public void setConnected(boolean status){
        this.isConnected = status;
    }

    public ToolWindow getToolWindow() {
        return this.toolWindow;
    }

}
