package com.messengerlite.ui

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.messengerlite.App
import com.messengerlite.R
import com.messengerlite.model.Message
import org.json.JSONArray
import kotlin.concurrent.thread

class ChatActivity : AppCompatActivity() {

    private lateinit var recycler: RecyclerView
    private lateinit var etMessage: EditText
    private lateinit var btnSend: ImageButton
    private lateinit var tvTyping: TextView
    private lateinit var adapter: MessageAdapter

    private val messages = mutableListOf<Message>()
    private var chatName = ""
    private var isGroup = false
    private var groupId: Int? = null
    private var polling = false
    private val ackDms = mutableListOf<Int>()
    private var groupCursor = 0
    private var lastTypingSent = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        chatName = intent.getStringExtra("name") ?: ""
        isGroup = intent.getBooleanExtra("is_group", false)
        groupId = if (intent.hasExtra("group_id")) intent.getIntExtra("group_id", 0) else null

        title = if (isGroup) "# $chatName" else chatName
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        recycler = findViewById(R.id.recycler_messages)
        etMessage = findViewById(R.id.et_message)
        btnSend = findViewById(R.id.btn_send)
        tvTyping = findViewById(R.id.tv_typing)

        adapter = MessageAdapter(messages, App.currentUser)
        val lm = LinearLayoutManager(this)
        lm.stackFromEnd = true
        recycler.layoutManager = lm
        recycler.adapter = adapter

        btnSend.setOnClickListener { sendMessage() }

        etMessage.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (!isGroup && System.currentTimeMillis() - lastTypingSent > 2000) {
                    lastTypingSent = System.currentTimeMillis()
                    thread { try { App.api.sendTyping(chatName) } catch (_: Exception) {} }
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        startPolling()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun sendMessage() {
        val text = etMessage.text.toString().trim()
        if (text.isEmpty()) return

        etMessage.setText("")
        thread {
            try {
                val result = if (isGroup) {
                    App.api.sendMessage(text, groupId = groupId)
                } else {
                    App.api.sendMessage(text, toUser = chatName)
                }
                if (result.optString("status") != "success") {
                    runOnUiThread {
                        Toast.makeText(this, result.optString("error", "Send failed"), Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this, "Send failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun startPolling() {
        polling = true
        thread {
            while (polling) {
                try {
                    doPoll()
                } catch (_: Exception) {}
                Thread.sleep(1500)
            }
        }
    }

    private fun doPoll() {
        val cursors = if (groupId != null) mapOf(groupId.toString() to groupCursor) else emptyMap()
        val result = App.api.poll(ackDms = ackDms.toList(), groupCursors = cursors)
        ackDms.clear()

        val newMessages = mutableListOf<Message>()

        if (isGroup && groupId != null) {
            val groupMsgs = result.optJSONObject("group_msgs")
            if (groupMsgs != null && groupMsgs.has(groupId.toString())) {
                val msgs = groupMsgs.getJSONArray(groupId.toString())
                parseMessages(msgs, newMessages)
                if (msgs.length() > 0) {
                    groupCursor = msgs.getJSONObject(msgs.length() - 1).getInt("id")
                }
            }
        } else {
            val dms = result.optJSONArray("dms")
            if (dms != null) {
                for (i in 0 until dms.length()) {
                    val msg = dms.getJSONObject(i)
                    val from = msg.getString("from_user")
                    val to = msg.optString("to_user", "")
                    if (from == chatName || to == chatName) {
                        newMessages.add(Message(
                            id = msg.getInt("id"),
                            fromUser = from,
                            toUser = to,
                            groupId = null,
                            message = msg.optString("message", ""),
                            type = msg.optString("type", "text"),
                            timestamp = msg.optLong("timestamp", 0),
                            replyToId = if (msg.has("reply_to_id")) msg.optInt("reply_to_id") else null
                        ))
                        ackDms.add(msg.getInt("id"))
                    }
                }
            }

            // Check typing
            val typing = result.optJSONArray("typing")
            runOnUiThread {
                if (typing != null) {
                    var isTyping = false
                    for (i in 0 until typing.length()) {
                        if (typing.getString(i) == chatName) {
                            isTyping = true
                            break
                        }
                    }
                    tvTyping.visibility = if (isTyping) View.VISIBLE else View.GONE
                    tvTyping.text = "$chatName is typing..."
                } else {
                    tvTyping.visibility = View.GONE
                }
            }
        }

        if (newMessages.isNotEmpty()) {
            runOnUiThread {
                messages.addAll(newMessages)
                adapter.notifyItemRangeInserted(messages.size - newMessages.size, newMessages.size)
                recycler.scrollToPosition(messages.size - 1)
            }
        }
    }

    private fun parseMessages(arr: JSONArray, out: MutableList<Message>) {
        for (i in 0 until arr.length()) {
            val msg = arr.getJSONObject(i)
            out.add(Message(
                id = msg.getInt("id"),
                fromUser = msg.optString("from_user", ""),
                toUser = msg.optString("to_user", null),
                groupId = msg.optInt("group_id", 0),
                message = msg.optString("message", ""),
                type = msg.optString("type", "text"),
                timestamp = msg.optLong("timestamp", 0),
                replyToId = if (msg.has("reply_to_id")) msg.optInt("reply_to_id") else null
            ))
        }
    }

    override fun onDestroy() {
        polling = false
        super.onDestroy()
    }
}

class MessageAdapter(
    private val items: List<Message>,
    private val currentUser: String
) : RecyclerView.Adapter<MessageAdapter.VH>() {

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tvSender: TextView = view.findViewById(R.id.tv_msg_sender)
        val tvMessage: TextView = view.findViewById(R.id.tv_msg_text)
        val tvTime: TextView = view.findViewById(R.id.tv_msg_time)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_message, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val msg = items[position]
        val isMe = msg.fromUser == currentUser
        holder.tvSender.text = if (isMe) "You" else msg.fromUser
        holder.tvSender.setTextColor(if (isMe) 0xFF7C4DFF.toInt() else 0xFF9C27B0.toInt())
        holder.tvMessage.text = msg.message

        val time = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
            .format(java.util.Date(msg.timestamp))
        holder.tvTime.text = time

        // Align message
        val params = holder.itemView.layoutParams as RecyclerView.LayoutParams
        if (isMe) {
            params.marginStart = 80
            params.marginEnd = 16
        } else {
            params.marginStart = 16
            params.marginEnd = 80
        }
        holder.itemView.layoutParams = params
    }

    override fun getItemCount() = items.size
}
