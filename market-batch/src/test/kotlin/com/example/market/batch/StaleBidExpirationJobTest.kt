package com.example.market.batch

import com.example.market.application.port.`in`.ExpireStaleBidsUseCase
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.atLeast
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.batch.core.BatchStatus
import org.springframework.batch.test.JobLauncherTestUtils
import org.springframework.batch.test.JobRepositoryTestUtils
import org.springframework.batch.test.context.SpringBatchTest
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.ContextConfiguration
import kotlin.test.assertEquals

/**
 * expireStaleBidsJob — 만료할 BID 가 없을 때까지 use case 를 반복 호출하고 COMPLETED 로 끝나는지 검증.
 */
@SpringBatchTest
@ContextConfiguration(classes = [BatchTestContext::class, StaleBidExpirationJobConfig::class])
class StaleBidExpirationJobTest {

    @Autowired
    private lateinit var jobLauncherTestUtils: JobLauncherTestUtils

    @Autowired
    private lateinit var jobRepositoryTestUtils: JobRepositoryTestUtils

    @Autowired
    private lateinit var useCase: ExpireStaleBidsUseCase

    @AfterEach
    fun cleanUp() {
        jobRepositoryTestUtils.removeJobExecutions()
    }

    @Test
    fun `batch 가 0 이 될 때까지 반복 호출한다`() {
        whenever(useCase.expireBatch(1000)).thenReturn(1000, 250, 0)

        val execution = jobLauncherTestUtils.launchJob()

        assertEquals(BatchStatus.COMPLETED, execution.status)
        verify(useCase, atLeast(3)).expireBatch(1000)
    }

    @Test
    fun `만료 대상이 없으면 호출 후 COMPLETED`() {
        whenever(useCase.expireBatch(1000)).thenReturn(0)

        val execution = jobLauncherTestUtils.launchJob()

        assertEquals(BatchStatus.COMPLETED, execution.status)
        verify(useCase, atLeast(1)).expireBatch(1000)
    }
}
