package com.casthighlightsca.extension.menu;

import com.casthighlightsca.extension.analyse.ResultsLoader;
import com.casthighlightsca.extension.components.ImagePanel;
import com.casthighlightsca.extension.connection.ConnectionService;
import com.casthighlightsca.extension.connection.ConnexionDialog;
import com.casthighlightsca.extension.connection.CredentialsManagment;
import com.casthighlightsca.extension.connection.CredentialsObject;
import com.casthighlightsca.extension.documentation.DocumentationDialog;
import com.casthighlightsca.extension.entrypoint.ExtensionInstance;
import com.casthighlightsca.extension.feedback.FeedBackDialog;
import com.casthighlightsca.extension.settings.SettingsDialog;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.ui.content.ContentManager;

import javax.management.Notification;
import javax.swing.*;

import java.util.List;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.ArrayList;

public class MenuBuilder {

    private JPanel panel;

    private ToolWindow toolWindow;

    public  MenuBuilder(){
        this.toolWindow = ExtensionInstance.getInstance().getToolWindow();
    }

    public void  buildMenuLogin(){

        JPanel result = new JPanel();
        result.setLayout(new BoxLayout(result, BoxLayout.Y_AXIS));

        ImagePanel imagePanel = new ImagePanel("/icons/hl2.png");
        imagePanel.setMaximumSize(new Dimension(600,70));


        JButton boutonConnexion = new JButton("Login");
        boutonConnexion.setMaximumSize(new Dimension(600, 60));

        /* Initialize the connexion Dialog component */
        ConnexionDialog dialogConnexion = ConnexionDialog.getInstance();

        boutonConnexion.addActionListener(e -> {
            CredentialsManagment cm = new CredentialsManagment();
            CredentialsObject credentials =  cm.loadCredentials();
            if(credentials != null && cm.loadCredentials().getToken() != null && !cm.loadCredentials().getToken().isEmpty()){
                if(ConnectionService.isNotExpired(cm.loadCredentials().getExpiration())){
                    // Session parameters retrived, loading session OK
                    this.buildMenuConnected();
                    return;
                }
            }

            dialogConnexion.buildConnexionDialog();
            dialogConnexion.setLocationRelativeTo(null);
            dialogConnexion.setVisible(true);
            dialogConnexion.setMinimumSize(new Dimension(500, 850));
        });

        /* Menu initialisation */

        result.add(imagePanel);
        result.add(boutonConnexion);


        /* Updating the UI with UI Thread */

        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                // Accéder au ContentManager de toolWindow
                ContentManager contentManager = toolWindow.getContentManager();

                // Supprimer tous les contenus existants
                for (Content content : contentManager.getContents()) {
                    contentManager.removeContent(content, true);
                }

                // Créer un nouveau contenu
                ContentFactory contentFactory = ContentFactory.getInstance();
                Content newContent = contentFactory.createContent(result, "", true);

                // Ajouter le nouveau contenu au ContentManager
                contentManager.addContent(newContent);
            }
        });


    }

    public void buildMenuConnected(){

        JPanel result = new JPanel();
        result.setLayout(new BoxLayout(result, BoxLayout.Y_AXIS));


        ImagePanel imagePanel = new ImagePanel("/icons/hl2.png");
        imagePanel.setMaximumSize(new Dimension(600,70));
        imagePanel.setPreferredSize(new Dimension(350,80));


        JButton projectSelectorButton = new JButton("Select Project");
        projectSelectorButton.setMaximumSize(new Dimension(600, 60));


        /* Adding list of folder to scan */

        DefaultListModel<String> selectedFoldersListModel = new DefaultListModel<>();
        JBList<String> selectedFoldersList = new JBList<>(selectedFoldersListModel);
        selectedFoldersList.setMaximumSize(new Dimension(350, 150));
        JBScrollPane selectedFolderPanel = new JBScrollPane(selectedFoldersList);
        selectedFolderPanel.setPreferredSize(new Dimension(330, 80));



        projectSelectorButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {

                JFileChooser folderChooser = new JFileChooser();
                folderChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);

                int returnValue = folderChooser.showOpenDialog(null);


                if (returnValue == JFileChooser.APPROVE_OPTION) {
                    File selectedFolder = folderChooser.getSelectedFile();
                    String folderPath = selectedFolder.getAbsolutePath();
                    selectedFoldersListModel.addElement(folderPath);

                }
            }
        });


        JButton clearButton = new JButton("Clear");
        clearButton.setMaximumSize(new Dimension(600, 60));

        clearButton.addActionListener(e -> {
            selectedFoldersListModel.clear();

        });

        /************ END PROJECT SELECTION BUTTONS ****************/

        JButton scanButton = new JButton("Scan");
        scanButton.setMaximumSize(new Dimension(600, 60));

        scanButton.addActionListener(e -> {

            // Convert the list model elements to a list of strings.
            List<String> folderPaths = new ArrayList<String>();
            for (int i = 0; i < selectedFoldersListModel.getSize(); i++) {
                folderPaths.add(selectedFoldersListModel.getElementAt(i));
            }


            ResultsLoader.getInstance().processToAnalyse(folderPaths);

        });


        JButton docButton = new JButton("Documentation");
        docButton.setMaximumSize(new Dimension(600, 60));
        /* Initialize the feedback Dialog component */
        DocumentationDialog documentationDialog = DocumentationDialog.getInstance();
        docButton.addActionListener(e -> {
            documentationDialog.buildDocumentationDialog();
            documentationDialog.setLocationRelativeTo(null);
            documentationDialog.setVisible(true);
            documentationDialog.setMinimumSize(new Dimension(900, 850));
        });


        JButton feedbackButton = new JButton("Feedback");
        feedbackButton.setMaximumSize(new Dimension(600, 60));
        /* Initialize the feedback Dialog component */
        FeedBackDialog feedBackDialog = FeedBackDialog.getInstance();
        feedbackButton.addActionListener(e -> {
            feedBackDialog.buildFeedBackDialog();
            feedBackDialog.setLocationRelativeTo(null);
            feedBackDialog.setVisible(true);
            feedBackDialog.setMinimumSize(new Dimension(900, 850));
        });


        JButton settingsButton = new JButton("Settings");
        settingsButton.setMaximumSize(new Dimension(600, 60));
        /* Initialize the connexion Dialog component */
        SettingsDialog dialogSettings = SettingsDialog.getInstance();
        settingsButton.addActionListener(e -> {
            dialogSettings.buildSettingsDialog();
            dialogSettings.setLocationRelativeTo(null);
            dialogSettings.setVisible(true);
            dialogSettings.setMinimumSize(new Dimension(500, 850));
        });



        JButton deconnectionButton = new JButton("Logout");
        deconnectionButton.setMaximumSize(new Dimension(600, 60));
        deconnectionButton.addActionListener(e -> {
            // Delete session's data
            CredentialsManagment cm = new CredentialsManagment();
            cm.deleteCredentials();
            this.buildMenuLogin();
        });


        /* Menu initialisation */

        result.setMinimumSize(new Dimension(600, 800));

        result.add(imagePanel);
        result.add(projectSelectorButton);
        result.add(selectedFolderPanel);
        result.add(clearButton);
        result.add(scanButton);
        result.add(docButton);
        result.add(feedbackButton);
        //result.add(settingsButton);
        result.add(deconnectionButton);


        /* Updating the UI with UI Thread */

        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                // Accéder au ContentManager de toolWindow
                ContentManager contentManager = toolWindow.getContentManager();

                // Supprimer tous les contenus existants
                for (Content content : contentManager.getContents()) {
                    contentManager.removeContent(content, true);
                }

                // Créer un nouveau contenu
                ContentFactory contentFactory = ContentFactory.getInstance();
                Content newContent = contentFactory.createContent(result, "", true);

                // Ajouter le nouveau contenu au ContentManager
                contentManager.addContent(newContent);
            }
        });



    }
}
