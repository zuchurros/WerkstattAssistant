package com.example.workshoprobot

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.fragment.app.Fragment

class MqttControlFragment : Fragment() {

    private lateinit var webView: WebView

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_mqtt_control, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        webView = view.findViewById(R.id.webview_mqtt)
        setupWebView()

        // Load the local HTML file from the assets folder
        webView.loadUrl("file:///android_asset/mqtt_control/index.html")
    }

    private fun setupWebView() {
        webView.webViewClient = WebViewClient() // Ensures links open inside the WebView
        webView.settings.javaScriptEnabled = true // Essential for MQTT client
        webView.settings.domStorageEnabled = true // Often needed for modern web apps
    }
}
