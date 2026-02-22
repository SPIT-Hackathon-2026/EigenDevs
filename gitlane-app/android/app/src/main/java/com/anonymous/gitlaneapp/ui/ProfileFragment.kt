package com.anonymous.gitlaneapp.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import coil.load
import coil.transform.CircleCropTransformation
import com.anonymous.gitlaneapp.CredentialsManager
import com.anonymous.gitlaneapp.GitHubApiManager
import com.anonymous.gitlaneapp.R
import com.anonymous.gitlaneapp.data.SettingsRepository
import com.anonymous.gitlaneapp.databinding.FragmentProfileBinding
import kotlinx.coroutines.launch

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    private lateinit var credentialsManager: CredentialsManager
    private lateinit var settingsRepository: SettingsRepository

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        credentialsManager = CredentialsManager(requireContext())
        settingsRepository = SettingsRepository(requireContext())

        setupClickListeners()
        loadData()
    }

    override fun onResume() {
        super.onResume()
        // Reload auth state when returning from auth flow
        loadData()
    }

    private fun loadData() {
        val token = credentialsManager.getPat("github.com")
        if (token.isNullOrBlank()) {
            showUnauthenticated()
        } else {
            loadGitHubProfile(token)
        }
        loadGroqKeyState()
    }

    // ─── Auth State UI ────────────────────────────────────────────────────────

    private fun showUnauthenticated() {
        binding.layoutUnauthenticated.visibility = View.VISIBLE
        binding.layoutAuthenticated.visibility = View.GONE
        binding.tvGithubStatus.visibility = View.GONE
        animateFadeIn(binding.layoutUnauthenticated)
    }

    private fun showAuthenticated() {
        binding.layoutUnauthenticated.visibility = View.GONE
        binding.tvGithubStatus.visibility = View.VISIBLE
        binding.layoutAuthenticated.visibility = View.VISIBLE
        binding.layoutAuthenticated.alpha = 0f
        binding.layoutAuthenticated.animate()
            .alpha(1f)
            .setDuration(500)
            .setStartDelay(100)
            .start()
    }

    private fun animateFadeIn(v: View) {
        v.alpha = 0f
        v.animate().alpha(1f).setDuration(400).start()
    }

    // ─── GitHub Profile Loading ───────────────────────────────────────────────

    private fun loadGitHubProfile(token: String) {
        lifecycleScope.launch {
            try {
                val api = GitHubApiManager(token)
                val profile = api.getUserProfile()

                showAuthenticated()

                // Avatar
                binding.ivAvatar.load(profile.avatarUrl) {
                    crossfade(true)
                    transformations(CircleCropTransformation())
                    placeholder(android.R.drawable.ic_menu_myplaces)
                }

                // Name / username / bio
                binding.tvName.text = profile.name.ifBlank { profile.login }
                binding.tvUsername.text = "@${profile.login}"
                binding.tvBio.text = profile.bio
                binding.tvBio.visibility = if (profile.bio.isBlank()) View.GONE else View.VISIBLE

                // Stats — animate count-up
                animateCount(binding.tvPublicRepos, profile.publicRepos)
                animateCount(binding.tvPrivateRepos, profile.privateRepos)
                animateCount(binding.tvFollowers, profile.followers)
                animateCount(binding.tvFollowing, profile.following)

                // Real GitHub Activity Heatmap
                binding.tvActivityYear.text = java.util.Calendar.getInstance()
                    .get(java.util.Calendar.YEAR).toString()
                try {
                    val contributions = api.getUserContributions(profile.login)
                    // Remap data to week-major order (col × 7 + row) for the heatmap view
                    binding.heatmapView.setData(contributions)
                } catch (e: Exception) {
                    // Fallback: show empty grid, not fake data
                    binding.heatmapView.setData(IntArray(364) { 0 })
                }

            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Could not load profile: ${e.message}", Toast.LENGTH_SHORT).show()
                showUnauthenticated()
            }
        }
    }

    // ─── Groq Key Management ─────────────────────────────────────────────────

    private fun loadGroqKeyState() {
        val existing = settingsRepository.getGroqApiKey()
        if (!existing.isNullOrBlank()) {
            showGroqKeySet(existing)
        } else {
            showGroqKeyEmpty()
        }
    }

    private fun showGroqKeyEmpty() {
        binding.layoutGroqEmpty.visibility = View.VISIBLE
        binding.layoutGroqSet.visibility = View.GONE
        binding.etGroqKey.text?.clear()
    }

    private fun showGroqKeySet(key: String) {
        binding.layoutGroqEmpty.visibility = View.GONE
        binding.layoutGroqSet.visibility = View.VISIBLE
        // Show first 4 / last 4 chars masked
        val masked = if (key.length > 8)
            key.take(4) + " ●●●●●●●● " + key.takeLast(4)
        else "●●●●●●●●"
        binding.tvGroqKeyDisplay?.text = masked
    }

    // ─── Click Listeners ──────────────────────────────────────────────────────

    private fun setupClickListeners() {
        // Connect GitHub via OAuth
        binding.btnConnectGitHub.setOnClickListener {
            startActivity(Intent(requireContext(), GitHubOAuthActivity::class.java))
        }

        // Use PAT
        binding.btnSetPat.setOnClickListener {
            showPatDialog()
        }

        // Save Groq key
        binding.btnSaveGroqKey.setOnClickListener {
            val key = binding.etGroqKey.text?.toString()?.trim() ?: ""
            if (key.isBlank()) {
                Toast.makeText(requireContext(), "Please enter an API key", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            settingsRepository.saveGroqApiKey(key)
            binding.btnSaveGroqKey.animate().scaleX(0.95f).scaleY(0.95f).setDuration(80)
                .withEndAction {
                    binding.btnSaveGroqKey.animate().scaleX(1f).scaleY(1f).setDuration(80).start()
                    showGroqKeySet(key)
                    Toast.makeText(requireContext(), "✅ API Key Saved!", Toast.LENGTH_SHORT).show()
                }.start()
        }

        // Edit Groq key
        binding.btnEditGroqKey.setOnClickListener {
            showGroqKeyEmpty()
        }

        // Collaboration / notifications
        binding.btnCollaboration.setOnClickListener {
            startActivity(Intent(requireContext(), InvitationInboxActivity::class.java))
        }
    }

    private fun showPatDialog() {
        val editText = android.widget.EditText(requireContext()).apply {
            hint = "ghp_xxxxxxxxxxxx"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            setPadding(40, 32, 40, 32)
        }

        android.app.AlertDialog.Builder(requireContext())
            .setTitle("Personal Access Token")
            .setMessage("Enter your GitHub PAT to connect:")
            .setView(editText)
            .setPositiveButton("Save") { _, _ ->
                val pat = editText.text.toString().trim()
                if (pat.isNotBlank()) {
                    credentialsManager.savePat("github.com", pat)
                    Toast.makeText(requireContext(), "✅ Token Saved!", Toast.LENGTH_SHORT).show()
                    loadData()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ─── Animations ───────────────────────────────────────────────────────────

    /** Animates a TextView counting from 0 to [target] over ~600ms. */
    private fun animateCount(textView: android.widget.TextView, target: Int) {
        val steps = 20
        val delay = 600L / steps
        var current = 0
        textView.text = "0"
        val handler = android.os.Handler(android.os.Looper.getMainLooper())

        fun step() {
            current += (target / steps).coerceAtLeast(1)
            if (current >= target) {
                textView.text = target.toString()
            } else {
                textView.text = current.toString()
                handler.postDelayed({ step() }, delay)
            }
        }
        step()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
