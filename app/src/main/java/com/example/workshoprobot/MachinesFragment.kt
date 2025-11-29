package com.example.workshoprobot

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.fragment.app.Fragment

class MachinesFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_machines, container, false)
    }

    // ADD THIS ENTIRE METHOD
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Find the back button from the layout
        val backButton = view.findViewById<Button>(R.id.btn_back_to_home_machines)

        // Set a click listener on it
        backButton.setOnClickListener {
            // Remove this fragment from the screen
            parentFragmentManager.beginTransaction().remove(this).commit()

            // Optional: Announce the action
            (activity as? MainActivity)?.speakOut("Returning to the home screen.")
        }
    }
}
