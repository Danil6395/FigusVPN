package com.v2ray.ang.ui

import android.view.*
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.RecyclerView
import com.v2ray.ang.R
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.util.SmartServerUtils
import com.v2ray.ang.util.SmartServerUtils.ListItem

class MainRecyclerAdapter(private val activity: MainActivity) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var masterList = listOf<ListItem.Server>() // Все сервера
    private var displayList = mutableListOf<ListItem>() // То, что видим на экране
    private var expandedCountries = mutableSetOf<String>()
    private var searchQuery: String = ""

    fun updateData(guids: List<String>) {
        masterList = guids.map { guid ->
            val config = MmkvManager.decodeServerConfig(guid)
            val country = SmartServerUtils.getCountryName(config?.remarks ?: "")
            ListItem.Server(guid, country, config?.remarks ?: "")
        }
        filterAndGroup()
    }

    fun setSearch(query: String) {
        searchQuery = query
        filterAndGroup()
    }

    private fun filterAndGroup() {
        val filtered = if (searchQuery.isEmpty()) masterList 
        else masterList.filter { it.name.contains(searchQuery, true) || 
                MmkvManager.decodeServerConfig(it.guid)?.server?.contains(searchQuery) == true }

        val groups = filtered.groupBy { it.country }
        displayList.clear()

        groups.forEach { (country, servers) ->
            val isExpanded = expandedCountries.contains(country) || searchQuery.isNotEmpty()
            displayList.add(ListItem.Header(country, SmartServerUtils.getFlag(country), isExpanded, servers.size))
            if (isExpanded) {
                servers.forEachIndexed { index, server ->
                    displayList.add(server.copy(name = "${server.country} #${index + 1}"))
                }
            }
        }
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int) = if (displayList[position] is ListItem.Header) 0 else 1
    override fun getItemCount() = displayList.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == 0) {
            HeaderViewHolder(inflater.inflate(R.layout.item_recycler_header, parent, false))
        } else {
            ServerViewHolder(inflater.inflate(R.layout.item_recycler_main, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = displayList[position]
        if (holder is HeaderViewHolder && item is ListItem.Header) {
            holder.tvTitle.text = "${item.flag} ${item.country} (${item.count})"
            holder.arrow.rotation = if (item.isExpanded) 180f else 0f
            holder.itemView.setOnClickListener {
                if (item.isExpanded) expandedCountries.remove(item.country) 
                else expandedCountries.add(item.country)
                filterAndGroup()
            }
        } else if (holder is ServerViewHolder && item is ListItem.Server) {
            val config = MmkvManager.decodeServerConfig(item.guid)
            holder.tvName.text = item.name
            val delay = MmkvManager.decodeServerAffiliationInfo(item.guid)?.testDelayMillis ?: 0L
            holder.tvPing.text = if (delay > 0) "$delay ms" else "ping"
            
            val isSelected = item.guid == MmkvManager.getSelectServer()
            holder.indicator.visibility = if (isSelected) View.VISIBLE else View.INVISIBLE

            holder.itemView.setOnClickListener {
                MmkvManager.setSelectServer(item.guid)
                notifyDataSetChanged()
            }

            holder.btnInfo.setOnClickListener {
                val config = MmkvManager.decodeServerConfig(item.guid)
                // Используем стандартный стиль, чтобы не было ошибок
                AlertDialog.Builder(activity)
                    .setTitle("Node Details")
                    .setMessage("Address: ${config?.server}\nPort: ${config?.serverPort}\nProtocol: ${config?.configType}")
                    .setPositiveButton("OK", null)
                    .show()
            }
        }
    }

    class HeaderViewHolder(v: View) : RecyclerView.ViewHolder(v) {
        val tvTitle: TextView = v.findViewById(R.id.tv_header_title)
        val arrow: ImageView = v.findViewById(R.id.iv_arrow)
    }

    class ServerViewHolder(v: View) : RecyclerView.ViewHolder(v) {
        val tvName: TextView = v.findViewById(R.id.tv_name)
        val tvPing: TextView = v.findViewById(R.id.tv_test_result)
        val indicator: View = v.findViewById(R.id.layout_indicator)
        val btnInfo: View = v.findViewById(R.id.btn_info_trigger)
    }
}