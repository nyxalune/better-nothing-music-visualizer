package com.better.nothing.music.vizualizer.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
internal fun CommunityOverlays(
    viewModel: MainViewModel
) {
    val isShowingCommunity by viewModel.isShowingCommunity.collectAsStateWithLifecycle()
    val isShowingAnnouncementHistory by viewModel.showAnnouncementHistory.collectAsStateWithLifecycle()
    val isShowingLeaderboard by viewModel.isShowingLeaderboard.collectAsStateWithLifecycle()
    val latestAnnouncement by viewModel.latestAnnouncement.collectAsStateWithLifecycle()
    val showAnnouncementModal by viewModel.showAnnouncementModal.collectAsStateWithLifecycle()
    val showAnnouncementEditor by viewModel.showAnnouncementEditor.collectAsStateWithLifecycle()

    if (isShowingCommunity) {
        val userId by viewModel.userId.collectAsStateWithLifecycle()
        CommunityPresetsScreen(
            presets = emptyList(), // Load from repo or state
            currentUserId = userId,
            error = null,
            onDownload = { /* handle download */ },
            onDelete = { /* handle delete */ },
            onDismiss = { viewModel.hideCommunity() }
        )
    }

    if (isShowingAnnouncementHistory) {
        val announcements by viewModel.announcementHistory.collectAsStateWithLifecycle()
        AnnouncementHistoryScreen(
            announcements = announcements,
            onDismiss = { viewModel.hideAnnouncementHistory() }
        )
    }

    if (isShowingLeaderboard) {
        LeaderboardScreen(
            entries = emptyList(), // Load from state
            onDismiss = { viewModel.hideLeaderboard() }
        )
    }

    if (showAnnouncementModal && latestAnnouncement != null) {
        AnnouncementModal(
            announcement = latestAnnouncement!!,
            onDismiss = { viewModel.dismissAnnouncement() }
        )
    }

    if (showAnnouncementEditor) {
        AnnouncementEditorScreen(
            onPost = { t, m, s, l, lt -> viewModel.postAnnouncement(t, m, s, l, lt) },
            onDismiss = { viewModel.hideAnnouncementEditor() }
        )
    }
}
