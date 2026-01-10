package com.example.workshoprobot

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import androidx.fragment.app.Fragment

class BigBlueButtonFragment : Fragment() {

    private lateinit var webView: WebView

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // This correctly inflates your fragment_bbb.xml layout
        return inflater.inflate(R.layout.fragment_bbb, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        webView = view.findViewById(R.id.webview_bbb)
        setupWebView()

        val meetingUrl = "https://demo.bigbluebutton.org/" // <-- IMPORTANT: Meeting URL here
        webView.loadUrl(meetingUrl)

        // Setup the back button from your layout
        val backButton = view.findViewById<Button>(R.id.btn_back_from_bbb)
        backButton.setOnClickListener {
            // This correctly navigates back, triggering the main menu to reappear
            requireActivity().supportFragmentManager.popBackStack()
        }
    }

    private fun setupWebView() {
        webView.webViewClient = WebViewClient() // Ensures links open inside the WebView
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true // Needed for modern web apps
        webView.settings.mediaPlaybackRequiresUserGesture = false

        // This is for camera/microphone access
        webView.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest?) {
                request?.grant(request.resources)
            }
        }
    }
}
