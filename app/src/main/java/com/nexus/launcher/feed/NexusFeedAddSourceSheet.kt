package com.nexus.launcher.feed

import android.os.Bundle
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class NexusFeedAddSourceSheet : BottomSheetDialogFragment() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        dismiss()
        NexusFeedManageSourcesSheet().show(parentFragmentManager, NexusFeedManageSourcesSheet.TAG)
    }

    companion object {
        const val TAG = "NexusFeedAddSourceSheet"
    }
}
