package com.anonymous.gitlaneapp.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.anonymous.gitlaneapp.databinding.ItemGithubRepoBinding
import com.anonymous.gitlaneapp.ui.GithubRepo

class GithubRepoAdapter(
    private val onClone:         (GithubRepo) -> Unit,
    private val onCommitHistory: (GithubRepo) -> Unit
) : ListAdapter<GithubRepo, GithubRepoAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val b = ItemGithubRepoBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(b)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val b: ItemGithubRepoBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(repo: GithubRepo) {
            b.tvRepoName.text        = repo.name
            b.tvFullName.text        = repo.fullName
            b.tvRepoDescription.text = repo.description.ifBlank { "No description" }
            b.tvPrivateBadge.visibility = if (repo.isPrivate) View.VISIBLE else View.GONE

            // Language badge
            if (!repo.language.isNullOrBlank()) {
                b.tvLanguage.text       = repo.language
                b.tvLanguage.visibility = View.VISIBLE
            } else {
                b.tvLanguage.visibility = View.GONE
            }

            // Stars
            b.tvStars.text = "⭐ ${repo.starCount}"

            // Updated (show short date)
            b.tvUpdated.text = repo.updatedAt.take(10)

            // Buttons
            b.btnCloneLocally.setOnClickListener  { onClone(repo) }
            b.btnCommitHistory.setOnClickListener { onCommitHistory(repo) }
        }
    }

    private class DiffCallback : DiffUtil.ItemCallback<GithubRepo>() {
        override fun areItemsTheSame(o: GithubRepo, n: GithubRepo) = o.fullName == n.fullName
        override fun areContentsTheSame(o: GithubRepo, n: GithubRepo) = o == n
    }
}
