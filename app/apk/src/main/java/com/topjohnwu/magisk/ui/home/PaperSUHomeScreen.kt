// ---------------------------------------------------------------------------
// paperSU: 纸SU 风格的首页。
//
// 原来这里是 Magisk 的 HomeScreen（37 KB，带 "Support Us"/"Follow Us" 之类的社交块，
// 还有它自己那套 update 卡片）。纸SU 的首页只要一眼看清四件事：
//   现在能不能用 / 用的是哪套实现 / 什么版本 / 什么内核。
//
// 数据全部来自 core 的 Info（Magisk 已经在启动时喂好了，不需要再跑 su 命令）：
//   Info.env.isActive / Info.env.versionString / Info.isRooted / Info.isSAR / Info.isAB / Info.slot
// ---------------------------------------------------------------------------
package com.topjohnwu.magisk.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.topjohnwu.magisk.core.Info
import com.topjohnwu.magisk.core.R as CoreR

@Composable
fun PaperSUHomeScreen(modifier: Modifier = Modifier) {
    // 点「重新检测」时 +1，触发重算
    var tick by remember { mutableIntStateOf(0) }

    val active = remember(tick) { Info.env.isActive }
    val versionString = remember(tick) { Info.env.versionString }
    val rooted = remember(tick) { Info.isRooted }
    val sar = remember(tick) { Info.isSAR }
    val ab = remember(tick) { Info.isAB }
    val slot = remember(tick) { Info.slot }

    val kernel = remember(tick) {
        runCatching { System.getProperty("os.version").orEmpty() }.getOrDefault("")
    }
    val arch = remember(tick) {
        runCatching { System.getProperty("os.arch").orEmpty() }.getOrDefault("")
    }

    val accent = MaterialTheme.colorScheme.primary
    val good = Color(0xFF2E7D32)
    val bad = MaterialTheme.colorScheme.error

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(Modifier.height(8.dp))

        // ---------------- 头部：状态
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.linearGradient(
                        listOf(
                            MaterialTheme.colorScheme.primaryContainer,
                            MaterialTheme.colorScheme.surfaceContainerHigh,
                        )
                    ),
                    shape = RoundedCornerShape(24.dp),
                )
                .padding(20.dp),
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(10.dp)
                            .background(if (active) good else bad, CircleShape)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(
                            if (active) CoreR.string.papersu_home_working
                            else CoreR.string.papersu_home_not_working
                        ),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = if (active && versionString.isNotBlank()) versionString
                    else stringResource(CoreR.string.not_installed),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // ---------------- 卡片：一堆键值
        InfoCard {
            InfoRow(
                stringResource(CoreR.string.papersu_home_root_mode),
                if (active) "Magisk" else "-",
            )
            InfoRow(
                stringResource(CoreR.string.papersu_home_version),
                versionString.ifBlank { "-" },
            )
            InfoRow(
                stringResource(CoreR.string.papersu_home_kernel),
                kernel.ifBlank { "-" },
            )
            InfoRow(
                stringResource(CoreR.string.papersu_home_arch),
                arch.ifBlank { "-" },
            )
            InfoRow(
                stringResource(CoreR.string.papersu_home_slot),
                if (ab) slot.ifBlank { "-" } else "A only",
            )
            InfoRow(
                "System-as-root",
                if (sar) "yes" else "no",
            )
            InfoRow(
                "Root",
                if (rooted) "yes" else "no",
            )
        }

        // ---------------- 重新检测
        Button(
            onClick = { tick++ },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(CoreR.string.papersu_home_check))
        }

        Spacer(Modifier.height(16.dp))
        Text(
            text = "PaperSU",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(bottom = 24.dp),
        )
    }
}

@Composable
private fun InfoCard(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(20.dp))
            .padding(vertical = 6.dp, horizontal = 16.dp),
    ) {
        content()
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
