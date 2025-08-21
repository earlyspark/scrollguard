package com.scrollguard.app.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.scrollguard.app.databinding.ItemAppSelectionBinding
import com.scrollguard.app.ui.model.AppInfo

/**
 * Adapter for app selection list in settings.
 * Displays social media apps with enable/disable switches.
 */
class AppSelectionAdapter(
    private val onAppToggled: (AppInfo, Boolean) -> Unit
) : ListAdapter<AppInfo, AppSelectionAdapter.AppViewHolder>(AppDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        val binding = ItemAppSelectionBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return AppViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class AppViewHolder(
        private val binding: ItemAppSelectionBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(appInfo: AppInfo) {
            binding.apply {
                appName.text = appInfo.appName
                appPackage.text = appInfo.packageName
                appIcon.setImageResource(appInfo.iconRes)
                appSwitch.isChecked = appInfo.isEnabled
                
                // Set content description for accessibility
                appIcon.contentDescription = root.context.getString(
                    com.scrollguard.app.R.string.cd_app_icon,
                    appInfo.appName
                )
                
                // Handle switch changes
                appSwitch.setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked != appInfo.isEnabled) {
                        onAppToggled(appInfo, isChecked)
                    }
                }
                
                // Handle row clicks to toggle switch
                root.setOnClickListener {
                    appSwitch.isChecked = !appSwitch.isChecked
                }
            }
        }
    }

    private class AppDiffCallback : DiffUtil.ItemCallback<AppInfo>() {
        override fun areItemsTheSame(oldItem: AppInfo, newItem: AppInfo): Boolean {
            return oldItem.packageName == newItem.packageName
        }

        override fun areContentsTheSame(oldItem: AppInfo, newItem: AppInfo): Boolean {
            return oldItem == newItem
        }
    }
}