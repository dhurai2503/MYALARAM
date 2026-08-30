package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.Alarm
import com.example.data.AlarmRepository
import com.example.data.AlarmRingState
import com.example.data.AppDatabase
import com.example.viewmodel.AlarmViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AlarmViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var repository: AlarmRepository
    private lateinit var viewModel: AlarmViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = AlarmRepository(database.alarmDao(), database.securityDao())
        viewModel = AlarmViewModel(repository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        database.close()
    }

    @Test
    fun testDefaultPasscodeInitialization() = runTest(testDispatcher) {
        advanceUntilIdle()
        val setting = repository.getSecuritySettingDirect()
        assertEquals("10-digit default passcode should be initialized", 10, setting.passcode.length)
        assertEquals("1234567890", setting.passcode)
    }

    @Test
    fun testVolumeScaleReducesTo40PercentOnFirstDigit() = runTest(testDispatcher) {
        advanceUntilIdle()
        // Default volume scale should be 1.0f (100%)
        AlarmRingState.setVolumeScale(1.0f)
        assertEquals(1.0f, AlarmRingState.volumeScale.value, 0.01f)

        // Enter 1st digit
        viewModel.onDigitPressed(context, 1)
        advanceUntilIdle()

        assertEquals("1", viewModel.enteredPasscode.value)
        assertEquals(
            "Volume scale should reduce to 0.40f (reduced by 60%) upon entering digit",
            0.4f,
            AlarmRingState.volumeScale.value,
            0.01f
        )

        // Enter second digit - volume should stay at 0.40f
        viewModel.onDigitPressed(context, 2)
        advanceUntilIdle()
        assertEquals("12", viewModel.enteredPasscode.value)
        assertEquals(0.4f, AlarmRingState.volumeScale.value, 0.01f)

        // Backspace once - 1 digit remains -> volume still 0.4f
        viewModel.onBackspacePressed()
        advanceUntilIdle()
        assertEquals("1", viewModel.enteredPasscode.value)
        assertEquals(0.4f, AlarmRingState.volumeScale.value, 0.01f)

        // Backspace again - 0 digits remain -> volume restores to 1.0f (100%)
        viewModel.onBackspacePressed()
        advanceUntilIdle()
        assertEquals("", viewModel.enteredPasscode.value)
        assertEquals(1.0f, AlarmRingState.volumeScale.value, 0.01f)
    }

    @Test
    fun testPasscodeClearRestoresFullVolume() = runTest(testDispatcher) {
        advanceUntilIdle()
        viewModel.onDigitPressed(context, 5)
        viewModel.onDigitPressed(context, 6)
        assertEquals(0.4f, AlarmRingState.volumeScale.value, 0.01f)

        viewModel.onClearPressed()
        advanceUntilIdle()

        assertEquals("", viewModel.enteredPasscode.value)
        assertEquals(1.0f, AlarmRingState.volumeScale.value, 0.01f)
    }

    @Test
    fun testPasscodeVerificationFailure() = runTest(testDispatcher) {
        advanceUntilIdle()
        AlarmRingState.startRinging(1, "Morning Alarm")

        // Enter wrong 10 digits
        "0000000000".forEach { char ->
            viewModel.onDigitPressed(context, char.digitToInt())
        }

        // Allow background thread to process Room DAO query and update Main dispatcher
        for (i in 0 until 20) {
            Thread.sleep(25)
            ShadowLooper.idleMainLooper()
            advanceUntilIdle()
            if (viewModel.enteredPasscode.value.isEmpty() && viewModel.isPasscodeIncorrect.value) break
        }

        // Passcode should be reset and marked incorrect
        assertEquals("", viewModel.enteredPasscode.value)
        assertTrue(viewModel.isPasscodeIncorrect.value)
        // Alarm remains ringing
        assertTrue(AlarmRingState.isRinging.value)
        // Volume restored to 1.0f
        assertEquals(1.0f, AlarmRingState.volumeScale.value, 0.01f)
    }

    @Test
    fun testAlarmToggleAndDatabaseOperations() = runTest(testDispatcher) {
        advanceUntilIdle()
        val alarm = Alarm(
            id = 1,
            hour = 7,
            minute = 30,
            isEnabled = true,
            label = "Work",
            repeatDays = "Mon,Tue"
        )
        repository.insertAlarm(alarm)
        advanceUntilIdle()

        viewModel.toggleAlarm(context, alarm.copy(isEnabled = false))
        advanceUntilIdle()
    }
}
