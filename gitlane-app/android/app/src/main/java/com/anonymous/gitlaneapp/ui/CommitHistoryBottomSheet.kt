package com.anonymous.gitlaneapp.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.anonymous.gitlaneapp.databinding.BottomsheetCommitHistoryBinding
import com.anonymous.gitlaneapp.databinding.ItemGhCommitBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL

class CommitHistoryBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomsheetCommitHistoryBinding? = null
    private val binding get() = _binding!!

    companion object {
        private const val ARG_REPO  = "repo"
        private const val ARG_TOKEN = "token"

        fun newInstance(repoFullName: String, token: String): CommitHistoryBottomSheet {
            return CommitHistoryBottomSheet().apply {
                arguments = Bundle().apply {
                    putString(ARG_REPO,  repoFullName)
                    putString(ARG_TOKEN, token)
                }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = BottomsheetCommitHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val repo  = arguments?.getString(ARG_REPO)  ?: return
        val token = arguments?.getString(ARG_TOKEN) ?: return

        binding.tvCommitRepoName.text = repo.substringAfter("/")
        binding.btnCloseCommits.setOnClickListener { dismiss() }

        val adapter = GhCommitAdapter()
        binding.rvCommits.layoutManager = LinearLayoutManager(requireContext())
        binding.rvCommits.adapter = adapter

        // Style the bottom sheet background
        dialog?.window?.setBackgroundDrawableResource(android.R.color.transparent)

        fetchCommits(repo, token, adapter)
    }

    private fun fetchCommits(repo: String, token: String, adapter: GhCommitAdapter) {
        binding.pbCommits.visibility  = View.VISIBLE
        binding.tvNoCommits.visibility = View.GONE

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val url  = URL("https://api.github.com/repos/$repo/commits?per_page=30")
                val conn = url.openConnection() as HttpURLConnection
                conn.setRequestProperty("Authorization", "token $token")
                conn.setRequestProperty("Accept", "application/vnd.github.v3+json")

                val body = conn.inputStream.bufferedReader().use { it.readText() }
                val arr  = JSONArray(body)

                val commits = mutableListOf<GhCommit>()
                for (i in 0 until arr.length()) {
                    val obj    = arr.getJSONObject(i)
                    val commit = obj.getJSONObject("commit")
                    val author = commit.optJSONObject("author")
                    commits.add(
                        GhCommit(
                            sha     = obj.getString("sha").take(7),
                            message = commit.getString("message").lines().first(),
                            author  = author?.optString("name", "Unknown") ?: "Unknown",
                            date    = author?.optString("date", "")?.take(10) ?: ""
                        )
                    )
                }

                withContext(Dispatchers.Main) {
                    binding.pbCommits.visibility = View.GONE
                    if (commits.isEmpty()) {
                        binding.tvNoCommits.visibility = View.VISIBLE
                    } else {
                        adapter.submitList(commits)
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.pbCommits.visibility = View.GONE
                    Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

// ─── Data class ──────────────────────────────────────────────────────────────

data class GhCommit(
    val sha:     String,
    val message: String,
    val author:  String,
    val date:    String
)

// ─── Adapter ─────────────────────────────────────────────────────────────────

class GhCommitAdapter : ListAdapter<GhCommit, GhCommitAdapter.VH>(Diff()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemGhCommitBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(b)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    inner class VH(private val b: ItemGhCommitBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(c: GhCommit) {
            b.tvGhCommitSha.text     = c.sha
            b.tvGhCommitMessage.text = c.message
            b.tvGhCommitAuthor.text  = "👤 ${c.author}"
            b.tvGhCommitDate.text    = c.date
        }
    }

    private class Diff : DiffUtil.ItemCallback<GhCommit>() {
        override fun areItemsTheSame(o: GhCommit, n: GhCommit) = o.sha == n.sha
        override fun areContentsTheSame(o: GhCommit, n: GhCommit) = o == n
    }
}
