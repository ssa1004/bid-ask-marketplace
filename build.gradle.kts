// 루트 빌드 — 공통 conventions. 각 모듈이 상속받는 공유 설정.
plugins {
    java
    kotlin("jvm") version "2.0.21" apply false
    kotlin("plugin.spring") version "2.0.21" apply false
    kotlin("plugin.jpa") version "2.0.21" apply false
    id("org.springframework.boot") version "3.4.13" apply false
    id("io.spring.dependency-management") version "1.1.7" apply false
    // OpenAPI spec build-time export — 실제 적용은 bootstrap 모듈.
    id("org.springdoc.openapi-gradle-plugin") version "1.9.0" apply false
    // Kotlin lint — CONTRIBUTING.md 가 명시한 ktlint. 전 subproject 에 적용.
    id("org.jlleitschuh.gradle.ktlint") version "12.1.1" apply false
    // 코드 커버리지 (Kotlin 네이티브). 루트가 subproject 결과를 합산.
    id("org.jetbrains.kotlinx.kover") version "0.8.3"
}

allprojects {
    group = "com.example.market"
    version = "0.1.0"

    repositories {
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "java")
    apply(plugin = "io.spring.dependency-management")
    apply(plugin = "org.jlleitschuh.gradle.ktlint")
    apply(plugin = "org.jetbrains.kotlinx.kover")

    java {
        toolchain {
            languageVersion = JavaLanguageVersion.of(21)
        }
    }

    // ktlint — kotlin.code.style=official 기준. 빌드 산출물은 검사 대상에서 제외.
    configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
        version.set("1.3.1")
        filter {
            exclude { it.file.path.contains("${'$'}{File.separator}build${'$'}{File.separator}") }
        }
    }

    the<io.spring.gradle.dependencymanagement.dsl.DependencyManagementExtension>().apply {
        imports {
            mavenBom("org.springframework.boot:spring-boot-dependencies:3.4.13")
            mavenBom("org.springframework.modulith:spring-modulith-bom:1.3.5")
        }
    }

    dependencies {
        // 모든 모듈 공통: JUnit launcher
        "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }

    tasks.withType<JavaCompile> {
        options.compilerArgs.addAll(listOf("-parameters", "-Xlint:all", "-Xlint:-processing"))
        options.encoding = "UTF-8"
    }
}

// Kover — production 모듈의 커버리지를 루트에서 합산해 하나의 리포트로 낸다.
// e2e-tests 는 production 코드가 없는 통합 시나리오 모듈이라 합산 대상에서 제외.
dependencies {
    listOf(
        "market-domain",
        "market-application",
        "market-adapter-in",
        "market-adapter-out",
        "market-batch",
        "market-bootstrap",
    ).forEach { kover(project(":$it")) }
}

// ./gradlew koverHtmlReport  → build/reports/kover/html/index.html
// ./gradlew koverXmlReport   → build/reports/kover/report.xml (CI 배지/업로드용)
kover {
    reports {
        // Spring Boot 진입점 / 생성 코드 등 단위 테스트 대상이 아닌 것은 커버리지에서 제외.
        filters {
            excludes {
                classes("com.example.market.MarketApplication*")
                annotatedBy("org.springframework.context.annotation.Configuration")
            }
        }
    }
}
