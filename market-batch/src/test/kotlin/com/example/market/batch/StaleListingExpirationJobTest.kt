package com.example.market.batch

import com.example.market.application.port.`in`.ExpireStaleListingsUseCase
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.atLeast
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.batch.core.BatchStatus
import org.springframework.batch.core.ExitStatus
import org.springframework.batch.test.JobLauncherTestUtils
import org.springframework.batch.test.JobRepositoryTestUtils
import org.springframework.batch.test.context.SpringBatchTest
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.ContextConfiguration
import kotlin.test.assertEquals

/**
 * expireStaleListingsJob — tasklet 이 use case 를 더 만료할 게 없을 때까지 반복 호출하고,
 * 만료 총량을 step write-count 로 집계하는지 검증.
 */
@SpringBatchTest
@ContextConfiguration(classes = [BatchTestContext::class, StaleListingExpirationJobConfig::class])
class StaleListingExpirationJobTest {

    @Autowired
    private lateinit var jobLauncherTestUtils: JobLauncherTestUtils

    @Autowired
    private lateinit var jobRepositoryTestUtils: JobRepositoryTestUtils

    @Autowired
    private lateinit var useCase: ExpireStaleListingsUseCase

    @AfterEach
    fun cleanUp() {
        jobRepositoryTestUtils.removeJobExecutions()
    }

    @Test
    fun `여러 batch 를 다 처리할 때까지 반복하고 총 만료 수를 write-count 로 집계한다`() {
        // 호출마다 1000 → 700 → 0. 0 이 나오면 do-while 이 종료 (사실상 큐를 비운다).
        whenever(useCase.expireBatch(1000)).thenReturn(1000, 700, 0)

        val execution = jobLauncherTestUtils.launchJob()

        assertEquals(BatchStatus.COMPLETED, execution.status)
        assertEquals(ExitStatus.COMPLETED, execution.exitStatus)
        // 1000 + 700 = 1700 을 step write-count 로 집계.
        val written = execution.stepExecutions.sumOf { it.writeCount }
        assertEquals(1700, written)
        // 0 이 나올 때까지 use case 를 반복 호출 — drain 동작 검증.
        verify(useCase, atLeast(3)).expireBatch(1000)
    }

    @Test
    fun `만료 대상이 없으면 호출 후 COMPLETED 이고 write-count 0`() {
        whenever(useCase.expireBatch(1000)).thenReturn(0)

        val execution = jobLauncherTestUtils.launchJob()

        assertEquals(BatchStatus.COMPLETED, execution.status)
        assertEquals(0, execution.stepExecutions.sumOf { it.writeCount })
        verify(useCase, atLeast(1)).expireBatch(1000)
    }
}
