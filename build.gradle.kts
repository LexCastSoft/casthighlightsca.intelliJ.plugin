plugins {
    id("java")
    id("org.jetbrains.intellij") version "1.15.0"
}

group = "com.casthighlightsca"
version = "1.0"

repositories {
    mavenCentral()
}


// Configure Gradle IntelliJ Plugin
// Read more: https://plugins.jetbrains.com/docs/intellij/tools-gradle-intellij-plugin.html
intellij {
    pluginName.set("casthighlightsca")
    version.set("2022.2.5")
    type.set("IC") // Target IDE Platform
    plugins.set(listOf())
}

tasks {
    // Set the JVM compatibility versions
    withType<JavaCompile> {
        sourceCompatibility = "11"
        targetCompatibility = "11"
    }

    patchPluginXml {
        sinceBuild.set("222")
        untilBuild.set("233.*")
    }

    signPlugin {
        certificateChain.set(System.getenv("CERTIFICATE_CHAIN"))
        privateKey.set(System.getenv("PRIVATE_KEY"))
        password.set(System.getenv("PRIVATE_KEY_PASSWORD"))
    }

    publishPlugin {
        token.set(System.getenv("PUBLISH_TOKEN"))
    }

    runIde {
        jbrVersion.set("17.0.8.1b1070.2")
        jbrVariant.set("jcef")
    }

}

dependencies{
    implementation ("com.google.code.gson:gson:2.8.8")
    implementation ("org.eclipse.jetty.websocket:websocket-server:9.4.42.v20210604")
    implementation ("org.eclipse.jetty.websocket:websocket-servlet:9.4.42.v20210604")
    implementation ("com.squareup.okhttp3:okhttp:4.9.1")
    implementation ("org.jetbrains.kotlin:kotlin-stdlib:1.6.0")
    implementation ("javax.json:javax.json-api:1.1.4")
    implementation ("org.glassfish:javax.json:1.1.4")
    implementation ("org.json:json:20090211")

}

