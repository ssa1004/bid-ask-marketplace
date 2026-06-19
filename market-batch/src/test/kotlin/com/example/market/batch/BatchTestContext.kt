package com.example.market.batch

import com.example.market.application.port.`in`.AutoCancelStaleTradesUseCase
import com.example.market.application.port.`in`.ExpireStaleBidsUseCase
import com.example.market.application.port.`in`.ExpireStaleListingsUseCase
import org.mockito.kotlin.mock
import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType
import org.springframework.transaction.PlatformTransactionManager
import javax.sql.DataSource

/**
 * Batch job 테스트용 최소 컨텍스트.
 *
 * 앱 전체 부팅 / Postgres / Kafka 없이, 인메모리 H2 위에 Spring Batch 메타데이터 테이블만
 * 올려 JobLauncher / JobRepository 를 띄운다. application use case 는 mock 으로 주입해
 * tasklet 의 batch-loop 거동(다 처리할 때까지 반복, 결과 집계)만 검증한다.
 */
@Configuration
@EnableBatchProcessing
class BatchTestContext {

    @Bean
    fun dataSource(): DataSource =
        EmbeddedDatabaseBuilder()
            .setType(EmbeddedDatabaseType.H2)
            // 테스트 클래스마다 별도 컨텍스트가 각자 H2 인스턴스를 갖도록 unique name.
            .generateUniqueName(true)
            // Spring Batch 메타 테이블 (BATCH_JOB_INSTANCE 등) DDL.
            .addScript("classpath:org/springframework/batch/core/schema-h2.sql")
            .build()

    @Bean
    fun transactionManager(dataSource: DataSource): PlatformTransactionManager =
        DataSourceTransactionManager(dataSource)

    @Bean
    fun expireStaleListingsUseCase(): ExpireStaleListingsUseCase = mock()

    @Bean
    fun expireStaleBidsUseCase(): ExpireStaleBidsUseCase = mock()

    @Bean
    fun autoCancelStaleTradesUseCase(): AutoCancelStaleTradesUseCase = mock()
}
