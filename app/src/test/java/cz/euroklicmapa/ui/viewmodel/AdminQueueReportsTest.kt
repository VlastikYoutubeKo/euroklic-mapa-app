package cz.euroklicmapa.ui.viewmodel

import androidx.lifecycle.ViewModelStore
import cz.euroklicmapa.data.model.AdminListResponse
import cz.euroklicmapa.data.model.ApiResult
import cz.euroklicmapa.data.model.LocationReport
import cz.euroklicmapa.data.remote.EuroklicApi
import cz.euroklicmapa.data.repository.AdminListState
import cz.euroklicmapa.data.repository.AdminRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.lang.reflect.Proxy

@OptIn(ExperimentalCoroutinesApi::class)
class AdminQueueReportsTest {
    @Test
    fun resolveRemovesRowAndUsesServerMessage() = runTest {
        verifyReview(resolve = true, expectedAction = "resolve_report", expectedMessage = "Nahlášení označeno jako vyřešené.") {
            ApiResult(success = true, message = "Nahlášení označeno jako vyřešené.")
        }
    }

    @Test
    fun dismissRemovesRowAndUsesServerMessage() = runTest {
        verifyReview(resolve = false, expectedAction = "dismiss_report", expectedMessage = "Nahlášení zamítnuto.") {
            ApiResult(success = true, message = "Nahlášení zamítnuto.")
        }
    }

    @Test
    fun alreadyHandledReportIsRemovedAndServerErrorIsShown() = runTest {
        verifyReview(resolve = true, expectedAction = "resolve_report", expectedMessage = "Nahlášení nenalezeno.") {
            throw httpError(404, "Nahlášení nenalezeno.")
        }
    }

    @Test
    fun forbiddenReportRemainsInQueue() = runTest {
        verifyReview(resolve = false, expectedAction = "dismiss_report", expectedMessage = "Nemáte oprávnění.", removed = false) {
            throw httpError(403, "Nemáte oprávnění.")
        }
    }

    @Test
    fun http200FailureRemainsInQueue() = runTest {
        verifyReview(resolve = true, expectedAction = "resolve_report", expectedMessage = "Akce odmítnuta.", removed = false) {
            ApiResult(success = false, error = "Akce odmítnuta.")
        }
    }

    private suspend fun TestScope.verifyReview(
        resolve: Boolean,
        expectedAction: String,
        expectedMessage: String,
        removed: Boolean = true,
        review: () -> ApiResult,
    ) {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val store = ViewModelStore()
        try {
            val reports = listOf(
                LocationReport(id = 42, location_id = 123, reason = "key_broken"),
                LocationReport(id = 41, location_id = 124, reason = "other"),
            )
            var called = false
            val api = Proxy.newProxyInstance(
                EuroklicApi::class.java.classLoader,
                arrayOf(EuroklicApi::class.java),
            ) { _, method, args ->
                when (method.name) {
                    "adminList" -> AdminListResponse(report_count = 2, location_reports = reports)
                    "adminReview" -> {
                        called = true
                        assertEquals(expectedAction, args!![0])
                        assertNull(args[1]) // place id
                        assertNull(args[2]) // photo id
                        assertNull(args[3]) // comment id
                        assertEquals(42, args[4]) // report_id has its own id space
                        review()
                    }
                    else -> error("Unexpected API call: ${method.name}")
                }
            } as EuroklicApi
            val vm = AdminQueueViewModel(AdminRepository(api))
            store.put("queue", vm)
            assertEquals(reports, (vm.state.value as AdminListState.Loaded).reports)
            vm.reviewReport(42, resolve)
            assertTrue(called)
            assertEquals(expectedMessage, vm.snack.first())
            assertEquals(if (removed) listOf(41) else listOf(42, 41), (vm.state.value as AdminListState.Loaded).reports.map { it.id })
            assertNull(vm.actingReportId)
        } finally {
            store.clear()
            Dispatchers.resetMain()
        }
    }

    private fun httpError(code: Int, message: String) = HttpException(
        Response.error<ApiResult>(code, """{"error":"$message","extra":true}""".toResponseBody("application/json".toMediaType())),
    )
}
