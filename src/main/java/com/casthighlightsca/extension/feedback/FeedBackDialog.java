package com.casthighlightsca.extension.feedback;

import org.cef.CefApp;
import org.cef.CefClient;
import org.cef.CefSettings;
import org.cef.browser.CefBrowser;

import javax.swing.*;
import java.awt.*;


public class FeedBackDialog extends JDialog {
    private CefSettings settings;
    private CefApp cefApp;

    private static class Holder {
        static final com.casthighlightsca.extension.feedback.FeedBackDialog INSTANCE;
        static {
            try {
                INSTANCE = new com.casthighlightsca.extension.feedback.FeedBackDialog();
            } catch (Exception e) {
                throw new ExceptionInInitializerError(e);
            }
        }
    }
    private FeedBackDialog() {
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
    public static com.casthighlightsca.extension.feedback.FeedBackDialog getInstance() {
        return com.casthighlightsca.extension.feedback.FeedBackDialog.Holder.INSTANCE;
    }


    public void buildFeedBackDialog() {

        /* Init feedback for CEF APP */
        CefClient client = cefApp.createClient();

        // Load content in browser
        // TODO FIX URL
        CefBrowser browser = client.createBrowser("https://www.castsoftware.com/highlight", true, false);

        JPanel panel = new JPanel(new BorderLayout());
        panel.add(browser.getUIComponent(), BorderLayout.CENTER);
        setContentPane(panel);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setMinimumSize(new Dimension(900, 850));


    }
}
