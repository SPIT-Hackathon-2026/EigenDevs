package com.anonymous.gitlaneapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anonymous.gitlaneapp.engine.CommitGraphEngine

/**
 * Pro Commit List Item
 * 
 * Styled for mobile-first GitKraken feel.
 */
@Composable
fun CommitListItem(
    node: CommitGraphEngine.GraphNode,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth().height(120.dp)
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            // Space for graph sidebar
            Spacer(modifier = Modifier.width(100.dp))
            
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 12.dp, horizontal = 16.dp)
                    .background(Color(0xFF161B22), RoundedCornerShape(8.dp))
                    .padding(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = node.commit.message,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                    Spacer(Modifier.weight(1f))
                    // Branch Tags
                    node.branches.forEach { branch ->
                        Surface(
                            color = Color(0xFF238636).copy(alpha = 0.2f),
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier.padding(start = 4.dp)
                        ) {
                            Text(
                                branch,
                                color = Color(0xFF238636),
                                fontSize = 10.sp,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                
                Spacer(Modifier.height(4.dp))
                
                Text(
                    text = "${node.commit.author} · ${node.commit.sha}",
                    color = Color.Gray,
                    fontSize = 11.sp,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                )
                
                if (node.commit.isConflict) {
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).background(Color(0xFFF85149), RoundedCornerShape(2.dp)))
                        Spacer(Modifier.width(8.dp))
                        Text("CONFLICT DETECTED", color = Color(0xFFF85149), fontSize = 10.sp, fontWeight = FontWeight.Black)
                    }
                }
            }
        }
    }
}
