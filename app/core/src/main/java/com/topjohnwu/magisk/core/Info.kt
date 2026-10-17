package com.topjohnwu.magisk.core

import android.app.KeyguardManager
import android.content.pm.ApplicationInfo
import android.os.Build
import androidx.lifecycle.MutableLiveData
import com.topjohnwu.magisk.StubApk
import com.topjohnwu.magisk.core.ktx.getProperty
import com.topjohnwu.magisk.core.model.UpdateInfo
import com.topjohnwu.magisk.core.repository.NetworkService
import com.topjohnwu.superuser.CallbackList
import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.ShellUtils.fastCmd
import com.topjohnwu.superuser.ShellUtils.fastCmdResult
import kotlinx.coroutines.Runnable

val isRunningAsStub get() = Info.stub != null

object Info {

    var stub: StubApk.Data? = null

    private val EMPTY_UPDATE = UpdateInfo()
    var update = EMPTY_UPDATE
        private set

    suspend fun fetchUpdate(svc: NetworkService): UpdateInfo? {
        return if (update === EMPTY_UPDATE) {
            svc.fetchUpdate()?.apply { update = this }
        } else update
    }

    fun resetUpdate() {
        update = EMPTY_UPDATE
    }

    var isRooted = false
    var noDataExec = false
    var patchBootVbmeta = false

    @JvmStatic var env = Env()
        private set
    @JvmStatic var isSAR = false
        private set
    var legacySAR = false
        private set
    var isAB = false
        private set
    var slot = ""
        private set
    var isVendorBoot = false
        private set
    @JvmField val isZygiskEnabled = System.getenv("ZYGISK_ENABLED") == "1"
    @JvmStatic val isFDE get() = crypto == "block"
    @JvmStatic var ramdisk = false
        private set
    private var crypto = ""

    val isEmulator =
        Build.DEVICE.contains("vsoc")
            || getProperty("ro.kernel.qemu", "0") == "1"
            || getProperty("ro.boot.qemu", "0") == "1"

    val isHeadlessSystemUser by lazy {
        getProperty("ro.fw.mu.headless_system_user", "false").toBoolean()
    }

    val isConnected = MutableLiveData(false)

    val showSuperUser: Boolean get() {
        return env.isActive && (Const.USER_ID == 0
                || Config.suMultiuserMode == Config.Value.MULTIUSER_MODE_USER)
    }

    val isDeviceSecure get() =
        AppContext.getSystemService(KeyguardManager::class.java).isDeviceSecure

    class Env(
        val versionString: String = "",
        val isDebug: Boolean = false,
        code: Int = -1
    ) {
        // paperSU: 原来这里有个 MIN_VERCODE 门槛，低于它就判定"不受支持"。
        //
        // 那是给 Magisk 官方发行版用的号段检查。纸SU 用的是自己编的 daemon，
        // build.py 把 versionCode 写死成 1000000，跟那个号段本来就没关系；
        // 而对不上就会让界面弹出"需要修复运行环境"。
        //
        // 纸SU 判断环境可用与否只看两件事：拿得到 root，daemon 自报了版本号。
        val versionCode = if (isRooted) code else -1

        // paperSU: 纸SU 没有"daemon 版本不受支持"这种概念，恒 false。
        val isUnsupported = false
        val isActive = versionCode > 0
    }

    /**
     * paperSU: 自己检测 root。
     *
     * 不问 magisk，也不依赖 libsu 的 shell.isRoot —— 后者是 libsu 自己 spawn su 探测的，
     * 在纸SU 的 Magisk 模式（4.x 机器 + 系统里那份 su）上会给出 false，整条链路于是断掉。
     *
     * 判定就一句话：真跑一次 id，看回来的是不是 uid=0。
     */
    fun detectRoot(shell: Shell): Boolean {
        fun isRootId(s: String) = s.contains("uid=0")

        // 1) libsu 已经确认是 root，直接算通
        runCatching { if (shell.isRoot) return true }.getOrNull()

        // 2) 真跑一次 id
        runCatching { if (isRootId(fastCmd(shell, "id"))) return true }.getOrNull()

        // 3) 直接 spawn su 再试一次（系统里的 su 不一定在 libsu 的搜索路径里）
        runCatching { if (isRootId(fastCmd("su -c id"))) return true }.getOrNull()

        return false
    }

    /** paperSU: 纸SU 自己的版本信息（不再解析 magisk 的号段）。 */
    private fun selfVersion(): Pair<String, Int> = runCatching {
        val pm = AppContext.packageManager
        val pi = pm.getPackageInfo(AppContext.packageName, 0)
        val name = pi.versionName ?: ""
        val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            pi.longVersionCode.toInt()
        } else {
            @Suppress("DEPRECATION")
            pi.versionCode
        }
        name to code
    }.getOrDefault("" to 0)

    private fun selfIsDebuggable(): Boolean = runCatching {
        (AppContext.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    }.getOrDefault(false)

    fun init(shell: Shell) {
        // paperSU: root 由我们自己判断。
        isRooted = detectRoot(shell)

        val (selfName, selfCode) = selfVersion()
        env = Env(
            versionString = selfName.ifBlank { "PaperSU" },
            isDebug = selfIsDebuggable(),
            code = if (isRooted) selfCode else -1
        )

        // DenyList 是 Magisk 的概念，纸SU 不读它，保持默认 false。
        Config.denyList = false

        val map = mutableMapOf<String, String>()
        val list = object : CallbackList<String>(Runnable::run) {
            override fun onAddElement(e: String) {
                val split = e.split("=")
                if (split.size >= 2) {
                    map[split[0]] = split[1]
                }
            }
        }
        shell.newJob().add("(app_init)").to(list).exec()

        fun getVar(name: String) = map[name] ?: ""
        fun getBool(name: String) = map[name].toBoolean()

        isSAR = getBool("SYSTEM_AS_ROOT")
        ramdisk = getBool("RAMDISKEXIST")
        isAB = getBool("ISAB")
        patchBootVbmeta = getBool("PATCHVBMETAFLAG")
        crypto = getVar("CRYPTOTYPE")
        slot = getVar("SLOT")
        legacySAR = getBool("LEGACYSAR")
        isVendorBoot = getBool("VENDORBOOT")

        // Default presets
        Config.recovery = getBool("RECOVERYMODE")
        Config.keepVerity = getBool("KEEPVERITY")
        Config.keepEnc = getBool("KEEPFORCEENCRYPT")
    }
}
