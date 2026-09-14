package com.example.expensestracker.ui.stats

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.example.expensestracker.ui.month.MonthlyTrend
import com.example.expensestracker.util.formatMonthShort
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.compose.cartesian.data.columnModel
import com.patrykandpatrick.vico.compose.cartesian.layer.ColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.common.Fill
import com.patrykandpatrick.vico.compose.common.ProvideVicoTheme
import com.patrykandpatrick.vico.compose.common.component.rememberLineComponent
import com.patrykandpatrick.vico.compose.m3.common.rememberM3VicoTheme
import java.time.YearMonth

/**
 * The last 6 months' totals as bars, tap-to-jump to that month. No value axis - the point is the
 * shape of the trend and the month labels, not reading exact figures off a scale (those are in
 * the summary card above). Tap detection is done with a plain pointerInput over the chart's own
 * bounds (dividing the width into [MonthlyTrend.months].size equal slots) rather than through
 * Vico's marker/interaction system, since every bar is the same width and evenly spaced - it's
 * simpler and doesn't depend on Vico's own hit-testing internals.
 */
@Composable
fun MonthlyTrendChart(
    trend: MonthlyTrend,
    onMonthClick: (YearMonth) -> Unit,
    modifier: Modifier = Modifier
) {
    val months = trend.months
    if (months.isEmpty()) return

    val modelProducer = remember { CartesianChartModelProducer() }
    LaunchedEffect(months) {
        modelProducer.runTransaction {
            columnModel { series(months.map { it.totalSpent }) }
        }
    }

    val columnColor = MaterialTheme.colorScheme.primary

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(160.dp)
            .pointerInput(months) {
                detectTapGestures { offset ->
                    val slotWidth = size.width / months.size.toFloat()
                    val index = (offset.x / slotWidth).toInt().coerceIn(0, months.size - 1)
                    onMonthClick(months[index].yearMonth)
                }
            }
    ) {
        ProvideVicoTheme(rememberM3VicoTheme()) {
            CartesianChartHost(
                chart = rememberCartesianChart(
                    rememberColumnCartesianLayer(
                        columnProvider = ColumnCartesianLayer.ColumnProvider.series(
                            rememberLineComponent(
                                fill = Fill(columnColor),
                                thickness = 22.dp,
                                shape = RoundedCornerShape(6.dp)
                            )
                        )
                    ),
                    bottomAxis = HorizontalAxis.rememberBottom(
                        valueFormatter = CartesianValueFormatter { _, value, _ ->
                            months.getOrNull(value.toInt())?.let { formatMonthShort(it.yearMonth) } ?: ""
                        }
                    )
                ),
                modelProducer = modelProducer,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
