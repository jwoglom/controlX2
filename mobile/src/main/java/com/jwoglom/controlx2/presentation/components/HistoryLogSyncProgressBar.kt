package com.jwoglom.controlx2.presentation.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jwoglom.controlx2.LocalDataStore
import com.jwoglom.controlx2.db.historylog.HistoryLogDummyDao
import com.jwoglom.controlx2.db.historylog.HistoryLogItem
import com.jwoglom.controlx2.db.historylog.HistoryLogRepo
import com.jwoglom.controlx2.db.historylog.HistoryLogViewModel
import com.jwoglom.controlx2.presentation.DataStore
import com.jwoglom.controlx2.presentation.theme.ControlX2Theme
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.HistoryLogStatusResponse
import java.time.LocalDateTime
import kotlin.math.max

@Composable
fun HistoryLogSyncProgressBar(
    historyLogViewModel: HistoryLogViewModel,
    modifier: Modifier = Modifier,
    hideWhenComplete: Boolean = false
) {
    val dataStore = LocalDataStore.current
    val historyLogStatus by dataStore.historyLogStatus.observeAsState()

    historyLogStatus?.let { status ->
        val pumpMaxSeqNum = status.lastSequenceNum
        val pumpMinSeqNum = status.firstSequenceNum
        
        // Calculate the range we care about: the most recent 5000 sequence IDs
        val targetMinSeqId = max(pumpMinSeqNum, pumpMaxSeqNum - 5000)
        
        // Get count of logs in DB above this threshold
        val dbLogCount by historyLogViewModel.getCountAboveSeqId(targetMinSeqId).observeAsState()
        val syncedLogs = dbLogCount ?: 0L
        
        // Total logs is the count that should exist on the pump in this range
        // If pumpMinSeqNum is within our target range, we only track from there
        val effectiveMinSeqId = max(pumpMinSeqNum, targetMinSeqId)
        val totalLogs = pumpMaxSeqNum - effectiveMinSeqId
        
        // Calculate progress (handle edge cases)
        val progress = if (totalLogs > 0) {
            (syncedLogs.toFloat() / totalLogs.toFloat()).coerceIn(0f, 1f)
        } else {
            1f
        }
        
        val isSynced = syncedLogs >= totalLogs

        if (hideWhenComplete && isSynced) {
            Column(
                modifier = modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .height(50.dp)
            ) {
                Spacer(modifier = Modifier.fillMaxWidth())
            }
            return
        }

        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .height(50.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "History Log Sync",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = if (isSynced) {
                        "Synced"
                    } else {
                        "$syncedLogs / $totalLogs"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 12.sp,
                    color = if (isSynced) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
            
            Spacer(modifier = Modifier.height(4.dp))
            
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp),
            )
            
            if (!isSynced && totalLogs > 0) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${(progress * 100).toInt()}% complete (${totalLogs - syncedLogs} logs remaining)",
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// Preview helper function to create mock HistoryLogViewModel
private fun createMockHistoryLogViewModel(maxSeqId: Long): HistoryLogViewModel {
    val mockItems = (1L..maxSeqId).map { seqId ->
        HistoryLogItem(
            seqId = seqId,
            pumpSid = 0,
            typeId = 1,
            cargo = ByteArray(0),
            pumpTime = LocalDateTime.now(),
            addedTime = LocalDateTime.now()
        )
    }.toMutableList()
    return HistoryLogViewModel(HistoryLogRepo(HistoryLogDummyDao(mockItems)), 0)
}

// Preview helper to set up DataStore with mock HistoryLogStatusResponse
private fun setupMockDataStore(dataStore: DataStore, firstSeqNum: Long, lastSeqNum: Long) {
    // Create a mock HistoryLogStatusResponse
    dataStore.historyLogStatus.value = object : HistoryLogStatusResponse() {
        override fun getFirstSequenceNum(): Long = firstSeqNum
        override fun getLastSequenceNum(): Long = lastSeqNum
    }
}

@Preview(showBackground = true, name = "50% Synced")
@Composable
private fun HistoryLogSyncProgressBar_HalfSynced_Preview() {
    val historyLogViewModel = createMockHistoryLogViewModel(5000)
    
    ControlX2Theme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.White,
        ) {
            val dataStore = DataStore()
            
            // Mock: pump has logs 1-10000, DB has logs 1-5000 (50% synced)
            setupMockDataStore(dataStore, 1, 10000)
            
            CompositionLocalProvider(LocalDataStore provides dataStore) {
                HistoryLogSyncProgressBar(
                    historyLogViewModel = historyLogViewModel
                )
            }
        }
    }
}

@Preview(showBackground = true, name = "75% Synced")
@Composable
private fun HistoryLogSyncProgressBar_MostlySynced_Preview() {
    val historyLogViewModel = createMockHistoryLogViewModel(7500)
    
    ControlX2Theme {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color.White,
        ) {
            val dataStore = DataStore()
            
            // Mock: pump has logs 1-10000, DB has logs 1-7500 (75% synced)
            setupMockDataStore(dataStore, 1, 10000)
            
            CompositionLocalProvider(LocalDataStore provides dataStore) {
                HistoryLogSyncProgressBar(
                    historyLogViewModel = historyLogViewModel
                )
            }
        }
    }
}

@Preview(showBackground = true, name = "Fully Synced")
@Composable
private fun HistoryLogSyncProgressBar_FullySynced_Preview() {
    val historyLogViewModel = createMockHistoryLogViewModel(10000)
    
    ControlX2Theme {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color.White,
        ) {
            val dataStore = DataStore()
            
            // Mock: pump has logs 1-10000, DB has logs 1-10000 (100% synced)
            setupMockDataStore(dataStore, 1, 10000)
            
            CompositionLocalProvider(LocalDataStore provides dataStore) {
                HistoryLogSyncProgressBar(
                    historyLogViewModel = historyLogViewModel
                )
            }
        }
    }
}
@Preview(showBackground = true, name = "Fully Synced Hidden")
@Composable
private fun HistoryLogSyncProgressBar_FullySyncedHidden_Preview() {
    val historyLogViewModel = createMockHistoryLogViewModel(10000)

    ControlX2Theme {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color.White,
        ) {
            val dataStore = DataStore()

            // Mock: pump has logs 1-10000, DB has logs 1-10000 (100% synced)
            setupMockDataStore(dataStore, 1, 10000)

            CompositionLocalProvider(LocalDataStore provides dataStore) {
                HistoryLogSyncProgressBar(
                    historyLogViewModel = historyLogViewModel,
                    hideWhenComplete = true
                )
            }
        }
    }
}

@Preview(showBackground = true, name = "Just Started (10%)")
@Composable
private fun HistoryLogSyncProgressBar_JustStarted_Preview() {
    val historyLogViewModel = createMockHistoryLogViewModel(1000)
    
    ControlX2Theme {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color.White,
        ) {
            val dataStore = DataStore()
            
            // Mock: pump has logs 1-10000, DB has logs 1-1000 (10% synced)
            setupMockDataStore(dataStore, 1, 10000)
            
            CompositionLocalProvider(LocalDataStore provides dataStore) {
                HistoryLogSyncProgressBar(
                    historyLogViewModel = historyLogViewModel
                )
            }
        }
    }
}

// Helper to create ViewModel for non-zero start preview (outside composable)
private fun createNonZeroStartViewModel(): HistoryLogViewModel {
    val mockItems = (5000L..8000L).map { seqId ->
        HistoryLogItem(
            seqId = seqId,
            pumpSid = 0,
            typeId = 1,
            cargo = ByteArray(0),
            pumpTime = LocalDateTime.now(),
            addedTime = LocalDateTime.now()
        )
    }.toMutableList()
    return HistoryLogViewModel(HistoryLogRepo(HistoryLogDummyDao(mockItems)), 0)
}

@Preview(showBackground = true, name = "With Non-Zero First Seq")
@Composable
private fun HistoryLogSyncProgressBar_NonZeroStart_Preview() {
    val historyLogViewModel = createNonZeroStartViewModel()

    ControlX2Theme {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color.White,
        ) {
            val dataStore = DataStore()
            
            // Mock: pump has logs 5000-10000, DB has logs 5000-8000 (60% synced)
            setupMockDataStore(dataStore, 5000, 10000)
            
            CompositionLocalProvider(LocalDataStore provides dataStore) {
                HistoryLogSyncProgressBar(
                    historyLogViewModel = historyLogViewModel
                )
            }
        }
    }
}
