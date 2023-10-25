package com.casthighlightsca.extension.connection;

import com.intellij.ui.jcef.JBCefApp;
import org.cef.CefClient;
import org.cef.CefSettings;
import org.cef.browser.CefBrowser;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;



public class ConnexionDialog extends JDialog {

    private CefSettings settings;
    private JBCefApp cefApp;

    private static class Holder {
        static final ConnexionDialog INSTANCE;
        static {
            try {
                INSTANCE = new ConnexionDialog();
            } catch (Exception e) {
                throw new ExceptionInInitializerError(e);
            }
        }
    }

    private ConnexionDialog() {
        initializeCef();
    }

    // Initialize CEF settings and app
    private void initializeCef() {
//        if (CefApp.getState() == CefApp.CefAppState.NONE) {
//            settings = new CefSettings();
//            settings.windowless_rendering_enabled = true;
//
//            cefApp = CefApp.getInstance(settings);
//        } else {
//            cefApp = CefApp.getInstance();
//        }

        cefApp = JBCefApp.getInstance();
    }

    // Static method to get the single instance of the class
    public static ConnexionDialog getInstance() {
        return Holder.INSTANCE;
    }

    public void close() {
        dispose();
    }


    public void buildConnexionDialog() {

        /* Init setting for CEF APP */

        CefClient client = cefApp.createClient().getCefClient();


        // Lire le contenu HTML des ressources
        StringBuilder content = new StringBuilder();
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream("html/login.html");
             BufferedReader reader = new BufferedReader(new InputStreamReader(stream))) {
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
        } catch (IOException e) {
            e.printStackTrace();
            return; // Gérer l'exception de manière appropriée
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
        //CefBrowser browser = client.createBrowser("https://www.speedtest.net/", true, false);

        JPanel panel = new JPanel(new BorderLayout());
        panel.add(browser.getUIComponent(), BorderLayout.CENTER);
        setContentPane(panel);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setMinimumSize(new Dimension(500, 1000));

    }


}