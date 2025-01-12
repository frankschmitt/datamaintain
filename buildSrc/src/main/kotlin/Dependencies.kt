import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.dependencies

fun Project.kotlinProject() {
    dependencies {
        "api"((platform("org.jetbrains.kotlin:kotlin-bom:${Versions.kotlin}")))
        "implementation"("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    }
}

fun Project.baseProject() {
    kotlinProject()

    dependencies {
        "implementation"("io.github.microutils:kotlin-logging:${Versions.kotlinLogging}")

        "testImplementation"("org.junit.jupiter:junit-jupiter-api:${Versions.junit}")
        "testImplementation"("org.junit.jupiter:junit-jupiter-params:${ Versions.junit }")
        "testRuntimeOnly"("org.junit.jupiter:junit-jupiter-engine:${Versions.junit}")
        "testImplementation"("io.strikt:strikt-core:${ Versions.strikt }")
        "testImplementation"("io.mockk:mockk:${ Versions.mockk }")
        "testImplementation"("org.testcontainers:testcontainers:${Versions.testcontainers}")
        "testImplementation"("org.testcontainers:junit-jupiter:${Versions.testcontainers}")
        "testImplementation"("org.testcontainers:mongodb:${Versions.testcontainers}")
    }

    tasks.getByPath("test").doFirst({
        with(this as Test) {
            useJUnitPlatform()
        }
    })

}
