package com.example.workshoprobot

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.fragment.app.Fragment

class AiAssistantFragment : Fragment() {

    private lateinit var chatContainer: LinearLayout
    private lateinit var scrollView: ScrollView

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_ai_assistant, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        chatContainer = view.findViewById(R.id.chat_container)
        scrollView = view.findViewById(R.id.scroll_view_chat)

        // Load chat history when the fragment is created
        loadChatHistory()
    }

    private fun loadChatHistory() {
        val mainActivity = activity as? MainActivity
        mainActivity?.chatHistory?.forEach {
            addMessageToChat(it.first, it.second)
        }
    }

    // This public method allows MainActivity to add new messages to the chat
    fun onNewMessage() {
        chatContainer.removeAllViews() // Clear existing views to prevent duplicates
        loadChatHistory() // Reload the entire history

        // Auto-scroll to the bottom to show the latest message
        scrollView.post { scrollView.fullScroll(View.FOCUS_DOWN) }
    }

    private fun addMessageToChat(message: String, isUser: Boolean) {
        val inflater = LayoutInflater.from(context)
        val bubbleLayoutId = if (isUser) R.layout.bubble_user else R.layout.bubble_ai

        // Inflate the correct bubble layout
        val bubbleView = inflater.inflate(bubbleLayoutId, chatContainer, false)

        // Find the TextView inside the bubble and set its text
        val messageTextView = bubbleView.findViewById<TextView>(R.id.text_message_body)
        messageTextView.text = message

        // Add the newly created bubble view to our chat container
        chatContainer.addView(bubbleView)
    }
}
