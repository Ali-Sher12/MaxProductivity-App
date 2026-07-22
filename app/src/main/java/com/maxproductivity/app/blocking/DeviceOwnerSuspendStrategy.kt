package com.maxproductivity.app.blocking

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import com.maxproductivity.app.receiver.MyDeviceAdminReceiver

class DeviceOwnerSuspendStrategy : BlockStrategy {

    override fun blockPackage(context: Context, packageName: String, message: String) {
        updateSuspendedPackages(context, setOf(packageName), suspend = true)
    }

    override fun updateSuspendedPackages(context: Context, packages: Set<String>, suspend: Boolean) {
        val dpm = context.getSystemService(DevicePolicyManager::class.java) ?: return
        if (!dpm.isDeviceOwnerApp(context.packageName)) return

        val admin = ComponentName(context, MyDeviceAdminReceiver::class.java)
        dpm.setPackagesSuspended(admin, packages.toTypedArray(), suspend)
    }
}
