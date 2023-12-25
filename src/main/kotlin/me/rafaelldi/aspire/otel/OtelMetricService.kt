package me.rafaelldi.aspire.otel

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.rafaelldi.aspire.generated.MetricBase
import me.rafaelldi.aspire.generated.MetricDouble
import me.rafaelldi.aspire.generated.MetricLong
import me.rafaelldi.aspire.sessionHost.AspireSessionListener
import me.rafaelldi.aspire.settings.AspireSettings
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write
import kotlin.time.Duration.Companion.seconds

@Service(Service.Level.PROJECT)
class OtelMetricService(project: Project, scope: CoroutineScope) {
    companion object {
        fun getInstance(project: Project) = project.service<OtelMetricService>()

        private val LOG = logger<OtelMetricService>()
    }

    init {
        scope.launch(Dispatchers.Default) {
            while (true) {
                delay(1.seconds)
                update()
            }
        }
    }

    private val lock = ReentrantReadWriteLock()
    private val metrics = mutableMapOf<String, MutableMap<Pair<String, String>, OtelMetric>>()
    private val otelMetricPublisher = project.messageBus.syncPublisher(OtelMetricListener.TOPIC)

    private fun update() {
        if (!AspireSettings.getInstance().collectTelemetry) return
        if (metrics.isEmpty()) return

        LOG.trace("Send metrics update")
        val otelMetrics = lock.read {
            metrics.toMap()
        }
        otelMetricPublisher.onMetricsUpdated(otelMetrics)
    }

    private fun addService(serviceName: String) {
        LOG.trace("Adding a new service $serviceName")
        lock.write {
            metrics.putIfAbsent(serviceName, mutableMapOf())
        }
    }

    private fun removeService(serviceName: String) {
        LOG.trace("Removing the service $serviceName")
        lock.write {
            metrics.remove(serviceName)
        }
    }

    fun metricReceived(metric: MetricBase) {
        LOG.trace("New metric received")
        lock.write {
            val serviceMetrics = metrics[metric.serviceName] ?: return@write

            val otelMetric = when (metric) {
                is MetricDouble -> {
                    OtelMetric(
                        metric.scope,
                        metric.name,
                        metric.description,
                        metric.unit,
                        metric.value,
                        metric.timeStamp
                    )
                }

                is MetricLong -> {
                    OtelMetric(
                        metric.scope,
                        metric.name,
                        metric.description,
                        metric.unit,
                        metric.value.toDouble(),
                        metric.timeStamp
                    )
                }

                else -> return
            }

            serviceMetrics[metric.scope to metric.name] = otelMetric
        }
    }

    class SessionListener(private val project: Project) : AspireSessionListener {
        override fun sessionStarted(otelServiceName: String) {
            getInstance(project).addService(otelServiceName)
        }

        override fun sessionTerminated(otelServiceName: String) {
            getInstance(project).removeService(otelServiceName)
        }
    }
}