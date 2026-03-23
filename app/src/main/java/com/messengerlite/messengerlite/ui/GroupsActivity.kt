package com.messengerlite.ui

import android.content.Intent
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.messengerlite.App
import com.messengerlite.R
import com.messengerlite.model.GroupInfo
import kotlin.concurrent.thread

class GroupsActivity : AppCompatActivity() {

    private lateinit var recycler: RecyclerView
    private lateinit var adapter: GroupAdapter
    private val groups = mutableListOf<GroupInfo>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_groups)

        title = "Groups"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        recycler = findViewById(R.id.recycler_groups)
        recycler.layoutManager = LinearLayoutManager(this)
        adapter = GroupAdapter(groups) { group ->
            val intent = Intent(this, ChatActivity::class.java)
            intent.putExtra("name", group.name)
            intent.putExtra("is_group", true)
            intent.putExtra("group_id", group.id)
            startActivity(intent)
        }
        recycler.adapter = adapter

        findViewById<Button>(R.id.btn_create_group).setOnClickListener { showCreateDialog() }
        findViewById<Button>(R.id.btn_join_group).setOnClickListener { showJoinDialog() }

        loadGroups()
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }

    private fun loadGroups() {
        thread {
            try {
                val result = App.api.getDiscoverableGroups()
                val items = result.optJSONArray("items")
                val list = mutableListOf<GroupInfo>()
                if (items != null) {
                    for (i in 0 until items.length()) {
                        val g = items.getJSONObject(i)
                        list.add(GroupInfo(
                            id = g.getInt("id"),
                            name = g.getString("name"),
                            type = g.optString("type", "public"),
                            joinCode = g.optString("join_code", null)
                        ))
                    }
                }
                runOnUiThread {
                    groups.clear()
                    groups.addAll(list)
                    adapter.notifyDataSetChanged()
                }
            } catch (e: Exception) {
                runOnUiThread { Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show() }
            }
        }
    }

    private fun showCreateDialog() {
        val input = EditText(this)
        input.hint = "Group name"
        AlertDialog.Builder(this)
            .setTitle("Create Group")
            .setView(input)
            .setPositiveButton("Create") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    thread {
                        try {
                            val result = App.api.createGroup(name)
                            runOnUiThread {
                                if (result.optString("status") == "success") {
                                    Toast.makeText(this, "Group created", Toast.LENGTH_SHORT).show()
                                    loadGroups()
                                } else {
                                    Toast.makeText(this, result.optString("error", "Failed"), Toast.LENGTH_SHORT).show()
                                }
                            }
                        } catch (e: Exception) {
                            runOnUiThread { Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show() }
                        }
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showJoinDialog() {
        val input = EditText(this)
        input.hint = "Join code"
        AlertDialog.Builder(this)
            .setTitle("Join Group")
            .setView(input)
            .setPositiveButton("Join") { _, _ ->
                val code = input.text.toString().trim()
                if (code.isNotEmpty()) {
                    thread {
                        try {
                            val result = App.api.joinGroup(code)
                            runOnUiThread {
                                if (result.optString("status") == "success") {
                                    Toast.makeText(this, "Joined!", Toast.LENGTH_SHORT).show()
                                    loadGroups()
                                } else {
                                    Toast.makeText(this, result.optString("error", "Failed"), Toast.LENGTH_SHORT).show()
                                }
                            }
                        } catch (e: Exception) {
                            runOnUiThread { Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show() }
                        }
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}

class GroupAdapter(
    private val items: List<GroupInfo>,
    private val onClick: (GroupInfo) -> Unit
) : RecyclerView.Adapter<GroupAdapter.VH>() {
    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tv_group_name)
        val tvType: TextView = view.findViewById(R.id.tv_group_type)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_group, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val g = items[position]
        holder.tvName.text = "# ${g.name}"
        holder.tvType.text = g.type
        holder.itemView.setOnClickListener { onClick(g) }
    }

    override fun getItemCount() = items.size
}
