package binderservice

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.IBinder
import android.os.IBinder.DeathRecipient
import android.os.Process
import androidx.annotation.Keep
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.core.os.HandlerCompat
import androidx.core.os.bundleOf
import androidx.startup.AppInitializer
import androidx.startup.Initializer
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume

@SuppressLint("DiscouragedPrivateApi", "PrivateApi")
class ServiceManager private constructor(context: Context) {
    companion object {
        private const val PATH = "application-binder-services.json"
        private const val KEY_NAME = "name"
        private const val KEY_BINDER = "binder"
        private const val KEY_CLASS = "class"
        private const val KEY_PROCESS = "process"

        suspend fun getService(name: String): IBinder? {
            return AppInitializer.getInstance(AppGlobals.application)
                .initializeComponent(Loader::class.java)
                .getService(name)
        }
    }

    @Keep
    internal class Loader : Initializer<ServiceManager> {
        override fun create(context: Context): ServiceManager {
            return ServiceManager(context)
        }

        override fun dependencies(): List<Class<out Initializer<*>>> {
            return emptyList()
        }
    }

    private inner class DeathCleaner(
        private val key: String,
        private val service: IBinder
    ) : DeathRecipient {
        override fun binderDied() {
            proxies.remove(key, service)
        }
    }

    init {
        AppGlobals.application = context.applicationContext as Application
    }

    private val scheduler = run {
        val queuedWork = Class.forName("android.app.QueuedWork")
            .getDeclaredMethod("getHandler")
            .apply {
                isAccessible = true
            }.invoke(null) as Handler
        HandlerCompat.createAsync(queuedWork.looper)
    }
    private val queryAction = AppGlobals.application.packageName + ".QUERY_BINDER_SERVICE"
    private val syncAction = AppGlobals.application.packageName + ".SYNC_BINDER_SERVICE"
    private val services by run {
        val loadServices = {
            val application = AppGlobals.application
            val shortProcessName = AppGlobals.processName.removePrefix(application.packageName)
            val services = JSONObject(
                application.assets.open(PATH).use { it.bufferedReader().readText() }
            )
            sequence {
                for (name in services.keys()) {
                    yield(name to services.getJSONObject(name))
                }
            }.mapNotNull {
                val (name, config) = it
                val process = config.getString(KEY_PROCESS)
                if (shortProcessName == process) {
                    val clazz = Class.forName(config.getString(KEY_CLASS))
                    val constructor = clazz.getConstructor()
                    name to lazy {
                        val binder = constructor.newInstance() as IBinder
                        val am = application.getSystemService<ActivityManager>()
                        if (am != null) {
                            application.sendBroadcast(
                                Intent(syncAction).apply {
                                    putExtras(
                                        bundleOf(
                                            KEY_NAME to name,
                                            KEY_BINDER to binder,
                                            KEY_PROCESS to AppGlobals.processName
                                        )
                                    )
                                },
                            )
                        }
                        return@lazy binder
                    }
                } else {
                    null
                }
            }.toMap()
        }
        if (AppGlobals.isDebuggable) {
            lazyOf(loadServices())
        } else {
            lazy(loadServices)
        }
    }
    private val proxies = ConcurrentHashMap<String, IBinder>()

    init {
        val application = AppGlobals.application
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    queryAction -> {
                        val name = intent.getStringExtra(KEY_NAME)
                        setResultExtras(bundleOf(KEY_BINDER to services[name]?.value))
                        abortBroadcast()
                    }

                    syncAction -> {
                        val extras = intent.extras
                        val process = extras?.getString(KEY_PROCESS)
                        if (AppGlobals.processName != process) {
                            val service = extras?.getBinder(KEY_BINDER)
                            val name = extras?.getString(KEY_NAME)
                            if (name != null && service != null) {
                                if (proxies.put(name, service) != service) {
                                    service.linkToDeath(DeathCleaner(name, service), 0)
                                }
                            }
                        }
                    }
                }
            }
        }
        arrayOf(
            IntentFilter().apply {
                addAction(queryAction)
                addCategory(AppGlobals.processName)
            },
            IntentFilter(syncAction)
        ).forEach {
            ContextCompat.registerReceiver(
                application,
                receiver,
                it,
                null,
                scheduler,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
        }
    }

    private suspend fun getServiceFromProcess(processName: String, name: String): IBinder? {
        return suspendCancellableCoroutine { con ->
            val application = AppGlobals.application
            application.sendOrderedBroadcast(
                Intent(queryAction).apply {
                    addCategory(processName)
                    `package` = application.packageName
                    putExtra(KEY_NAME, name)
                },
                null,
                object : BroadcastReceiver() {
                    override fun onReceive(context: Context, intent: Intent) {
                        con.runCatching {
                            resume(
                                getResultExtras(false)?.getBinder(KEY_BINDER)
                            )
                        }
                    }
                },
                scheduler,
                0,
                null,
                null
            )
        }
    }

    private suspend fun getServiceFromOtherProcesses(name: String): IBinder? {
        val application = AppGlobals.application
        val am = application.getSystemService<ActivityManager>() ?: return null
        return coroutineScope {
            val uid = Process.myUid()
            val pid = Process.myPid()
            val jobs = am.runningAppProcesses.asSequence().filter {
                it.uid == uid && it.pid != pid
            }.map {
                async { getServiceFromProcess(it.processName, name) }
            }.toMutableSet()
            while (jobs.isNotEmpty()) {
                val (job, service) = select {
                    jobs.forEach { job ->
                        job.onAwait { service ->
                            job to service
                        }
                    }
                }
                if (service == null) {
                    jobs.remove(job)
                } else {
                    if (proxies.put(name, service) != service) {
                        service.linkToDeath(DeathCleaner(name, service), 0)
                    }
                    return@coroutineScope service
                }
            }
            return@coroutineScope null
        }
    }

    private suspend fun getService(name: String): IBinder? {
        return services[name]?.value ?: proxies[name] ?: getServiceFromOtherProcesses(name)
    }

}