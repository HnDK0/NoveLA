package my.noveldokusha.extensions

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Switch
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import my.noveldokusha.core.Extension

class ExtensionsAdapter(
    private val onToggle: (extensionId: String, enabled: Boolean) -> Unit
) : ListAdapter<Extension, ExtensionsAdapter.ExtensionViewHolder>(ExtensionDiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ExtensionViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_extension, parent, false)
        return ExtensionViewHolder(view)
    }

    override fun onBindViewHolder(holder: ExtensionViewHolder, position: Int) {
        val extension = getItem(position)
        holder.bind(extension)
    }

    inner class ExtensionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val nameText: TextView = itemView.findViewById(R.id.extension_name)
        private val versionText: TextView = itemView.findViewById(R.id.extension_version)
        private val enableSwitch: Switch = itemView.findViewById(R.id.extension_switch)

        fun bind(extension: Extension) {
            nameText.text = extension.name
            versionText.text = "v${extension.version}"
            enableSwitch.isChecked = extension.enabled

            enableSwitch.setOnCheckedChangeListener { _, isChecked ->
                onToggle(extension.id, isChecked)
            }
        }
    }

    object ExtensionDiffCallback : DiffUtil.ItemCallback<Extension>() {
        override fun areItemsTheSame(oldItem: Extension, newItem: Extension): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Extension, newItem: Extension): Boolean {
            return oldItem == newItem
        }
    }
}
