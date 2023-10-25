package com.casthighlightsca.extension.analyse;


import org.eclipse.aether.collection.DependencyManager;
import org.json.JSONException;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;


import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;


public class Analyser {

    private List<String> configFiles = new ArrayList<>();

    /**
     * This function will search recursively for json and pom files within provided directories.
     * @param paths List of directories to be scanned.
     * @return List of found configuration files.
     */
    public List<String> findConfigFile(List<String> paths) {
        for (String path : paths) {
            try {
                configFiles.addAll(Files.walk(Paths.get(path))
                        .filter(Files::isRegularFile)
                        .map(p -> p.toString())
                        .filter(p -> p.endsWith(".json") || p.endsWith("pom.xml"))
                        .collect(Collectors.toList()));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return configFiles;
    }



    private static final Logger logger = Logger.getLogger(DependencyManager.class.getName());

    public List<Dependency> getDependencies(String pomPath) throws Exception {
        // Load the main POM
        Document mainPom = loadXmlDocument(pomPath);
        List<Dependency> dependencies = extractDependencies(mainPom);

        // Log the initial list of dependencies
        logDependencies(dependencies);

        // Resolve dependencies from local POMs
        List<Dependency> localResolvedDependencies = resolveLocalDependencies(dependencies, pomPath);
        logUpdatedDependencies(localResolvedDependencies, dependencies);

        // Resolve dependencies from remote POMs
        List<Dependency> remoteResolvedDependencies = resolveRemoteDependencies(localResolvedDependencies, pomPath);
        logUpdatedDependencies(remoteResolvedDependencies, localResolvedDependencies);

        // Compile and log the final list of dependencies
        List<Dependency> finalDependencies = compileDependencies(remoteResolvedDependencies);
        logDependencies(finalDependencies);
        logMissingVersions(finalDependencies);

        return finalDependencies;
    }
    private List<Dependency> extractDependencies(Document doc) {
        NodeList dependencyNodes = doc.getElementsByTagName("dependency");
        List<Dependency> dependencies = new ArrayList<>();
        for (int i = 0; i < dependencyNodes.getLength(); i++) {
            Element dependencyElement = (Element) dependencyNodes.item(i);
            String groupId = getXmlElementContent(dependencyElement, "groupId");
            String artifactId = getXmlElementContent(dependencyElement, "artifactId");
            String version = getXmlElementContent(dependencyElement, "version");
            dependencies.add(new Dependency(groupId, artifactId, version));
        }
        dependencies = updateDependencyVersionsFromProperties(dependencies, doc);
        return dependencies;
    }

    public List<Dependency> extractDependenciesFromJson(String pathToJsonFile) {
        try {
            String content = new String(Files.readAllBytes(Paths.get(pathToJsonFile)));
            JsonElement domElement =  JsonParser.parseString(content.toString().replaceAll("\r\n",""));
            JsonObject dom;

            if(!domElement.isJsonObject()){
                return null;
            }else{
                dom = domElement.getAsJsonObject();
            }


            List<Dependency> dependencies = new ArrayList<>();

            JsonObject deps = new JsonObject();
            JsonObject devDeps = new JsonObject();

            // Extract 'dependencies' and 'devDependencies'
            if(dom.get("dependencies").isJsonObject()){
                deps = dom.get("dependencies").getAsJsonObject();
            }

            if(dom.get("devDependencies").isJsonObject()){
                devDeps = dom.get("devDependencies").getAsJsonObject();
            }



            List<Dependency> finalDependencies = dependencies;


            if(deps != null && deps.size() > 0) {
                for(Map.Entry<String, JsonElement> key : deps.entrySet()) {
                    finalDependencies.add(new Dependency("", key.getKey(), key.getValue().toString()));
                };
            }

            if(devDeps != null && devDeps.size() > 0) {
                for(Map.Entry<String, JsonElement> key : devDeps.entrySet()) {
                    finalDependencies.add(new Dependency("", key.getKey(), key.getValue().toString()));
                };
            }

            // Handle version resolution if versions are specified using variables
            // You would need to implement a method for this similar to 'updateDependencyVersionsFromProperties'
            // for your POM dependencies
            //TODO
            //dependencies = updateDependencyVersionsFromJsonProperties(dependencies, jsonObject);

            return dependencies;

        } catch(Exception e) {
            System.err.println("Error parsing package.json" + e);
            return null;
        }
    }

    // Méthode pour résoudre des versions comme $var_version
    private String resolveVersion(String version, JSONObject jsonObject) {
        // Si la version commence par $, recherchez-la dans le jsonObject
        if (version.startsWith("$")) {
            String variableName = version.substring(1);
            if (jsonObject.has(variableName)) {
                try {
                    return jsonObject.getString(variableName);
                } catch (JSONException e) {
                    System.out.println("Error resolving version" + e);
                }
            }
        }
        // Ajoutez ici une logique pour résoudre la version à partir de sources externes si nécessaire
        // Pour l'instant, renvoyez simplement la version
        return version;
    }




    private List<Dependency> resolveLocalDependencies(List<Dependency> dependencies, String pomPath) throws Exception {
        String currentPomPath = pomPath;
        Set<String> visitedPoms = new HashSet<>();

        while (!dependenciesNeedingResolution(dependencies).isEmpty()) {
            if (visitedPoms.contains(currentPomPath)) {
                break;
            }
            visitedPoms.add(currentPomPath);

            Path parentPomPath = Paths.get(currentPomPath).getParent().getParent().resolve("pom.xml");
            if (Files.exists(parentPomPath)) {
                currentPomPath = parentPomPath.toString();
                Document parentPom = loadXmlDocument(currentPomPath);

                // Update the versions from the properties tag
                dependencies = updateDependencyVersionsFromProperties(dependencies, parentPom);

                dependencies = updateDependencyVersionsFromParent(dependencies, parentPom);
            } else {
                break;
            }
        }
        return dependencies;
    }

    private List<Dependency> updateDependencyVersionsFromProperties(List<Dependency> dependencies, Document pom) {
        NodeList propertiesList = pom.getElementsByTagName("properties");

        if (propertiesList.getLength() > 0) {
            Node properties = propertiesList.item(0);
            NodeList propertyList = properties.getChildNodes();

            for (Dependency dep : dependencies) {
                /*  Check for defined properties   */
                if (dep.getVersion() != null && dep.getVersion().startsWith("${") && dep.getVersion().endsWith("}")) {
                    String propertyName = dep.getVersion().substring(2, dep.getVersion().length() - 1);
                    for (int i = 0; i < propertyList.getLength(); i++) {
                        Node property = propertyList.item(i);
                        if (property.getNodeName().equals(propertyName)) {
                            dep.setVersion(property.getTextContent());
                            break;
                        }
                    }

                }

            }
        }


        return dependencies;
    }


    private List<Dependency> resolveRemoteDependencies(List<Dependency> dependencies, String pomPath) throws Exception {
        Document remoteParentPom = getRemoteParentPom(loadXmlDocument(pomPath), pomPath, 0);
        if (remoteParentPom != null) {
            dependencies = updateDependencyVersionsFromProperties(dependencies, remoteParentPom);
            dependencies = updateDependencyVersionsFromParent(dependencies, remoteParentPom);
        }
        return dependencies;
    }

    public Document getRemoteParentPom(Document doc, String localPath, int depth) {


        int MAX_RECURSION_DEPTH = 10;
        System.out.println("depth : " + depth);
        if (depth >= MAX_RECURSION_DEPTH) {
            System.out.println("Maximum recursion depth reached, stopping.");
            return null;
        }

        NodeList parentNodes = doc.getElementsByTagName("parent");
        if (parentNodes.getLength() > 0) {
            File localParentPomFile = getParentPomFile(localPath);
            if (localParentPomFile.exists()) {
                try {
                    Document localDoc = loadXmlDocument(localParentPomFile.getAbsolutePath());
                    Document nextDoc = getRemoteParentPom(localDoc, localParentPomFile.getAbsolutePath(), depth + 1);
                    return (nextDoc != null) ? nextDoc : localDoc;
                } catch (Exception e) {
                    System.out.println("Error reading local pom from " + localParentPomFile.getAbsolutePath() + " \n " + e);
                }
            } else {
                String groupId = extractTagValue(doc, "parent", "groupId").replace(".", "/");
                String artifactId = extractTagValue(doc, "parent", "artifactId");
                String version = extractTagValue(doc, "parent", "version");
                String url = String.format("https://repo1.maven.org/maven2/%s/%s/%s/%s-%s.pom", groupId, artifactId, version, artifactId, version);
                try {
                    System.out.println("Dependencies remote try with url :" + url);
                    String xmlContent = Jsoup.connect(url).ignoreContentType(true).execute().body();
                    Document results = convertStringToDocument(xmlContent);
                    return results;
                } catch (IOException e) {
                    System.out.printf("Failed to fetch or parse the XML from URL: %s. Error: %s", url, e.getMessage());
                }
            }
        }
        return null;
    }

    public static File getParentPomFile(String childPomPath) {
        File childPom = new File(childPomPath);
        File parentDirectory = childPom.getParentFile().getParentFile(); // Go up two levels
        File parentPom = new File(parentDirectory, "pom.xml");
        return parentPom;
    }

    private String extractTagValue(Document doc, String parentTagName, String tagName) {
        NodeList childNodes;
        if (parentTagName != null) {
            NodeList parentNodes = doc.getElementsByTagName(parentTagName);
            if (parentNodes.getLength() == 0) return "";
            Element parentElement = (Element) parentNodes.item(0);
            childNodes = parentElement.getElementsByTagName(tagName);
        } else {
            childNodes = doc.getElementsByTagName(tagName);
        }
        if (childNodes.getLength() > 0) {
            return childNodes.item(0).getTextContent();
        }
        return "";
    }

    private Document convertStringToDocument(String xmlStr) {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder;
        try {
            builder = factory.newDocumentBuilder();
            return builder.parse(new ByteArrayInputStream(xmlStr.getBytes()));
        } catch (Exception e) {
            System.out.printf("Error converting string to XML document", e);
        }
        return null;
    }

    private Document loadXmlDocument(String filePath) {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder;
        try {
            builder = factory.newDocumentBuilder();
            return builder.parse(new File(filePath));
        } catch (Exception e) {
            System.out.printf("Error loading XML document from file", e);
        }
        return null;
    }


    private List<Dependency> updateDependencyVersionsFromParent(List<Dependency> dependencies, Document parentDoc) {
        NodeList dependencyNodes = parentDoc.getElementsByTagName("dependency");
        for (int i = 0; i < dependencyNodes.getLength(); i++) {
            Element dependencyElement = (Element) dependencyNodes.item(i);
            String groupId = getXmlElementContent(dependencyElement, "groupId");
            String artifactId = getXmlElementContent(dependencyElement, "artifactId");
            String version = getXmlElementContent(dependencyElement, "version");

            for (Dependency dep : dependencies) {
                if (dep.getGroupId().equals(groupId) && dep.getArtifactId().equals(artifactId) && (dep.getVersion() == null || dep.getVersion().isEmpty())) {
                    dep.setVersion(version);
                }
            }
        }

        dependencies = updateDependencyVersionsFromProperties(dependencies, parentDoc);

        return dependencies;
    }

    private  String getXmlElementContent(Element parent, String tagName) {
        if (parent.getElementsByTagName(tagName).getLength() > 0) {
            return parent.getElementsByTagName(tagName).item(0).getTextContent();
        }
        return null;
    }

    // Return a list of dependencies that need version resolution
    private List<Dependency> dependenciesNeedingResolution(List<Dependency> dependencies) {
        return dependencies.stream()
                .filter(dep -> dep.getVersion() == null || dep.getVersion().isEmpty())
                .collect(Collectors.toList());
    }

    // Log the details of all dependencies provided
    private void logDependencies(List<Dependency> dependencies) {
        System.out.println("List of all dependencies:");
        for (int i = 0; i < dependencies.size(); i++) {
            Dependency dep = dependencies.get(i);
            System.out.printf("Dependency %d: artifactId: %s, groupId: %s, version: %s%n", i + 1, dep.getArtifactId(), dep.getGroupId(), dep.getVersion());
        }
    }

    // Log dependencies that are missing versions
    private void logMissingVersions(List<Dependency> dependencies) {
        System.out.println("Dependencies with missing versions:");
        for (int i = 0; i < dependencies.size(); i++) {
            Dependency dep = dependencies.get(i);
            if (dep.getVersion() == null || dep.getVersion().isEmpty()) {
                System.out.printf("Dependency %d: artifactId: %s, groupId: %s%n", i + 1, dep.getArtifactId(), dep.getGroupId());
            }
        }
    }

    private List<Dependency> compileDependencies(List<Dependency> dependencies) {
        // Deduplicate dependencies. If two dependencies have the same groupId and artifactId,
        // we keep the one with the non-null version. If both have non-null versions, it's a conflict.
        Map<String, Dependency> dedupedDependenciesMap = new HashMap<>();
        for (Dependency dependency : dependencies) {
            String key = dependency.getGroupId() + ":" + dependency.getArtifactId();
            if (dedupedDependenciesMap.containsKey(key)) {
                Dependency existingDep = dedupedDependenciesMap.get(key);
                if (existingDep.getVersion() == null && dependency.getVersion() != null) {
                    dedupedDependenciesMap.put(key, dependency);
                }
                // TODO: Handle conflicts where both versions are non-null.
            } else {
                dedupedDependenciesMap.put(key, dependency);
            }
        }

        // Sort the dependencies by groupId and artifactId for better readability
        List<Dependency> finalDependencies = new ArrayList<>(dedupedDependenciesMap.values());
        finalDependencies.sort(Comparator.comparing(Dependency::getGroupId)
                .thenComparing(Dependency::getArtifactId));

        return finalDependencies;
    }

    // Log dependencies that had their versions updated
    private void logUpdatedDependencies(List<Dependency> originalDeps, List<Dependency> updatedDeps) {
        System.out.println("Dependencies with updated versions:");
        for (int i = 0; i < originalDeps.size(); i++) {
            Dependency original = originalDeps.get(i);
            Dependency updated = updatedDeps.get(i);
            if (!Objects.equals(original.getVersion(), updated.getVersion())) {
                System.out.printf("Dependency: artifactId: %s, groupId: %s, Original Version: %s, Updated Version: %s%n",
                        updated.getArtifactId(), updated.getGroupId(), original.getVersion(), updated.getVersion());
            }
        }
    }


}


