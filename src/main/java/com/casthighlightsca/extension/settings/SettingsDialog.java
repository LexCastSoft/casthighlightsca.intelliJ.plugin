package com.casthighlightsca.extension.settings;

import org.cef.CefApp;
import org.cef.CefClient;
import org.cef.CefSettings;
import org.cef.browser.CefBrowser;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;

public class SettingsDialog extends JDialog {
    private CefSettings settings;
    private CefApp cefApp;

    private static class Holder {
        static final SettingsDialog INSTANCE;
        static {
            try {
                INSTANCE = new SettingsDialog();
            } catch (Exception e) {
                throw new ExceptionInInitializerError(e);
            }
        }
    }
    private SettingsDialog() {
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
    public static SettingsDialog getInstance() {
        return Holder.INSTANCE;
    }


    public void buildSettingsDialog() {

        /* Init setting for CEF APP */
        CefClient client = cefApp.createClient();

        // Read  HTML content of the resource
        StringBuilder content = new StringBuilder();
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream("html/settings.html");
             BufferedReader reader = new BufferedReader(new InputStreamReader(stream))) {
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }

        // Sauvegarder le contenu HTML dans un fichier temporaire
        Path tempFile;
        try {
            tempFile = Files.createTempFile("temp", ".html");
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(tempFile.toFile()))) {
                writer.write(content.toString());
            }
        } catch (IOException e) {
            e.printStackTrace();
            return; // Gérer l'exception de manière appropriée
        }

        // Load content in browser
        CefBrowser browser = client.createBrowser(tempFile.toUri().toString(), true, false);

        JPanel panel = new JPanel(new BorderLayout());
        panel.add(browser.getUIComponent(), BorderLayout.CENTER);
        setContentPane(panel);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setMinimumSize(new Dimension(500, 850));


    }
}
