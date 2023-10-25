package com.casthighlightsca.extension.analyse;

import com.casthighlightsca.extension.connection.CredentialsManagment;
import com.casthighlightsca.extension.connection.CredentialsObject;
import com.casthighlightsca.extension.menu.MenuBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.ui.Messages;
import okhttp3.OkHttpClient;
import org.cef.CefApp;
import org.cef.CefClient;
import org.cef.CefSettings;
import org.cef.browser.CefBrowser;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;


public class ResultsLoader {

    private CefApp cefApp;
    private String htmlResourcePath = "html/results.html";

    private CefSettings settings;


    private static class Holder {
        static final ResultsLoader INSTANCE;
        static {
            try {
                INSTANCE = new ResultsLoader();
            } catch (Exception e) {
                throw new ExceptionInInitializerError(e);
            }
        }
    }
    private ResultsLoader() {
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
    public static ResultsLoader getInstance() {
        return ResultsLoader.Holder.INSTANCE;
    }



    public void processToAnalyse(List<String> listPathToAnalyse) {

        // Run task asynchronously with a progress bar
        ProgressManager.getInstance().run(new Task.Backgroundable(null, "Analyzing Dependencies", false) {

            @Override
            public void run(ProgressIndicator indicator) {
                // Set the progress bar indeterminate
                indicator.setIndeterminate(true);

                // Notify user task has started
                indicator.setText("Starting analysis...");

                // Create an instance of the Analyzer class.
                Analyser analyzer = new Analyser();

                // Find the config files within the selected folders.
                List<String> configFiles = analyzer.findConfigFile(listPathToAnalyse);

                for (String configFile : configFiles) {

                    if (configFile.endsWith("\\pom.xml")) {

                        // Extract dependencies from POM file.
                        List<Dependency> DependenciesPOM = null;
                        try {
                            DependenciesPOM = analyzer.getDependencies(configFile.toString());
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }

                        if(DependenciesPOM == null){
                            ApplicationManager.getApplication().invokeLater(() ->
                                    Messages.showErrorDialog("Analysis failed !", "Task fail")
                            );
                            return;
                        }



                        // Requesting the Highlight API for the list of dependencies
                        Map<String, JsonObject> resultsToDisplay = new HashMap<String, JsonObject>();

                        for (Dependency dep : DependenciesPOM) {
                            JsonObject jsonResults = getComponentInfo(dep,false);
                            if(dep.getVersion() != null && !dep.getVersion().isEmpty() && jsonResults != null && jsonResults.size() > 0 ){
                                String key = generateKey(dep);
                                resultsToDisplay.put(key, jsonResults);
                            }
                        }

                        Path path = Paths.get(configFile);
                        Path parentFolder = path.getParent().getFileName();

                        if(resultsToDisplay.isEmpty()){
                            // Nothing to display
                            return;
                        }

                        StringBuilder displayResults = loadAllResults(resultsToDisplay, parentFolder.toString(), configFile);

                        // Update the progress text
                        indicator.setText("Analyzing: " + configFile);

                        // Execute the displayResults method on the Event Dispatch Thread (EDT) to avoid UI freezing
                        ApplicationManager.getApplication().invokeLater(() -> displayResults(displayResults));


                    } else if (configFile.endsWith("package.json")) {
                        // Extract dependencies from package.json
                        List<Dependency> DependenciesJSON = null;
                        try {
                            DependenciesJSON = analyzer.extractDependenciesFromJson(configFile.toString());
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }

                        if(DependenciesJSON == null){
                            ApplicationManager.getApplication().invokeLater(() ->
                                    Messages.showErrorDialog("Analysis failed for JSON file!", "Task fail")
                            );
                            return;
                        }

                        // Requesting the Highlight API for the list of dependencies
                        Map<String, JsonObject> resultsToDisplay = new HashMap<String, JsonObject>();

                        for (Dependency dep : DependenciesJSON) {
                            JsonObject jsonResults = getComponentInfo(dep, true);
                            if(dep.getVersion() != null && !dep.getVersion().isEmpty() && jsonResults != null && jsonResults.size() > 0){
                                String key = generateKey(dep);
                                resultsToDisplay.put(key, jsonResults);
                            }
                        }

                        Path path = Paths.get(configFile);
                        Path parentFolder = path.getParent().getFileName();

                        if(resultsToDisplay.isEmpty()){
                            // Nothing to display
                            return;
                        }

                        StringBuilder displayResults = loadAllResults(resultsToDisplay, parentFolder.toString(), configFile);

                        // Update the progress text
                        indicator.setText("Analyzing: " + configFile);

                        // Execute the displayResults method on the Event Dispatch Thread (EDT) to avoid UI freezing
                        ApplicationManager.getApplication().invokeLater(() -> displayResults(displayResults));

                    }
                }

                // Notify user when task is completed
                ApplicationManager.getApplication().invokeLater(() ->
                        Messages.showInfoMessage("Analysis completed successfully!", "Task Completed")
                );
            }
        });
    }


    private String generateKey(Dependency dep) {
        return dep.getArtifactId() + ":" + dep.getVersion();
    }


    public void displayResults(StringBuilder content){

        CefClient client = cefApp.createClient();

        // Save Wole content into temporary file for Explorer Loading
        Path tempFile;
        try {
            tempFile = Files.createTempFile("temp", ".html");
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(tempFile.toFile()))) {
                writer.write(content.toString());
            }
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }

        // Display Content in IntelliJ
        CefBrowser browser = client.createBrowser(tempFile.toUri().toString(), true, false);

        JFrame frame = new JFrame("Results");
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(browser.getUIComponent(), BorderLayout.CENTER);
        frame.setContentPane(panel);
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.setMinimumSize(new Dimension(900, 1200));
        frame.pack();
        frame.setVisible(true);

    }


    /**
     * Load CVEs for a given dependency
     */

    public JsonObject getComponentInfo(Dependency dependency, boolean isPackageJson) {

        /* PARAM ENCODING FOR REQUEST */

        CredentialsObject credential  = new CredentialsManagment().loadCredentials();

        if(credential.getToken() == null){
            MenuBuilder mb = new MenuBuilder();
            mb.buildMenuLogin();
            // Notify user when task is completed
            ApplicationManager.getApplication().invokeLater(() ->
                    Messages.showInfoMessage("Token time out, reconnect to launch a new scan", "Task Canceled")
            );
            return null;
        }

        String repoName;
        String companyId = credential.getCompanyId();
        String token = credential.getToken();
        String url = credential.getUrl();
        int expiration = credential.getExpiration();


        String groupId = dependency.getGroupId();
        String artifactId = dependency.getArtifactId();
        String version = dependency.getVersion();

        if(version == null){
            return null;
        }


        if(isPackageJson){
            repoName = artifactId + ".npm";
        } else {
            repoName = groupId + ":" + artifactId;
        }


        String repoNameBase64 = Base64.getEncoder().encodeToString(repoName.getBytes());
        String repoVersionBase64 = Base64.getEncoder().encodeToString(version.getBytes());
        String paramContent = "?contentType='application%2Fjson%3B%20charset%3Dutf-8'";

        String finalUrl = url + "WS2/domains/" + companyId + "/components/" + repoNameBase64 + "/" + repoVersionBase64 + "/timeline" + paramContent;

        /* REQUEST */

        // TODO ADD URL BUILDER CHECK FOR DOUBLE SLASH IN URL

        try {
            URL obj = new URL(finalUrl);
            HttpURLConnection connectionRequest = (HttpURLConnection) obj.openConnection();

            connectionRequest.setRequestMethod("GET");
            connectionRequest.setRequestProperty("Authorization", "Bearer " + token);
            connectionRequest.setRequestProperty("Accept", "application/json; charset=UTF-8");

            int responseCode = connectionRequest.getResponseCode();

            if (responseCode == HttpURLConnection.HTTP_OK) {


                BufferedReader in = new BufferedReader(new InputStreamReader(connectionRequest.getInputStream()));
                String inputLine;
                StringBuilder content = new StringBuilder();
                while ((inputLine = in.readLine()) != null) {
                    content.append(inputLine);
                }
                in.close();

                JsonElement element = JsonParser.parseString(content.toString());
                JsonObject json = new JsonObject();

                if (element.isJsonObject()) {
                    json = element.getAsJsonObject();
                    return json;
                }else{
                    return null;
                }

            } else if(responseCode == HttpURLConnection.HTTP_UNAUTHORIZED) {
                /* Token must be too old, reconnection is required */
                MenuBuilder mb = new MenuBuilder();
                mb.buildMenuLogin();
                CredentialsManagment cm = new CredentialsManagment();
                cm.deleteCredentials();
                return null;

            }else{
                return null;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }


    private String extractVersionFromKey(String compositeKey) {
        String[] parts = compositeKey.split(":");
        if (parts.length >= 2) {
            return parts[1];
        }
        return null;
    }


    public StringBuilder loadAllResults(Map<String, JsonObject> listDependencyResults, String workspaceName, String pathOfConfigFile) {

        // Set Required parameters

        // Load results answer HTML skeleton and css

        StringBuilder contentReader = new StringBuilder();
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream(htmlResourcePath);
             BufferedReader reader = new BufferedReader(new InputStreamReader(stream))) {
            String line;
            while ((line = reader.readLine()) != null) {
                contentReader.append(line).append("\n");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        // Dependencies results injection

        Document dom = Jsoup.parse(contentReader.toString());

        Element element = dom.getElementById("allComponents");

        StringBuilder content = new StringBuilder();

        // Simulating the htmlContentString based on panel.webview.html.toString() in TypeScript code.
        String htmlContentString = ""; // Placeholder; actual logic needs to be implemented.

        // Get the allComponents element from DOM
        Element allComponentsElement = dom.getElementById("allComponents");

        // Initializations

        final ArrayList<String> allComponentsInfoHTML = new ArrayList<String>();

        AtomicInteger dependencyNumber = new AtomicInteger();

        // Placeholder arrays
        ArrayList<Integer> classificationDeny = new ArrayList<>();
        ArrayList<Integer> classificationAllow = new ArrayList<>();
        ArrayList<Integer> repoVersionCount = new ArrayList<>();

        // Global Counting variables for the Dashboard top Graph
        AtomicInteger workspaceVulnerabilityCriticalCount = new AtomicInteger();

        AtomicInteger workspaceVulnerabilityHighCount = new AtomicInteger();
        AtomicInteger workspaceVulnerabilityMediumCount = new AtomicInteger();
        AtomicInteger workspaceVulnerabilityLowCount = new AtomicInteger();
        AtomicInteger workspaceVulnerabilityAdvisoriesCount = new AtomicInteger();


        listDependencyResults.forEach((key, dependencyResponse) -> {

                    // Extract version from key
                    String requiredVersion;
                    String decryptVersion = extractVersionFromKey(key);

                    if(decryptVersion != null && !decryptVersion.isEmpty()){
                        String temp = decryptVersion.replaceAll("~", "");
                        temp = temp.replaceAll("^","");
                        temp = temp.replaceAll("\"","");

                        /* Check that the cast is well made */
                        String test = "" + temp.charAt(0);
                        if(test.equals("^")){
                            requiredVersion = temp.substring(1);
                        }else{
                            requiredVersion = temp;
                        }

                    } else {
                        requiredVersion = "";
                    }


            if(dependencyResponse == null || dependencyResponse.get("versions") == null){
                        // No version fetch, not processed dependecy
                        return;
                    }

                    int dependecyNumberTemp = dependencyResponse.get("versions").getAsJsonArray().size();

                    long currentTimeMillis = new Date().getTime();

                    // Calculate the duration since the creation in years
                    double yearsSinceCreation = (double) (currentTimeMillis - dependencyResponse.get("creation").getAsLong()) / 1000 / 60 / 60 / 24 / 365;

                    // Calculate the average number of versions per year
                    double versionsPerYear = dependecyNumberTemp / yearsSinceCreation;

                    // Return the result rounded to one decimal place
                    double repoVersionsPerYear = Math.round(versionsPerYear * 10) / 10.0;

                    repoVersionCount.add(dependencyNumber.get(), dependecyNumberTemp);
                    String repoCreation = timeConverter(dependencyResponse.get("creation").getAsLong());

                    allComponentsInfoHTML.add("<div style='font-weight:600;font-size: x-large;'><i class='fa fa-cube'></i> <span id='repoName'>" + dependencyResponse.get("name").getAsString() + "</span><div style='float:right' id='classificationBadge" + dependencyNumber + "'> </div></div>" +
                            "<div><i class='fa fa-link'></i> Origin: <span id='repoUrl'>" + "<a href='" + dependencyResponse.get("url").getAsString().replaceAll("\"", "") + "' target='_blank'>" + dependencyResponse.get("url").getAsString() + "</a>" + "</span></div>" +
                            "<div><i class='far fa-calendar-alt'></i> Created: <span id='repoCreation'>" + repoCreation + "</span></div>" +
                            "<div><i class='fas fa-code-branch'></i> <span id='repoVersionCount'>" + repoVersionCount.get(dependencyNumber.get()) + "</span> versions | <span id='repoVersionsPerYear'>" + repoVersionsPerYear + "</span> versions / year</div>");


                    AtomicInteger repoVulnerabilityCount = new AtomicInteger();

                    String repoLicenses;
                    int repoAverageVulnerabilityPerVersion;


                    if (dependencyResponse.get("versions") != null && !dependencyResponse.get("versions").getAsJsonArray().isEmpty()) {
                        JsonArray versions = dependencyResponse.getAsJsonArray("versions");

                        // Trouver le dernier élément avec des licences (en supposant que le JSON est correctement structuré)

                        JsonObject latestKnownLicenses = null;
                        for (int i = versions.size() - 1; i >= 0; i--) {
                            JsonObject version = versions.get(i).getAsJsonObject();
                            if (version.getAsJsonArray("licenses") != null && version.getAsJsonArray("licenses").size() > 0) {
                                latestKnownLicenses = version.getAsJsonObject();
                                break;
                            }
                        }

                        if (latestKnownLicenses != null) {
                            JsonArray licenses = latestKnownLicenses.getAsJsonArray("licenses");
                            for (JsonElement licenseValue : licenses) {
                                JsonObject license = licenseValue.getAsJsonObject();
                                String compliance = license.get("compliance").getAsString();
                                String name = license.get("name").toString();
                                String contentToAdd = "";

                                switch (compliance) {
                                    case "compliant":
                                        contentToAdd = "<span class='badge bg-success' title='This license does comply with your organization policy. Contact your legal team for specific guidance.'>" + name + "</span> ";
                                        break;
                                    case "notCompliant":
                                        contentToAdd = "<span class='badge bg-danger' title='This license does not comply with your organization policy. Contact your legal team for specific guidance.'>" + name + "</span> ";
                                        break;
                                    case "partial":
                                        contentToAdd = "<span class='badge bg-warning' title='This license partially complies with your organization policy. Contact your legal team for specific guidance.'>" + name + "</span> ";
                                        break;
                                }

                                allComponentsInfoHTML.add("<div><i class='fas fa-gavel'></i> License(s): <span id='repoLicenses'>" + contentToAdd + "</span></div><br>");
                            }
                        }
                    }


                    int repoLastReleaseDate = dependencyResponse.getAsJsonArray("versions").get(0).getAsJsonObject().get("releaseDate").getAsInt();
                    AtomicInteger repoVersionVulnerabilityCriticalCount = new AtomicInteger();
                    AtomicInteger repoVersionVulnerabilityHighCount = new AtomicInteger();
                    AtomicInteger repoVersionVulnerabilityMediumCount = new AtomicInteger();
                    AtomicInteger repoVersionVulnerabilityLowCount = new AtomicInteger();
                    AtomicInteger repoVersionVulnerabilityAdvisoriesCount = new AtomicInteger();

                    AtomicInteger repoVersionVulnerabilityCriticalCurrent = new AtomicInteger();
                    AtomicInteger repoVersionVulnerabilityHighCurrent = new AtomicInteger();
                    AtomicInteger repoVersionVulnerabilityMediumCurrent = new AtomicInteger();
                    AtomicInteger repoVersionVulnerabilityLowCurrent = new AtomicInteger();
                    AtomicInteger repoVersionVulnerabilityAdvisoriesCurrent = new AtomicInteger();



                    long repoReleaseGap = (Instant.now().getEpochSecond()  - repoLastReleaseDate) / 1000 / 60 / 60 / 24 / 365;

                    if (repoReleaseGap > 1 && repoReleaseGap < 2) {
                        allComponentsInfoHTML.add("<div id=\"repoReleaseAlert\"><div class=\"alert alert-warning\" style=\"color: yellow; background-color: #333333; border-color: yellow;\" role=\"alert\"><i class=\"fas fa-exclamation-circle\"></i> No release for 12+ months</div></div>");
                    } else if (repoReleaseGap > 2) {
                        allComponentsInfoHTML.add("<div id=\"repoReleaseAlert\"><div class=\"alert alert-danger\" style=\"color: indianred; background-color: #333333; border-color: indianred;\" role=\"alert\"><i class=\"fas fa-exclamation-circle\"></i> No release for 24+ months</div></div>");
                    }

                    // LOOP ON VERSION

                    // Some dependency might have some undefined version because they aren't defined in any pom
                    // If it's the case Maven use the last version known on remote repository
                    // We'll do kind of the same by replacing the version by the latest analyzed version by highlight

                    String latestVersion;


                    if (requiredVersion.equals("dW5kZWZpbmVk")) {
                        JsonObject latestVersionObject = getLatestVersion(dependencyResponse.getAsJsonArray("versions"));

                        if (latestVersionObject != null && latestVersionObject.size() > 0) {
                            latestVersion = latestVersionObject.get("version").getAsString();
                        } else {
                            latestVersion = "";
                        }

                    } else {
                        latestVersion = "";
                    }

                    final ArrayList<String> repoVersionContainerHTML = new ArrayList<String>();

                    /************************************************************************
                     * ***************** DEPENDENCY VERSION *********************************
                     * **********************************************************************
                     */

                    if(dependencyResponse.getAsJsonArray("versions") != null) {
                        dependencyResponse.getAsJsonArray("versions").forEach(jsonElement -> {

                            JsonObject version;

                            if(jsonElement.getAsJsonObject() == null){
                                return;
                            }else{
                                version = jsonElement.getAsJsonObject();
                            }


                            // For each version we compute the counts of allowed and denied version to later defined the class (badge of the component)
                            // First itteration of the count Array


                            if (classificationAllow.isEmpty()) {
                                classificationAllow.add(dependencyNumber.get(), 0);
                            }
                            if (classificationDeny.isEmpty()) {
                                classificationDeny.add(dependencyNumber.get(), 0);
                            }

                            if (classificationAllow.size() > dependencyNumber.get() && version.get("classification") != null && version.get("classification").getAsString() != null && !version.get("classification").getAsString().isEmpty() &&  version.get("classification").getAsString().equals("allow")) {
                                classificationDeny.add(dependencyNumber.get(), classificationAllow.get(dependencyNumber.get()) + 1);
                            }
                            if (classificationDeny.size() > dependencyNumber.get() && version.get("classification") != null && version.get("classification").getAsString() != null && !version.get("classification").getAsString().isEmpty() &&  version.get("classification").getAsString().equals("deny")) {
                                classificationDeny.add(dependencyNumber.get(), classificationDeny.get(dependencyNumber.get()) + 1);
                            }

                            // Sage trigger


                            // Finding the specific version if any or the latest version if no version specified
                            if (version.get("version").getAsString().equals(requiredVersion) || (requiredVersion.equals("dW5kZWZpbmVk") && latestVersion != null && version.get("version").getAsString().equals(latestVersion))) {


                                repoVersionContainerHTML.add("<div id='repoVersionContainer'>" +
                                        "<div id='repoVersionInfo' style='display:block'>");

                                repoVersionContainerHTML.add("<h5><i class='fas fa-code-branch'></i> Version <span class='repoVersionName'>" + version.get("version") + "</span> information</h5>");
                                repoVersionContainerHTML.add("<div><i class='far fa-calendar-alt'></i> Released: <span id='repoVersionRelease'>" + timeConverter(version.get("releaseDate").getAsLong() / 1000) + "</span></div>");

                                // looping on licenses

                                if (version.get("licenses") != null && version.getAsJsonArray("licenses").size() > 0) {

                                    version.getAsJsonArray("licenses").forEach(jsonElement1 -> {

                                        if (jsonElement1.getAsJsonObject().get("compliance") != null &&  jsonElement1.getAsJsonObject().get("name") != null) {

                                            String compliance = jsonElement1.getAsJsonObject().get("compliance").getAsString();
                                            String name = jsonElement1.getAsJsonObject().get("name").getAsString();

                                            if(compliance.equals("compliant")) {
                                                var htmlToAdd = " <span class='badge bg-success' title='This license does comply with your organization policy. Contact your legal team for specific guidance.'>" + name + "</span> ";
                                                repoVersionContainerHTML.add("<div><i class='fas fa-gavel'></i> License(s): <span id='repoVersionLicenses'>" + htmlToAdd + "</span></div>");
                                            } else if (compliance.equals("notCompliant")) {
                                                var htmlToAdd = " <span class='badge bg-danger' title='This license does not comply with your organization policy. Contact your legal team for specific guidance.'>" + name + "</span> ";
                                                repoVersionContainerHTML.add("<div><i class='fas fa-gavel'></i> License(s): <span id='repoVersionLicenses'>" + htmlToAdd + "</span></div>)");
                                            } else if (compliance.equals("partiallyCompliant")) {
                                                var htmlToAdd = " <span class='badge bg-warning' title='This license partially complies with your organization policy. Contact your legal team for specific guidance.'>" + name + "</span> ";
                                                repoVersionContainerHTML.add("<div><i class='fas fa-gavel'></i> License(s): <span id='repoVersionLicenses'>" + htmlToAdd + "</span></div>");
                                            }
                                        }

                                    });
                                }


                                repoVersionContainerHTML.add("<br><div><i class='fa fa-bug'></i> Version <span class='repoVersionName'> " + version.get("version") + "</span> Vulnerabilities</div>");
                                repoVersionContainerHTML.add("</div></div>");
                            }



                            if (version.getAsJsonArray("basicVulnerabilities") != null && !version.getAsJsonArray("basicVulnerabilities").isEmpty()) {

                                // List of vulnerability type for the current version
                                ArrayList<String> listVulnerabilityCritical = new ArrayList<>();
                                ArrayList<String> listVulnerabilityHigh = new ArrayList<String>();
                                ArrayList<String> listVulnerabilityMedium = new ArrayList<String>();
                                ArrayList<String> listVulnerabilityLow = new ArrayList<String>();
                                ArrayList<String> listVulnerabilityUnknown = new ArrayList<String>();



                                version.getAsJsonArray("basicVulnerabilities").forEach(jsonElement1 -> {

                                    String severity = jsonElement1.getAsJsonObject().get("severity").getAsString();
                                    String name = jsonElement1.getAsJsonObject().get("name").getAsString();

                                    repoVulnerabilityCount.getAndIncrement();



                                    // COUNT VULNERABILITIES OF THE CURRENT VERSION
                                    if ( version.get("version").getAsString().equals(requiredVersion) || ((requiredVersion.equals("dW5kZWZpbmVk") && latestVersion != null && !latestVersion.isEmpty() && version.get("version").getAsString().equals(latestVersion)))) {


                                        // Create a list for each vulnerability type and then ad it to table

                                        if (severity.equals("CRITICAL")) {
                                            listVulnerabilityCritical.add("<a href=\"https://nvd.nist.gov/vuln/detail/" + name + "\" target=\"_blank\">" + name + "</a>");
                                            repoVersionVulnerabilityCriticalCount.getAndIncrement();
                                        }

                                        if (severity.equals("HIGH")) {
                                            listVulnerabilityHigh.add("<a href=\"https://nvd.nist.gov/vuln/detail/" + name + "\" target=\"_blank\">" + name + "</a>");
                                            repoVersionVulnerabilityHighCount.getAndIncrement();
                                        }

                                        if (severity.equals("MEDIUM")) {
                                            listVulnerabilityMedium.add("<a href=\"https://nvd.nist.gov/vuln/detail/" + name + "\" target=\"_blank\">" + name + "</a>");
                                            repoVersionVulnerabilityMediumCount.getAndIncrement();
                                        }

                                        if (severity.equals("LOW")) {
                                            listVulnerabilityLow.add("<a href=\"https://nvd.nist.gov/vuln/detail/" + name + "\" target=\"_blank\">" + name + "</a>");
                                            repoVersionVulnerabilityLowCount.getAndIncrement();
                                        }
                                        if (severity.equals("UNKNOWN")) {
                                            listVulnerabilityUnknown.add("<a href=\"https://nvd.nist.gov/vuln/detail/" + name + "\" target=\"_blank\">" + name + "</a>");
                                            repoVersionVulnerabilityAdvisoriesCount.getAndIncrement();
                                        }

                                    }


                                });


                                // If vulnerability are found for the current version, we display a table of vulnerability
                                String vulnerabilityTable = "";


                                if (!listVulnerabilityCritical.isEmpty() || !listVulnerabilityHigh.isEmpty() || listVulnerabilityMedium.size() > 0 || listVulnerabilityLow.size() > 0 || listVulnerabilityUnknown.size() > 0) {
                                    // Creation of the table Headers
                                    vulnerabilityTable = ("<br><table class=\"VulnerabilityTable\">" +
                                            "<thead>" +
                                            "<tr>                   " +
                                            "<th class=\"thCritical\"> <span class=\"badge bg-dark\" style=\"margin-left:1px; margin-right: 1px;\">CRITICAL</span>  </th>      " +
                                            "<th class=\"thHigh\"> <span class=\"badge bg-danger\" style=\"margin-left:1px; margin-right: 1px;\">HIGH</span> </th>   " +
                                            "<th class=\"thMedium\"> <span class=\"badge bg-warning\" style=\"margin-left:1px; margin-right: 1px;\">MEDIUM</span> </th>   " +
                                            "<th class=\"thLow\"> <span class=\"badge bg-light\" style=\"color:#BBB;margin-left:1px; margin-right: 1px;\">LOW</span> </th>   " +
                                            "<th class=\"thUnknow\"> <span class=\"badge bg-white\" style=\"color:rgb(42, 38, 38);margin-left:1px; margin-right: 1px;\">ADVISORIES</span> </th>   " +
                                            "</tr>                 " +
                                            "</thead>" +
                                            "<tbody>");

                                    int iteratorVulnerabilityList = 0;


                                    while (listVulnerabilityCritical.size() > iteratorVulnerabilityList || listVulnerabilityHigh.size() > iteratorVulnerabilityList
                                            || listVulnerabilityMedium.size() > iteratorVulnerabilityList || listVulnerabilityLow.size() > iteratorVulnerabilityList
                                            || listVulnerabilityUnknown.size() > iteratorVulnerabilityList) {

                                        vulnerabilityTable += "<tr>";
                                        // Adding line entry for each type of vulnerability
                                        if (listVulnerabilityCritical.size() > iteratorVulnerabilityList) {
                                            vulnerabilityTable += "<td>" + listVulnerabilityCritical.get(iteratorVulnerabilityList) + "</td>";
                                        } else {
                                            vulnerabilityTable += "<td></td>";
                                        }

                                        if (listVulnerabilityHigh.size() > iteratorVulnerabilityList) {
                                            vulnerabilityTable += "<td>" + listVulnerabilityHigh.get(iteratorVulnerabilityList) + "</td>";
                                        } else {
                                            vulnerabilityTable += "<td></td>";
                                        }

                                        if (listVulnerabilityMedium.size() > iteratorVulnerabilityList) {
                                            vulnerabilityTable += "<td>" + listVulnerabilityMedium.get(iteratorVulnerabilityList) + "</td>";
                                        } else {
                                            vulnerabilityTable += "<td></td>";
                                        }

                                        if (listVulnerabilityLow.size() > iteratorVulnerabilityList) {
                                            vulnerabilityTable += "<td>" + listVulnerabilityLow.get(iteratorVulnerabilityList) + "</td>";
                                        } else {
                                            vulnerabilityTable += "<td></td>";
                                        }

                                        if (listVulnerabilityUnknown.size() > iteratorVulnerabilityList) {
                                            vulnerabilityTable += "<td>" + listVulnerabilityUnknown.get(iteratorVulnerabilityList) + "</td>";
                                        } else {
                                            vulnerabilityTable += "<td></td>";
                                        }

                                        vulnerabilityTable += "</tr>";

                                        iteratorVulnerabilityList++;

                                    }


                                    // Close table

                                    vulnerabilityTable += "</tobdy>";
                                    vulnerabilityTable += "</table><br>";

                                }


                                repoVersionContainerHTML.add(vulnerabilityTable);

                            }


                            // Retriving workspace global number of vulnerability then reset the repoVersion manually for atomic Integer
                            workspaceVulnerabilityCriticalCount.getAndAdd(repoVersionVulnerabilityCriticalCount.get());
                            repoVersionVulnerabilityCriticalCurrent.getAndAdd(repoVersionVulnerabilityCriticalCount.get());
                            repoVersionVulnerabilityCriticalCount.set(0);
                            workspaceVulnerabilityHighCount.getAndAdd(repoVersionVulnerabilityHighCount.get());
                            repoVersionVulnerabilityHighCurrent.getAndAdd(repoVersionVulnerabilityHighCount.get());
                            repoVersionVulnerabilityHighCount.set(0);
                            workspaceVulnerabilityMediumCount.getAndAdd(repoVersionVulnerabilityMediumCount.get());
                            repoVersionVulnerabilityMediumCurrent.getAndAdd(repoVersionVulnerabilityMediumCount.get());
                            repoVersionVulnerabilityMediumCount.set(0);
                            workspaceVulnerabilityLowCount.getAndAdd(repoVersionVulnerabilityLowCount.get());
                            repoVersionVulnerabilityLowCurrent.getAndAdd(repoVersionVulnerabilityLowCount.get());
                            repoVersionVulnerabilityLowCount.set(0);
                            workspaceVulnerabilityAdvisoriesCount.getAndAdd(repoVersionVulnerabilityAdvisoriesCount.get());
                            repoVersionVulnerabilityAdvisoriesCurrent.getAndAdd(repoVersionVulnerabilityAdvisoriesCount.get());
                            repoVersionVulnerabilityAdvisoriesCount.set(0);

                        });
                    }

                    /************************************************************************
                     * ************************ END VERSION *********************************
                     * **********************************************************************
                     */

                    // closing div

                    allComponentsInfoHTML.add(String.join("", repoVersionContainerHTML));

                    allComponentsInfoHTML.add("<h5><i class='fa fa-bug'></i> Component Vulnerabilities</h5>" +
                            "<h5>" +
                            "<span class='badge bg-dark' style='margin-left:1px; margin-right: 1px;'>CRITICAL <span id='vulnerabilityCriticalCount' class='badge bg-light text-dark'>" + repoVersionVulnerabilityCriticalCurrent.get() + "</span></span>" +
                            "<span class='badge bg-danger' style='margin-left:1px; margin-right: 1px;'>HIGH <span id='vulnerabilityHighCount' class='badge bg-light text-dark' '>" + repoVersionVulnerabilityHighCurrent.get() + "</span></span>" +
                            "<span class='badge bg-warning' style='margin-left:1px; margin-right: 1px;'>MEDIUM <span id='vulnerabilityMediumCount' class='badge bg-light text-dark' >" + repoVersionVulnerabilityMediumCurrent.get() + "</span></span>" +
                            "<span class='badge bg-light' style='color:#BBB; margin-left:1px; margin-right: 1px;'>LOW <span id='vulnerabilityLowCount' class='badge bg-light' style='color:#BBB'>" + repoVersionVulnerabilityLowCurrent.get() + "</span></span>" +
                            "<span class='badge bg-white' style='color:rgb(42, 38, 38);margin-left:1px; margin-right: 1px;'>ADVISORIES <span id='vulnerabilityAdvisoriesCount' class='badge bg-light' style='color:#BBB'>" + repoVersionVulnerabilityAdvisoriesCurrent.get() + "</span></span>" +
                            "</h5>");

                    /* Setting current counter to zero */


                    repoVersionVulnerabilityCriticalCurrent.set(0);
                    repoVersionVulnerabilityHighCurrent.set(0);
                    repoVersionVulnerabilityMediumCurrent.set(0);
                    repoVersionVulnerabilityLowCurrent.set(0);
                    repoVersionVulnerabilityAdvisoriesCurrent.set(0);


                    repoAverageVulnerabilityPerVersion = repoVulnerabilityCount.get() / repoVersionCount.get(dependencyNumber.get());

                    allComponentsInfoHTML.add("<div id='repoTotalCVEs'><i class='fa fa-bug'></i> Total Vulnerabilities: <span id='repoVulnerabilityCount'>" + repoVulnerabilityCount + "  (" + repoAverageVulnerabilityPerVersion + " / version)" + "</span></div>");
                    allComponentsInfoHTML.add("<br><hr style=\"height: 3px; background-color: #ffffff !important;\"><br>");
                    dependencyNumber.getAndIncrement();


        });

        Element elementUpdate = dom.getElementById("allComponents");

        if (elementUpdate != null) {
            elementUpdate.append(String.join("", allComponentsInfoHTML));
            content = new StringBuilder(dom.html());
        }

        // Once the whole analyze is done we can retrive the count results of each depencency and create a global graph for each specific worspace analysis


        String workspaceVulnerabilityCountDisplay = "<h3 style='font-weight:800' >" + workspaceName + "</h3>";
        workspaceVulnerabilityCountDisplay += "<h6><a id='pathExplorer'><i>Configuration in " + pathOfConfigFile + "</i></a></h6>";
        workspaceVulnerabilityCountDisplay += "<h5>Number of dependency found : " + listDependencyResults.size() + "</h5>";



        workspaceVulnerabilityCountDisplay += "<h5><i class='fa fa-bug'></i> Component Vulnerabilities Count</h5>" +
                "<h5 class='componentVulneraibilityCount' >" +
                "<span class='badgeVulneraCount badge bg-dark' style='margin-right: 3px; padding: .35em 0.65em; border-width: 2px; border-style: solid; border-radius: 8px; border-color: white;font-size: large;margin-left:1px; margin-right: 1px;'><span id='vulnerabilityCriticalCount' class='badge ' style='font-weight: 800;font-size: 30px;'>" + workspaceVulnerabilityCriticalCount + "</span> <br>CRITICAL </span>&nbsp;" +
                "<span class='badgeVulneraCount badge bg-danger' style='margin-right: 3px;padding: .35em 0.65em;border-width: 2px; border-style: solid; border-radius: 8px; border-color: white;font-size: large;margin-left:1px; margin-right: 1px;'><span id='vulnerabilityHighCount' class='badge' style='font-weight: 800;font-size: 30px;'>" + workspaceVulnerabilityHighCount + "</span> <br> HIGH </span>&nbsp;" +
                "<span class='badgeVulneraCount badge bg-warning' style='margin-right: 3px;padding: .35em 0.65em;border-width: 2px; border-style: solid; border-radius: 8px; border-color: white;font-size: large;margin-left:1px; margin-right: 1px;'> <span id='vulnerabilityMediumCount' class='badge ' style='font-weight: 800;font-size: 30px;'>" + workspaceVulnerabilityMediumCount + "</span> <br>MEDIUM </span>&nbsp;" +
                "<span class='badgeVulneraCount badge bg-light' style='margin-right: 3px;padding: .35em 0.65em;border-width: 2px; border-style: solid; border-radius: 8px; border-color: black;font-size: large;color:#BBB; margin-left:1px; margin-right: 1px;'> <span id='vulnerabilityLowCount' class='badge ' style='font-weight: 800;font-size: 30px;color:#BBB'>" + workspaceVulnerabilityLowCount + "</span> <br> LOW</span>&nbsp;" +
                "<span class='badgeVulneraCount badge bg-white' style='margin-right: 3px;padding: .35em 0.65em;border-width: 2px; border-style: solid; border-radius: 8px; border-color: black;font-size: large;color:rgb(42, 38, 38);margin-left:1px; margin-right: 1px;'> <span id='vulnerabilityAdvisoriesCount' class='badge' style='font-weight: 800;font-size: 30px;color:#BBB'>" + workspaceVulnerabilityAdvisoriesCount + "</span> <br>ADVISORIES</span>" +

                "</h5>";



        Element elementTempo = dom.getElementById("dashbordVulnerabilityCount");

        if (elementTempo != null) {
            elementTempo.append(workspaceVulnerabilityCountDisplay);
            content = new StringBuilder(dom.html());
        }


        /******************************************************************
         * *************** ALLOW DENY STATUS PART *************************
         * ***************************************************************
         */


        for (int i = 0; i < listDependencyResults.size() - 1; i++) {
            
                if (i < classificationAllow.size() && i < repoVersionCount.size() && !classificationAllow.isEmpty() && !repoVersionCount.isEmpty() && repoVersionCount.get(i) != null && Objects.equals(classificationAllow.get(i), repoVersionCount.get(i))) {
                    // dom.getElementById("mainContentColor").style.backgroundColor = '#d2fcd7';
                    Objects.requireNonNull(dom.getElementById("classificationBadge" + i)).append("<h4><span class=\"badge bg-success\">ALLOWED</span></h4>");
                }
                else if (i < classificationDeny.size() && !classificationDeny.isEmpty() && i < repoVersionCount.size() && !repoVersionCount.isEmpty() && repoVersionCount.get(i) != null && Objects.equals(classificationDeny.get(i), repoVersionCount.get(i))) {
                    //dom.getElementById("mainContentColor").style.backgroundColor = '#db8c94';
                    dom.getElementById("classificationBadge" + i).append("<h4><span class=\"badge bg-danger\">DENIED</span></h4>");
                }
                else if (i < classificationDeny.size() && i < classificationAllow.size() &&  !classificationAllow.isEmpty() && !classificationDeny.isEmpty() && classificationAllow.get(i)  > 0 && classificationDeny.get(i)  > 0 && classificationAllow.get(i)  > classificationDeny.get(i) ) {
                    //dom.getElementById("mainContentColor").style.backgroundColor = '#faeab1';
                    dom.getElementById("classificationBadge" + i).append("<h4><span class=\"badge bg-warning\">PARTIALLY ALLOWED</span></h4>");
                }
                else if (i < classificationDeny.size() && i < classificationAllow.size() &&  !classificationAllow.isEmpty() && !classificationDeny.isEmpty() && classificationAllow.get(i)  > 0 && classificationDeny.get(i)  > 0 && classificationDeny.get(i)  > classificationAllow.get(i) ) {
                    //dom.getElementById("mainContentColor").style.backgroundColor =  '#faeab1';
                    dom.getElementById("classificationBadge" + i).append("<h4><span class=\"badge bg-warning\">PARTIALLY DENIED</span></h4>");
                }
                else {
                    //dom.getElementById("mainContentColor").style.backgroundColor = '#F0F0F0';
                    if(dom.getElementById("classificationBadge" + i) != null ){
                        dom.getElementById("classificationBadge" + i).append("<span class=\"badge bg-dark\">UNKNOWN STATUS</span>");
                    }
                }

        }

        content = new StringBuilder(dom.html());
        return content;

    }


    // Placeholder methods based on the given TypeScript code.
    private String timeConverter(long timestamp) {

        // Convert Unix timestamp to milliseconds
        long milliseconds = timestamp * 1000;

        // Create a date instance using the milliseconds
        Date date = new Date(milliseconds);

        // Format the date instance to the desired format
        SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy HH:mm:ss");

        // This step is optional, but you can set a timezone if you want
        sdf.setTimeZone(TimeZone.getDefault());

        // Return the formatted date-time string
        return sdf.format(date);
    }




    private JsonObject getLatestVersion(JsonArray versions) {

        JsonObject latestVersion =  versions.get(0).getAsJsonObject();

        for (int i = 1; i < versions.size(); i++) {
            Date one = new Date(versions.get(i).getAsJsonObject().get("releaseDate").getAsLong());
            Date two =  new Date(latestVersion.get("releaseDate").getAsInt());
            if (one.after(two)) {
                latestVersion = versions.get(i).getAsJsonObject();
            }
        }

        return latestVersion;
    }

    public String getRepoVersion(JsonObject resultRequest) {

        String url = resultRequest.get("url").getAsString();
        String repoVersion = null;
        String repo = "";
        String repoName = "";
        String repoNameBase64 = "";
        String repoVersionBase64 = "";
        String repoGroupId;
        String repoArtifactId;

        if (url.contains("npmjs.com/package/")) {
            repo = url.split("https://www.npmjs.com/package/")[1];
            repoName = repo.split("/v/")[0];
            repoVersion = repo.split("/v/")[1];
            repoNameBase64 = Base64.getEncoder().encodeToString((repoName + ".npm").getBytes());
            repoVersionBase64 = Base64.getEncoder().encodeToString(repoVersion.getBytes());
        } else if (url.contains("github.com")) {
            repo = url.split("github.com/")[1];
            repoVersion = repo.split("releases/tag/")[1];
        } else if (url.contains("gitlab.com")) {
            repo = url.split("gitlab.com/")[1];
            repoVersion = repo.split("-/tags/")[1];
        } else if (url.contains("nuget.org/packages/")) {
            repo = url.split("https://www.nuget.org/packages/")[1];
            repoVersion = repo.split("/")[1];
        } else if (url.contains("packagist.org/packages/")) {
            repo = url.split("packagist.org/packages/")[1];
            repoVersion = repo.split("#")[1];
        } else if (url.contains("search.maven.org/artifact/")) {
            repo = url.split("search.maven.org/artifact/")[1];
            repoVersion = repo.split("/")[2];
        } else if (url.contains("mvnrepository.com/artifact/")) {
            repo = url.split("mvnrepository.com/artifact/")[1];
            repoVersion = repo.split("/")[2];
        } else if (url.contains("repo1.maven.org/maven2/")) {
            repo = url.split("repo1.maven.org/maven2/")[1];
            String[] repoParts = repo.split("/");
            int countSlashes = repoParts.length - 2;
            if (countSlashes >= 2) { // Ensure that there are enough parts before accessing
                repoGroupId = repoParts[0] + '.' + repoParts[1];
                repoArtifactId = repoParts[countSlashes - 1];
                repoName = repoGroupId + ':' + repoArtifactId;
                repoVersion = repoParts[countSlashes];
                repoNameBase64 = Base64.getEncoder().encodeToString(repoName.getBytes());
                repoVersionBase64 = Base64.getEncoder().encodeToString(repoVersion.getBytes());
            } else {
                // Handle the unexpected format gracefully
                System.err.println("Unexpected URL format: " + url);
                // You might want to log the error or do some other error handling here
            }
        }else if (url.contains("rubygems.org/gems/")) {
            repo = url.split("https://rubygems.org/gems/")[1];
            repoVersion = repo.split("/versions")[1];
        } else if (url.contains("pypi.org/project/")) {
            repo = url.split("https://pypi.org/project/")[1];
            repoVersion = repo.split("/")[1];
        }
        return repoVersion;
    }


}
