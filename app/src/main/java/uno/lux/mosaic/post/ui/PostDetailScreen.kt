package uno.lux.mosaic.post.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import uno.lux.mosaic.R
import uno.lux.mosaic.app.fixtures.SampleComments
import uno.lux.mosaic.app.fixtures.SamplePosts
import uno.lux.mosaic.app.fixtures.SampleUsers
import uno.lux.mosaic.comment.data.domain.Comment
import uno.lux.mosaic.common.asText
import uno.lux.mosaic.common.ui.FailedActionEffect
import uno.lux.mosaic.common.ui.FullScreenError
import uno.lux.mosaic.common.ui.FullScreenProgress
import uno.lux.mosaic.common.ui.LoadMoreEffect
import uno.lux.mosaic.common.ui.LoadMoreFooter
import uno.lux.mosaic.common.util.AppError
import uno.lux.mosaic.common.util.LightStatusBarIcons
import uno.lux.mosaic.common.util.relativeTime
import uno.lux.mosaic.designsystem.components.AppBarAction
import uno.lux.mosaic.designsystem.components.debouncedClickable
import uno.lux.mosaic.designsystem.components.rememberDebounced
import uno.lux.mosaic.designsystem.theme.LocalMosaicColors
import uno.lux.mosaic.designsystem.theme.MosaicTheme
import uno.lux.mosaic.designsystem.theme.accentBarColors
import uno.lux.mosaic.post.data.domain.Post
import uno.lux.mosaic.post.data.domain.PostId
import uno.lux.mosaic.post.ui.PostDetailUiState.Content
import uno.lux.mosaic.user.data.domain.User
import uno.lux.mosaic.user.ui.Avatar
import uno.lux.mosaic.video.data.domain.Video
import uno.lux.mosaic.common.R as CommonR
import uno.lux.mosaic.post.ui.PostDetailUiEvent as UiEvent
import uno.lux.mosaic.post.ui.PostDetailUiState as UiState

@Composable
fun PostDetailScreen(
    postId: PostId,
    modifier: Modifier = Modifier,
    viewModel: PostDetailViewModel =
        hiltViewModel<PostDetailViewModel, PostDetailViewModel.Factory>(
            creationCallback = { factory -> factory.create(postId) },
        ),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    PostDetailScreen(
        uiState = uiState,
        onEvent = viewModel::onEvent,
        modifier = modifier,
    )
}

/**
 * Stateless post detail screen — the post, its comment thread, and a sticky composer at the
 * bottom.
 *
 * Every control on this page reports a [UiEvent] through [onEvent]. The leaf
 * components keep their own callbacks and are adapted at the call site, so they stay callable
 * from a screen that sends different events, or none at all.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PostDetailScreen(
    uiState: UiState,
    onEvent: (UiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val loaded = uiState.content as? Content.Loaded
    val deletePost: () -> Unit = { onEvent(UiEvent.Delete) }
    val listState = rememberLazyListState()
    val elevated = listState.canScrollBackward
    val snackbarHostState = remember { SnackbarHostState() }

    LightStatusBarIcons()

    // A delete or comment whose request failed after its dialog already closed.
    FailedActionEffect(uiState.failedAction, snackbarHostState) {
        onEvent(UiEvent.FailedActionShown)
    }

    Scaffold(
        modifier = modifier.imePadding(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            val elevationPx = with(LocalDensity.current) { TopBarElevation.toPx() }
            val shadowElevation = remember { Animatable(0f) }

            LaunchedEffect(elevated, elevationPx) {
                shadowElevation.animateTo(if (elevated) elevationPx else 0f)
            }

            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.post_detail_title),
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                navigationIcon = {
                    AppBarAction(
                        icon = CommonR.drawable.ic_arrow_back,
                        onClick = { onEvent(UiEvent.GoBack) },
                        contentDescription = stringResource(CommonR.string.navigate_back),
                    )
                },
                actions = {
                    if (loaded != null) {
                        PostOverflowMenu(
                            post = loaded.post,
                            author = loaded.author,
                            reportSend = uiState.reportSend,
                            onToggleBookmark = { onEvent(UiEvent.ToggleBookmark) },
                            onReport = { reason, details ->
                                onEvent(UiEvent.Report(reason, details))
                            },
                            onReportClosed = { onEvent(UiEvent.CloseReport) },
                            onDelete = deletePost.takeIf { loaded.isOwn },
                        )
                    }
                },
                colors = accentBarColors(),
                modifier = Modifier.graphicsLayer { this.shadowElevation = shadowElevation.value },
            )
        },
        bottomBar = {
            if (loaded != null) {
                CommentComposer(
                    user = uiState.composerUser,
                    sendState = uiState.commentSend,
                    onSend = { text -> onEvent(UiEvent.AddComment(text)) },
                    onSent = { onEvent(UiEvent.CommentSent) },
                )
            }
        },
    ) { contentPadding ->
        when (val content = uiState.content) {
            Content.Loading -> FullScreenProgress(
                modifier = Modifier.padding(contentPadding),
            )

            Content.NotFound -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding)
                    .padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.post_detail_not_found),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            is Content.Error -> FullScreenError(
                message = content.error.asText(),
                onRetry = { onEvent(UiEvent.Retry) },
                modifier = Modifier.padding(contentPadding),
            )

            // The thread is passed separately rather than as the whole state, so a report or a
            // failed delete moving does not recompose the list under the reader.
            is Content.Loaded -> PostDetailContent(
                content = content,
                thread = uiState.commentThread,
                listState = listState,
                onEvent = onEvent,
                modifier = Modifier.padding(contentPadding),
            )
        }
    }
}

/** Resting shadow depth of the pinned detail bar once the content is scrolled. */
private val TopBarElevation = 4.dp

/**
 * Where the thread section starts: the row right after the post card, which is the list's single
 * first item. Compose has no key-based scroll, and a row's index cannot be asked for unless the
 * row is visible, so this is the anchor every scroll on the page offsets from.
 */
private const val COMMENTS_HEADER_INDEX = 1

@Composable
private fun PostDetailContent(
    content: Content.Loaded,
    thread: CommentThread,
    listState: LazyListState,
    onEvent: (UiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val post = content.post
    val author = content.author

    // The post is one item at the top, so the end of this list is the end of the thread. While the
    // first page is still coming — or failed — the thread counts as ended: there is no cursor to
    // ask a second page for yet.
    LoadMoreEffect(
        listState = listState,
        endReached = thread.endReached || thread.isLoading || thread.error != null,
        loadMoreFailed = thread.loadMoreFailed,
        onLoadMore = { onEvent(UiEvent.LoadMoreComments) },
    )

    // Bring the comment the user just sent into view — the row after the header, offset by where
    // the comment sits in the thread, since the thread hangs below the whole post. The signal is
    // spent either way: a comment the list no longer shows is one a reload has replaced.
    LaunchedEffect(thread.scrollTo) {
        val scrollTo = thread.scrollTo ?: return@LaunchedEffect

        val position = thread.comments.indexOfFirst { it.id == scrollTo }
        if (position >= 0 && thread.error == null && !thread.isLoading) {
            listState.animateScrollToItem(COMMENTS_HEADER_INDEX + 1 + position)
        }
        onEvent(UiEvent.ScrolledToComment)
    }

    LazyColumn(state = listState, modifier = modifier.fillMaxSize()) {
        item {
            DetailPostCard(
                post = post,
                author = author,
                onOpenProfile = { onEvent(UiEvent.OpenProfile(author.id)) },
                onOpenVideo = { video -> onEvent(UiEvent.OpenVideo(video)) },
                onOpenAlbum = { urls, index ->
                    onEvent(UiEvent.OpenAlbum(urls, index))
                },
                onToggleLike = { onEvent(UiEvent.ToggleLike) },
                onToggleBookmark = { onEvent(UiEvent.ToggleBookmark) },
                onScrollToComments = {
                    scope.launch { listState.animateScrollToItem(COMMENTS_HEADER_INDEX) }
                },
            )
        }

        if (thread.error != null) {
            item(key = "comments_error") {
                CommentsError(
                    error = thread.error,
                    onRetry = { onEvent(UiEvent.RetryComments) },
                )
            }
        } else if (thread.isLoading) {
            item(key = "comments_loading") { CommentsLoading() }
        } else {
            // The whole thread's count, not the loaded window's — the list below is a page of it.
            item(key = "comments_header") { CommentsHeader(count = post.commentCount) }
            if (thread.comments.isNotEmpty()) {
                items(thread.comments, key = { it.id }) { comment ->
                    CommentRow(
                        comment = comment,
                        onLike = {
                            onEvent(UiEvent.ToggleCommentLike(comment.id))
                        },
                        onOpenProfile = {
                            onEvent(UiEvent.OpenProfile(comment.author.id))
                        },
                    )
                }
                if (!thread.endReached) {
                    item(key = "comments_loading_more") {
                        LoadMoreFooter(
                            failed = thread.loadMoreFailed,
                            onRetry = { onEvent(UiEvent.LoadMoreComments) },
                        )
                    }
                }
            } else {
                item(key = "empty_comments") {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(vertical = 32.dp, horizontal = 24.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.post_detail_no_comments),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
private fun CommentsLoading() {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp, horizontal = 24.dp),
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun CommentsError(error: AppError, onRetry: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp, horizontal = 24.dp),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = error.asText(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Button(onClick = onRetry) {
                Text(stringResource(CommonR.string.error_retry))
            }
        }
    }
}

/**
 * The post itself, from the same blocks as the feed's [PostCard], minus the affordances that only
 * make sense in a list: the body isn't a tap target, the overflow menu lives in the top bar, and
 * the comment action scrolls to the thread instead of navigating.
 */
@Composable
private fun DetailPostCard(
    post: Post,
    author: User,
    onOpenProfile: () -> Unit,
    onOpenVideo: (Video) -> Unit,
    onOpenAlbum: (List<String>, initialIndex: Int) -> Unit,
    onToggleLike: () -> Unit,
    onToggleBookmark: () -> Unit,
    onScrollToComments: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(4.dp)
            .background(
                MaterialTheme.colorScheme.surface,
                RoundedCornerShape(16.dp),
            ),
    ) {
        PostAuthorHeader(post = post, author = author, onOpenProfile = onOpenProfile)
        PostBody(title = post.title, body = post.body)
        PostMedia(post = post, onOpenVideo = onOpenVideo, onOpenAlbum = onOpenAlbum)
        PostActions(
            post = post,
            onToggleLike = onToggleLike,
            onToggleBookmark = onToggleBookmark,
            onCommentClick = onScrollToComments,
        )
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun CommentsHeader(
    count: Int,
    modifier: Modifier = Modifier,
) {
    Text(
        text = pluralStringResource(R.plurals.post_detail_comments_header, count, count),
        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.04.em),
        color = LocalMosaicColors.current.textTertiary,
        modifier = modifier.padding(start = 16.dp, top = 14.dp, end = 16.dp, bottom = 2.dp),
    )
}

@Composable
private fun CommentRow(
    comment: Comment,
    onLike: () -> Unit,
    onOpenProfile: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val likeTint by animateColorAsState(
        targetValue = if (comment.isLiked) LocalMosaicColors.current.like else muted,
        label = "commentLikeTint",
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, top = 12.dp, end = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        Box(
            modifier = Modifier
                .clip(CircleShape)
                .debouncedClickable(onClick = onOpenProfile),
        ) {
            Avatar(user = comment.author, size = 36.dp)
        }

        Column(Modifier.weight(1f)) {
            // Inline "name  body" — author bold, text regular — as one reflowing block.
            Text(
                text = buildAnnotatedString {
                    withStyle(
                        SpanStyle(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        ),
                    ) {
                        append(comment.author.nickname)
                    }
                    append("  ")
                    withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant)) {
                        append(comment.text)
                    }
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(6.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = relativeTime(comment.createdAt).asText(),
                    style = MaterialTheme.typography.labelMedium,
                    color = LocalMosaicColors.current.textTertiary,
                )
                Text(
                    text = pluralStringResource(
                        R.plurals.comment_like_count,
                        comment.likeCount,
                        comment.likeCount,
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = LocalMosaicColors.current.textTertiary,
                )
            }
        }

        IconButton(
            onClick = onLike.rememberDebounced(),
            modifier = Modifier.size(36.dp),
        ) {
            Icon(
                painter = painterResource(
                    if (comment.isLiked) R.drawable.ic_favorite_filled else R.drawable.ic_favorite_border,
                ),
                contentDescription = stringResource(
                    if (comment.isLiked) R.string.comment_action_unlike else R.string.comment_action_like,
                ),
                tint = likeTint,
                modifier = Modifier.size(18.dp),
            )
        }
    }
    Spacer(Modifier.height(12.dp))
}

/**
 * The sticky composer at the bottom of the thread. It owns the text and gives it up only once
 * [sendState] reaches [CommentSendState.SENT], so a send that failed leaves what the user typed
 * where it is. While [CommentSendState.SENDING] the field and the button are disabled, so one
 * tap is one comment.
 */
@Composable
private fun CommentComposer(
    user: User,
    sendState: CommentSendState,
    onSend: (String) -> Unit,
    onSent: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val textState = rememberTextFieldState()
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val keyboardController = LocalSoftwareKeyboardController.current
    val isSending = sendState == CommentSendState.SENDING
    val send = {
        val trimmed = textState.text.toString().trim()
        if (trimmed.isNotEmpty() && !isSending) onSend(trimmed)
    }

    // The server has the comment, so the text has done its job. Spending the signal here keeps the
    // two in step: the box empties and the send ends in the same pass.
    LaunchedEffect(sendState) {
        if (sendState != CommentSendState.SENT) return@LaunchedEffect

        textState.clearText()
        keyboardController?.hide()
        onSent()
    }

    Column(modifier = modifier.fillMaxWidth()) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 14.dp, vertical = 10.dp)
                .navigationBarsPadding(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Avatar(user = user, size = 36.dp)
            OutlinedTextField(
                state = textState,
                enabled = !isSending,
                placeholder = {
                    Text(
                        text = stringResource(R.string.post_detail_comment_hint),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                onKeyboardAction = { send() },
                shape = RoundedCornerShape(12.dp),
                lineLimits = TextFieldLineLimits.SingleLine,
                textStyle = MaterialTheme.typography.bodyMedium,
                // The disabled container matches the enabled one, so a send in flight dims the text
                // without the field appearing to change shape.
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = MaterialTheme.colorScheme.background,
                    focusedContainerColor = MaterialTheme.colorScheme.background,
                    disabledContainerColor = MaterialTheme.colorScheme.background,
                ),
                contentPadding = OutlinedTextFieldDefaults.contentPadding(
                    top = 8.dp,
                    bottom = 8.dp,
                ),
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 42.dp),
            )
            val hasText by remember { derivedStateOf { textState.text.isNotBlank() } }
            val canSend = hasText && !isSending
            val sendTint by animateColorAsState(
                targetValue = if (canSend) MaterialTheme.colorScheme.primary else muted,
                label = "sendTint",
            )

            IconButton(onClick = send, enabled = canSend) {
                // The spinner stands in the button's place, so the disabled field reads as work in
                // progress rather than as a composer that has stopped responding.
                if (isSending) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Icon(
                        painter = painterResource(R.drawable.ic_send),
                        contentDescription = stringResource(R.string.post_detail_send_comment),
                        tint = sendTint,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PostDetailLoadedPreview() {
    PostDetailPreview(
        content = Content.Loaded(
            post = SamplePosts.first(),
            author = SampleUsers.first(),
            isOwn = false,
        ),
        thread = CommentThread(
            comments = SampleComments["p1"] ?: emptyList(),
            isLoading = false,
            endReached = true,
        ),
    )
}

@Preview(showBackground = true)
@Composable
private fun PostDetailErrorPreview() {
    PostDetailPreview(Content.Error(AppError.NoConnection))
}

@Preview(showBackground = true)
@Composable
private fun PostDetailLoadingPreview() {
    PostDetailPreview(Content.Loading)
}

/** The screen with its events ignored, so each preview supplies only the state it shows. */
@Composable
private fun PostDetailPreview(
    content: UiState.Content,
    thread: CommentThread = CommentThread(),
) {
    MosaicTheme {
        PostDetailScreen(
            uiState = UiState(
                content = content,
                composerUser = SampleUsers.first(),
                commentThread = thread,
            ),
            onEvent = {},
        )
    }
}
