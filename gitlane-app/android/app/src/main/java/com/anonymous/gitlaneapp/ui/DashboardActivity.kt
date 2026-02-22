package com.anonymous.gitlaneapp.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.anonymous.gitlaneapp.R
import com.anonymous.gitlaneapp.databinding.ActivityDashboardBinding

class DashboardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDashboardBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupNavigation()

        // Set default fragment
        if (savedInstanceState == null) {
            loadFragment(HomeFragment())
        }
    }

    private fun setupNavigation() {
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            val fragment: Fragment = when (item.itemId) {
                R.id.nav_home     -> HomeFragment()
                R.id.nav_search   -> SearchFragment()
                R.id.nav_chatbot  -> ChatbotFragment()
                R.id.nav_profile  -> ProfileFragment()
                else              -> HomeFragment()
            }
            loadFragment(fragment)
            true
        }
    }

    fun switchToTab(tabIndex: Int) {
        val itemId = when (tabIndex) {
            0    -> R.id.nav_home
            1    -> R.id.nav_search
            2    -> R.id.nav_chatbot
            3    -> R.id.nav_profile
            else -> R.id.nav_home
        }
        binding.bottomNavigation.selectedItemId = itemId
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.nav_host_fragment, fragment)
            .commit()
    }
}
