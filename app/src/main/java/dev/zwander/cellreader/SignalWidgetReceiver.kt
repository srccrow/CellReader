package dev.zwander.cellreader

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.telephony.*
import android.util.Log
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.currentCompositionLocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.*
import androidx.glance.appwidget.*
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.itemsIndexed
import androidx.glance.layout.*
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.Text
import dev.zwander.cellreader.utils.PrefUtils
import dev.zwander.cellreader.utils.onAvail
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class SignalWidget : GlanceAppWidget() {
    companion object {
        val cellInfoKey = stringPreferencesKey("cellInfos")
        val primaryCellKey = intPreferencesKey("primaryCell")

        private val SMALL_BOX = DpSize(120.dp, 120.dp)
        private val BIG_BOX = DpSize(180.dp, 180.dp)
        private val VERY_BIG_BOX = DpSize(300.dp, 300.dp)
        private val ROW = DpSize(120.dp, 48.dp)
        private val LARGE_ROW = DpSize(300.dp, 48.dp)
        private val COLUMN = DpSize(48.dp, 120.dp)
        private val LARGE_COLUMN = DpSize(48.dp, 300.dp)
    }

    override val stateDefinition = PreferencesGlanceStateDefinition

    override val sizeMode = SizeMode.Responsive(
        setOf(
            SMALL_BOX, BIG_BOX, VERY_BIG_BOX, ROW, LARGE_ROW, COLUMN, LARGE_COLUMN
        )
    )

    @Composable
    override fun Content() {
        val context = LocalContext.current

        Box(
            modifier = GlanceModifier.cornerRadius(8.dp)
                .background(Color(0xff121212))
                .fillMaxSize()
        ) {
            LazyColumn(
                modifier = GlanceModifier.fillMaxSize()
            ) {
                sortedSubIds.forEach { t ->
                    item(t.toLong()) {
                        Box(
                            modifier = GlanceModifier.height(48.dp)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = context.resources.getString(R.string.sim_slot_format, t.toString()))
                        }
                    }

                    itemsIndexed(cellInfos[t]!!, { _, item -> "$t:${item.cellIdentity}".hashCode().toLong() }) { _, item ->
                        SignalCard(cellInfo = item)
                    }
                }
            }
        }
    }

    @Composable
    private fun SignalCard(cellInfo: CellInfo) {
        val context = LocalContext.current

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = GlanceModifier.fillMaxWidth()
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = GlanceModifier.padding(8.dp)
                    .fillMaxWidth(),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Image(
                            provider = ImageProvider(
                                when (cellInfo.cellSignalStrength.level) {
                                    CellSignalStrength.SIGNAL_STRENGTH_POOR -> R.drawable.cell_1
                                    CellSignalStrength.SIGNAL_STRENGTH_MODERATE -> R.drawable.cell_2
                                    CellSignalStrength.SIGNAL_STRENGTH_GOOD -> R.drawable.cell_3
                                    CellSignalStrength.SIGNAL_STRENGTH_GREAT -> R.drawable.cell_4
                                    else -> R.drawable.cell_0
                                }
                            ),
                            contentDescription = null,
                            modifier = GlanceModifier.width(48.dp).height(48.dp),
                            contentScale = ContentScale.Fit
                        )

                        Spacer(GlanceModifier.size(8.dp))

                        Text(text = cellInfo.cellSignalStrength.dbm.toString())
                    }

                    Spacer(GlanceModifier.size(8.dp))

                    Column(
                        modifier = GlanceModifier.fillMaxWidth()
                    ) {
                        Text(
                            text = context.resources.getString(
                                R.string.type_format,
                                context.resources.getString(
                                    when (cellInfo.cellSignalStrength) {
                                        is CellSignalStrengthGsm -> R.string.gsm
                                        is CellSignalStrengthWcdma -> R.string.wcdma
                                        is CellSignalStrengthCdma -> R.string.cdma
                                        is CellSignalStrengthTdscdma -> R.string.tdscdma
                                        is CellSignalStrengthLte -> R.string.lte
                                        is CellSignalStrengthNr -> R.string.nr
                                        else -> R.string.unknown
                                    }
                                )
                            )
                        )

                        with(cellInfo.cellSignalStrength) {
                            when (this) {
                                is CellSignalStrengthLte -> {
                                    Spacer(GlanceModifier.size(8.dp))

                                    Text(
                                        text = context.resources.getString(R.string.rsrq_format, rsrq.toString())
                                    )
                                }
                                is CellSignalStrengthNr -> {
                                    Spacer(GlanceModifier.size(8.dp))

                                    csiRsrq.onAvail {
                                        Text(text = context.resources.getString(R.string.rsrq_format, it.toString()))
                                    }

                                    ssRsrq.onAvail {
                                        Text(text = context.resources.getString(R.string.rsrq_format, it.toString()))
                                    }
                                }
                            }
                        }

                        with (cellInfo.cellIdentity) {
                            when (this) {
                                is CellIdentityLte -> {
                                    Spacer(GlanceModifier.size(8.dp))

                                    Text(text = context.resources.getString(R.string.bands_format, bands.joinToString(", ")))
                                }
                                is CellIdentityNr -> {
                                    Spacer(GlanceModifier.size(8.dp))

                                    Text(text = context.resources.getString(R.string.bands_format, bands.joinToString(", ")))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

class SignalWidgetReceiver : GlanceAppWidgetReceiver() {
    companion object {
        const val ACTION_REFRESH = "${BuildConfig.APPLICATION_ID}.REFRESH"
    }

    override val glanceAppWidget = SignalWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)

        context.startForegroundService(Intent(context, UpdaterService::class.java))
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_REFRESH) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName =
                ComponentName(context.packageName, checkNotNull(javaClass.canonicalName))
            onUpdate(
                context,
                appWidgetManager,
                appWidgetManager.getAppWidgetIds(componentName)
            )
            GlobalScope.launch {
                glanceAppWidget.updateAll(context)
            }
            return
        }

        super.onReceive(context, intent)
    }
}