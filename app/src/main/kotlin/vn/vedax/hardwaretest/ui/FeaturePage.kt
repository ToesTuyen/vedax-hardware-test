package vn.vedax.hardwaretest.ui

import android.widget.LinearLayout

interface FeaturePage {
    fun show(parent: LinearLayout)
    fun onResume() = Unit
    fun onPause() = Unit
    fun onPermissionResult(requestCode: Int, grantResults: IntArray) = Unit
}
