package com.uncosens.rocketchess.ui

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VariationDeviceTest {
    @Test
    fun firstOwnMoveDoesNotBlockTheMainThread() = runBlocking {
        withContext(Dispatchers.Main) {
            val vm = GameViewModel()
            vm.load("1. e4 e5 2. Nf3 Nc6 *", "variation-test", null)
            withTimeout(5_000) { while (vm.line == null) delay(10) }
            vm.goTo(2)
            val started = System.nanoTime()
            vm.play("f2f4")
            Log.i("VariationTest", "play returned in ${(System.nanoTime() - started) / 1_000_000.0} ms")
            // Preparing the move must dispatch away from the UI thread before publishing it.
            assertEquals(2, vm.ply)
            withTimeout(5_000) { while (vm.ply == 2) delay(10) }
            assertNotNull(vm.variation)
            assertEquals(2, vm.variation!!.branch)
            assertEquals(listOf("e4", "e5", "f4"), vm.line!!.plies.map { it.san })
            // Navigating cancels a pending move before another position is played.
            vm.goTo(2)
            vm.play("d2d4")
            vm.goTo(1)
            vm.play("c7c5")
            assertEquals(1, vm.ply)
            withTimeout(5_000) { while (vm.ply == 1) delay(10) }
            assertEquals(listOf("e4", "c5"), vm.line!!.plies.map { it.san })
            assertEquals(1, vm.variation!!.branch)
            vm.leaveVariation()
            assertEquals(1, vm.ply)
            assertEquals(listOf("e4", "e5", "Nf3", "Nc6"), vm.line!!.plies.map { it.san })
        }
    }
}
