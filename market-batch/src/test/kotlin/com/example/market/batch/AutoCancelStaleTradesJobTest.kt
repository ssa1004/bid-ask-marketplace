package com.example.market.batch

import com.example.market.application.port.`in`.AutoCancelStaleTradesUseCase
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.atLeast
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.batch.core.BatchStatus
import org.springframework.batch.test.JobLauncherTestUtils
import org.springframework.batch.test.JobRepositoryTestUtils
import org.springframework.batch.test.context.SpringBatchTest
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.ContextConfiguration
import java.time.Duration
import kotlin.test.assertEquals

/**
 * autoCancelStaleTradesJob — 결제 TTL 을 넘긴 CREATED 거래를 더 없을 때까지 반복 취소하는지,
 * 그리고 설정된 TTL(기본 15분) + batch size(500) 가 use case 로 그대로 전달되는지 검증.
 */
@SpringBatchTest
@ContextConfiguration(classes = [BatchTestContext::class, AutoCancelStaleTradesJobConfig::class])
class AutoCancelStaleTradesJobTest {

    @Autowired
    private lateinit var jobLauncherTestUtils: JobLauncherTestUtils

    @Autowired
    private lateinit var jobRepositoryTestUtils: JobRepositoryTestUtils

    @Autowired
    private lateinit var useCase: AutoCancelStaleTradesUseCase

    @AfterEach
    fun cleanUp() {
        jobRepositoryTestUtils.removeJobExecutions()
    }

    @Test
    fun `취소할 거래가 없을 때까지 반복하고 기본 TTL 15분 + batch 500 을 전달한다`() {
        whenever(useCase.cancelStale(eq(Duration.ofMinutes(15)), eq(500))).thenReturn(500, 120, 0)

        val execution = jobLauncherTestUtils.launchJob()

        assertEquals(BatchStatus.COMPLETED, execution.status)
        // 0 이 나올 때까지 반복 호출 — 매번 TTL 15분 / batch 500 으로 drain.
        verify(useCase, atLeast(3)).cancelStale(eq(Duration.ofMinutes(15)), eq(500))
    }

    @Test
    fun `취소 대상이 없으면 호출 후 COMPLETED`() {
        whenever(useCase.cancelStale(eq(Duration.ofMinutes(15)), eq(500))).thenReturn(0)

        val execution = jobLauncherTestUtils.launchJob()

        assertEquals(BatchStatus.COMPLETED, execution.status)
        verify(useCase, atLeast(1)).cancelStale(eq(Duration.ofMinutes(15)), eq(500))
    }
}
