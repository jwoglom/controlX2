package com.jwoglom.wearx2

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.wear.widget.WearableLinearLayoutManager
import androidx.wear.widget.WearableRecyclerView


class BolusActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_bolus)
        val recyclerView = findViewById<WearableRecyclerView>(R.id.activity_bolus_view)
        recyclerView.setHasFixedSize(true)
        recyclerView.isEdgeItemsCenteringEnabled = true
        recyclerView.layoutManager = WearableLinearLayoutManager(this)

        val menuItems: ArrayList<MenuItem> = ArrayList()
        menuItems.add(MenuItem(androidx.wear.R.drawable.open_on_phone, "Item 1"))
        menuItems.add(MenuItem(androidx.wear.R.drawable.open_on_phone, "Item 2"))
        menuItems.add(MenuItem(androidx.wear.R.drawable.open_on_phone, "Item 3"))
        menuItems.add(MenuItem(androidx.wear.R.drawable.open_on_phone, "Item 4"))

        recyclerView.adapter = MainMenuAdapter(this, menuItems, object :
            MainMenuAdapter.AdapterCallback {
                override fun onItemClicked(menuPosition: Int?) {
                    when (menuPosition) {
                    }
                }
            }
        )
    }

    class MainMenuAdapter(
        context: Context,
        dataArgs: ArrayList<MenuItem>,
        callback: AdapterCallback?
    ) :
        RecyclerView.Adapter<MainMenuAdapter.RecyclerViewHolder?>() {
        private var dataSource = ArrayList<MenuItem>()

        interface AdapterCallback {
            fun onItemClicked(menuPosition: Int?)
        }

        private val callback: AdapterCallback?
        private val drawableIcon: String? = null
        private val context: Context

        init {
            this.context = context
            dataSource = dataArgs
            this.callback = callback
        }

        override fun onCreateViewHolder(
            parent: ViewGroup,
            viewType: Int
        ): RecyclerViewHolder {
            val view: View = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.menu_container, parent, false)
            return RecyclerViewHolder(view)
        }

        class RecyclerViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            var menuContainer: RelativeLayout
            var menuItem: TextView
            var menuIcon: ImageView

            init {
                menuContainer = view.findViewById(R.id.menu_container)
                menuItem = view.findViewById(R.id.menu_item)
                menuIcon = view.findViewById(R.id.menu_icon)
            }
        }

        override fun onBindViewHolder(holder: RecyclerViewHolder, position: Int) {
            val data_provider = dataSource[position]
            holder.menuItem.setText(data_provider.text)
            holder.menuIcon.setImageResource(data_provider.image)
            holder.menuContainer.setOnClickListener {
                callback?.onItemClicked(position)
            }
        }

        override fun getItemCount(): Int {
            return dataSource.size
        }
    }

    class MenuItem(val image: Int, val text: String)
}