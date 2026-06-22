// Inbound adapter — REST + WebSocket (Kotlin)
plugins {
    `java-library`
    kotlin("jvm")
    kotlin("plugin.spring")
}

dependencies {
    implementation(project(":market-application"))

    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
    implementation("org.springframework.boot:spring-boot-starter-websocket")  // 실시간 호가 push
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")

    // Kafka consumers (정산 이벤트 수신 등)
    implementation("org.springframework.kafka:spring-kafka")

    // OpenAPI — 2.8.x 라인은 Spring Framework 6.2 (Spring Boot 3.5) 호환. 2.6.0 은
    // SF 6.2 에서 제거된 ControllerAdviceBean(Object) 생성자를 호출해 @RestControllerAdvice
    // 가 있으면 /v3/api-docs 가 NoSuchMethodError 로 500 을 던진다 (springdoc #3041).
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.9")

    // Tracing
    implementation("io.micrometer:micrometer-tracing")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.4.0")
    testImplementation("com.fasterxml.jackson.module:jackson-module-kotlin")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict")
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}
