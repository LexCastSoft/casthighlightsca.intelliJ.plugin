package com.casthighlightsca.extension.documentation;

import org.cef.CefApp;
import org.cef.CefClient;
import org.cef.CefSettings;
import org.cef.browser.CefBrowser;

import javax.swing.*;
import java.awt.*;

public class DocumentationDialog extends JDialog {
    private CefSettings settings;
    private CefApp cefApp;

    private static class Holder {
        static final com.casthighlightsca.extension.documentation.DocumentationDialog INSTANCE;
        static {
            try {
                INSTANCE = new com.casthighlightsca.extension.documentation.DocumentationDialog();
            } catch (Exception e) {
                throw new ExceptionInInitializerError(e);
            }
        }
    }
    private DocumentationDialog() {
        initializeCef();
    }

    // Initialize CEF settings and app
    private void initializeCef() {
        if (CefApp.getState() == CefApp.CefAppState.NONE) {
            settings = new CefSettings();
            settings.windowless_rendering_enabled = true;
            cefApp = CefApp.getInstance(settings);
        } else {
            cefApp = CefApp.getInstance();
        }
    }

    /**
     * Static method to get the single instance of the class
     * @return Instance of SettingsDialog
     */
    public static com.casthighlightsca.extension.documentation.DocumentationDialog getInstance() {
        return com.casthighlightsca.extension.documentation.DocumentationDialog.Holder.INSTANCE;
    }


    public void buildDocumentationDialog() {

        /* Init feedback for CEF APP */
        CefClient client = cefApp.createClient();

        // Load content in browser
        // TODO FIX URL
        CefBrowser browser = client.createBrowser("https://www.jetbrains.com/help/idea/managing-plugins.html", true, false);

        JPanel panel = new JPanel(new BorderLayout());
        panel.add(browser.getUIComponent(), BorderLayout.CENTER);
        setContentPane(panel);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setMinimumSize(new Dimension(900, 850));


    }
}
