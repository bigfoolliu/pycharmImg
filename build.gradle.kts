plugins {
    java
    id("org.jetbrains.intellij.platform") version "2.19.0"
}

group = "dev.pycharmimg"
version = "0.1.1"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        local("/Applications/PyCharm.app")
    }
    testImplementation("junit:junit:4.13.2")
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(17)
}

tasks.test {
    useJUnit()
    systemProperty("java.awt.headless", "true")
}

intellijPlatform {
    buildSearchableOptions = false
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "241.19416.19"
            untilBuild = "241.19416.19"
        }
    }
}
