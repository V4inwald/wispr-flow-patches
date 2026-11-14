group = "app.v4inwald"

patches {
    about {
        name = "Wispr Flow patches"
        description = "Patches for the Wispr Flow Android app: an unlimited dictation quota, and the install-source answer its Play licensing check needs."
        source = "git@github.com:V4inwald/wispr-flow-patches.git"
        author = "V4inwald"
        contact = "na"
        website = "https://github.com/V4inwald/wispr-flow-patches"
        license = "GNU General Public License v3.0"
    }
}

dependencies {
    implementation(libs.morphe.patches.library)
}

tasks {
    register<JavaExec>("generatePatchesList") {
        description = "Build a patches list for third-party tools"

        dependsOn(build)

        classpath = sourceSets["main"].runtimeClasspath
        mainClass.set("app.morphe.util.PatchListGeneratorKt")
    }

    // Used by gradle-semantic-release-plugin.
    publish {
        dependsOn("generatePatchesList")
    }
}
