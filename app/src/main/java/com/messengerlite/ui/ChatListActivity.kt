package com.messengerlite.ui

import android.content.Intent
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.messengerlite.App
import com.messengerlite.R
import com.messengerlite.model.Conversation
import org.json.JSONObject
import kotlin.concurrent.thread

class ChatListActivity : AppCompatActivity() {

    private lateinit var recycler: RecyclerView
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var tvEmpty: TextView
    private lateinit var adapter: ConversationAdapter
    private val conversations = mutableListOf<Conversation>()
    private var polling = false
    private val groupCursors = mutableMapOf<String, Int>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat_list)

        title = "Messenger Lite"
        supportActionBar?.subtitle = App.currentUser

        recycler = findViewById(R.id.recycler_chats)
        swipeRefresh = findViewById(R.id.swipe_refresh)
        tvEmpty = findViewById(R.id.tv_empty)

        adapter = ConversationAdapter(conversations) { conv ->
            val intent = Intent(this, ChatActivity::class.java)
            intent.putExtra("name", conv.name)
            intent.putExtra("is_group", conv.isGroup)
            conv.groupId?.let { intent.putExtra("group_id", it) }
            startActivity(intent)
        }
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter

        swipeRefresh.setOnRefreshListener { pollOnce() }

        startPolling()
    }

    override fun onResume() {
        super.onResume()
        pollOnce()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_chat_list, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_new_dm -> { showNewDmDialog(); true }
            R.id.action_groups -> {
                startActivity(Intent(this, GroupsActivity::class.java))
                true
            }
            R.id.action_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }
            R.id.action_logout -> { doLogout(); true }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showNewDmDialog() {
        val input = EditText(this)
        input.hint = "Username"
        AlertDialog.Builder(this)
            .setTitle("New Direct Message")
            .setView(input)
            .setPositiveButton("Chat") { _, _ ->
                val user = input.text.toString().trim()
                if (user.isNotEmpty() && user != App.currentUser) {
                    val intent = Intent(this, ChatActivity::class.java)
                    intent.putExtra("name", user)
                    intent.putExtra("is_group", false)
                    startActivity(intent)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun startPolling() {
        if (polling) return
        polling = true
        thread {
            while (polling) {
                try {
                    doPoll()
                } catch (_: Exception) {}
                Thread.sleep(3000)
            }
        }
    }

    private fun pollOnce() {
        thread {
            try {
                doPoll()
            } catch (_: Exception) {}
            runOnUiThread { swipeRefresh.isRefreshing = false }
        }
    }

    private fun doPoll() {
        val result = App.api.poll(groupCursors = groupCursors)
        if (result.optString("status", "") == "error") return

        val newConversations = mutableListOf<Conversation>()

        // Parse DMs
        val dms = result.optJSONArray("dms")
        val onlineList = result.optJSONArray("online")
        val onlineSet = mutableSetOf<String>()
        if (onlineList != null) {
            for (i in 0 until onlineList.length()) {
                onlineSet.add(onlineList.getString(i))
            }
        }

        val dmUsers = mutableMapOf<String, Conversation>()
        if (dms != null) {
            for (i in 0 until dms.length()) {
                val msg = dms.getJSONObject(i)
                val from = msg.getString("from_user")
                val otherUser = if (from == App.currentUser) msg.optString("to_user", "") else from
                if (otherUser.isEmpty()) continue
                val existing = dmUsers[otherUser]
                val ts = msg.optLong("timestamp", 0)
                if (existing == null || ts > existing.lastTimestamp) {
                    dmUsers[otherUser] = Conversation(
                        name = otherUser,
                        isGroup = false,
                        lastMessage = msg.optString("message", ""),
                        lastTimestamp = ts,
                        unreadCount = (existing?.unreadCount ?: 0) + 1,
                        isOnline = onlineSet.contains(otherUser)
                    )
                }
            }
        }
        newConversations.addAll(dmUsers.values)

        // Parse groups
        val groups = result.optJSONArray("groups")
        if (groups != null) {
            for (i in 0 until groups.length()) {
                val g = groups.getJSONObject(i)
                val gid = g.getInt("id")
                val gName = g.getString("name")
                var lastMsg = ""
                var lastTs = 0L
                var unread = 0

                val groupMsgs = result.optJSONObject("group_msgs")
                if (groupMsgs != null && groupMsgs.has(gid.toString())) {
                    val msgs = groupMsgs.getJSONArray(gid.toString())
                    if (msgs.length() > 0) {
                        val last = msgs.getJSONObject(msgs.length() - 1)
                        lastMsg = "${last.optString("from_user")}: ${last.optString("message")}"
                        lastTs = last.optLong("timestamp", 0)
                        unread = msgs.length()
                        // Update cursor
                        groupCursors[gid.toString()] = last.getInt("id")
                    }
                }

                newConversations.add(Conversation(
                    name = gName,
                    isGroup = true,
                    groupId = gid,
                    lastMessage = lastMsg,
                    lastTimestamp = lastTs,
                    unreadCount = unread
                ))
            }
        }

        // Add existing conversations that have no new messages
        for (existing in conversations) {
            if (!existing.isGroup && !dmUsers.containsKey(existing.name)) {
                newConversations.add(existing.copy(unreadCount = 0))
            }
        }

        newConversations.sortByDescending { it.lastTimestamp }

        runOnUiThread {
            conversations.clear()
            conversations.addAll(newConversations)
            adapter.notifyDataSetChanged()
            tvEmpty.visibility = if (conversations.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun doLogout() {
        thread {
            try { App.api.logout() } catch (_: Exception) {}
        }
        App.clearLogin()
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }

    override fun onDestroy() {
        polling = false
        super.onDestroy()
    }
}

class ConversationAdapter(
    private val items: List<Conversation>,
    private val onClick: (Conversation) -> Unit
) : RecyclerView.Adapter<ConversationAdapter.VH>() {

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tv_conv_name)
        val tvLast: TextView = view.findViewById(R.id.tv_conv_last)
        val tvBadge: TextView = view.findViewById(R.id.tv_conv_badge)
        val tvOnline: View = view.findViewById(R.id.view_online)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_conversation, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val conv = items[position]
        holder.tvName.text = if (conv.isGroup) "# ${conv.name}" else conv.name
        holder.tvLast.text = conv.lastMessage.ifEmpty { "No messages yet" }
        holder.tvBadge.visibility = if (conv.unreadCount > 0) View.VISIBLE else View.GONE
        holder.tvBadge.text = conv.unreadCount.toString()
        holder.tvOnline.visibility = if (!conv.isGroup && conv.isOnline) View.VISIBLE else View.GONE
        holder.itemView.setOnClickListener { onClick(conv) }
    }

    override fun getItemCount() = items.size
}
