plugins {
    id("com.alkeari.modtrix.fabric")
}

repositories {
    exclusiveContent {
        forRepository { maven("https://maven.shedaniel.me/") }
        filter { includeGroupAndSubgroups("me.shedaniel") }
    }
}

dependencies {
    modImplementation("me.shedaniel.cloth:cloth-config-fabric:${modtrix.dep("cloth_config")}") {
        exclude(group = "net.fabricmc.fabric-api")
    }
}
