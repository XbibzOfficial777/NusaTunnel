package com.nusa.tunnel.core

import android.content.Context
import android.content.Intent
import java.util.Locale

/** Application inclusion policy passed to Android's VpnService.Builder. */
enum class VpnAppRoutingMode(val preferenceValue: String, val label: String) {
    AllApps("all_apps", "All apps"),
    SelectedOnly("selected_only", "Only selected apps"),
    ExcludeSelected("exclude_selected", "All except selected");

    companion object {
        fun fromPreference(value: String?): VpnAppRoutingMode =
            entries.firstOrNull { it.preferenceValue == value } ?: AllApps
    }
}

data class VpnAppRoutingSettings(
    val mode: VpnAppRoutingMode = VpnAppRoutingMode.AllApps,
    val selectedPackages: Set<String> = emptySet(),
)

data class VpnAppInfo(val packageName: String, val label: String)

data class VpnAppRoutingPlan(
    /** null means Android's default route for every app not explicitly disallowed. */
    val allowedPackages: Set<String>?,
    val disallowedPackages: Set<String>,
)

/** Build and validate an explicit package policy; the VPN owner's sockets are never tunneled. */
object VpnAppRoutingPlanner {
    private val packageNamePattern = Regex("[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+")

    fun isValidPackageName(packageName: String): Boolean =
        packageName.length <= 255 && packageNamePattern.matches(packageName)

    fun create(
        settings: VpnAppRoutingSettings,
        ownerPackageName: String,
        installedPackages: Set<String>,
    ): VpnAppRoutingPlan {
        require(isValidPackageName(ownerPackageName)) { "Invalid VPN owner package name" }
        require(settings.selectedPackages.all(::isValidPackageName)) {
            "Selected application package name is invalid"
        }
        require(ownerPackageName !in settings.selectedPackages) {
            "NusaTunnel cannot be selected for its own VPN tunnel"
        }

        if (settings.mode != VpnAppRoutingMode.AllApps) {
            val missing = settings.selectedPackages - installedPackages
            require(missing.isEmpty()) {
                "Selected applications are no longer installed: ${missing.sorted().joinToString()}"
            }
        }

        return when (settings.mode) {
            VpnAppRoutingMode.AllApps -> VpnAppRoutingPlan(
                allowedPackages = null,
                disallowedPackages = setOf(ownerPackageName),
            )
            VpnAppRoutingMode.SelectedOnly -> {
                require(settings.selectedPackages.isNotEmpty()) {
                    "Select at least one installed app for app-only VPN mode"
                }
                VpnAppRoutingPlan(
                    allowedPackages = settings.selectedPackages,
                    disallowedPackages = emptySet(),
                )
            }
            VpnAppRoutingMode.ExcludeSelected -> VpnAppRoutingPlan(
                allowedPackages = null,
                disallowedPackages = settings.selectedPackages + ownerPackageName,
            )
        }
    }
}

/** Visible launchable applications; the manifest declares the corresponding package query. */
object VpnApplicationCatalog {
    fun launchableApps(context: Context): List<VpnAppInfo> {
        val packageManager = context.packageManager
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return packageManager.queryIntentActivities(launcherIntent, 0)
            .mapNotNull { resolve ->
                val activity = resolve.activityInfo ?: return@mapNotNull null
                val packageName = activity.packageName
                if (packageName == context.packageName) return@mapNotNull null
                val label = runCatching { activity.loadLabel(packageManager).toString().trim() }
                    .getOrDefault(packageName)
                    .ifBlank { packageName }
                VpnAppInfo(packageName, label)
            }
            .distinctBy(VpnAppInfo::packageName)
            .sortedWith(compareBy<VpnAppInfo> { it.label.lowercase(Locale.ROOT) }.thenBy { it.packageName })
    }
}
