package com.lagradost.cloudstream3.ui.tv

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.activityViewModels
import androidx.compose.runtime.livedata.observeAsState
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.lagradost.cloudstream3.DubStatus
import com.lagradost.cloudstream3.ui.home.HomeViewModel
import com.lagradost.cloudstream3.ui.library.LibraryViewModel
import com.lagradost.cloudstream3.ui.result.EpisodeClickEvent
import com.lagradost.cloudstream3.ui.result.ResultViewModel2
import com.lagradost.cloudstream3.ui.result.ACTION_CLICK_DEFAULT
import com.lagradost.cloudstream3.ui.search.SearchViewModel
import com.lagradost.cloudstream3.ui.tv.detail.TvDetailPresenter
import com.lagradost.cloudstream3.ui.tv.detail.TvDetailScreen
import com.lagradost.cloudstream3.ui.tv.home.TvHomeEvent
import com.lagradost.cloudstream3.ui.tv.home.TvHomePresenter
import com.lagradost.cloudstream3.ui.tv.home.TvHomeScreen
import com.lagradost.cloudstream3.ui.tv.library.TvLibraryPresenter
import com.lagradost.cloudstream3.ui.tv.library.TvLibraryScreen
import com.lagradost.cloudstream3.ui.tv.search.TvSearchPresenter
import com.lagradost.cloudstream3.ui.tv.search.TvSearchScreen
import com.lagradost.cloudstream3.ui.tv.theme.LocalTvColors
import com.lagradost.cloudstream3.ui.tv.theme.TvTheme
import com.lagradost.cloudstream3.ui.tv.theme.isReducedMotion
import com.lagradost.cloudstream3.ui.WatchType
import com.lagradost.cloudstream3.utils.AppContextUtils.filterProviderByPreferredMedia

private const val ROUTE_HOME = "home"
private const val ROUTE_SEARCH = "search"
private const val ROUTE_LIBRARY = "library"
private const val ROUTE_DETAIL = "detail/{url}/{apiName}/{title}/{poster}"

class TvShellFragment : Fragment() {
    private val homeViewModel by activityViewModels<HomeViewModel>()
    private val searchViewModel by activityViewModels<SearchViewModel>()
    private val libraryViewModel by activityViewModels<LibraryViewModel>()
    private val resultViewModel by activityViewModels<ResultViewModel2>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = ComposeView(requireContext()).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            TvTheme(reducedMotion = isReducedMotion(requireContext())) {
                TvShell(
                    homeViewModel = homeViewModel,
                    searchViewModel = searchViewModel,
                    libraryViewModel = libraryViewModel,
                    resultViewModel = resultViewModel,
                    fragmentActivity = requireActivity(),
                    onOpenLegacy = { destination ->
                        (activity as? TvShellHost)?.openTvLegacyDestination(destination)
                    },
                )
            }
        }
    }
}

interface TvShellHost {
    fun openTvLegacyDestination(destination: String)
    fun openTvResult(url: String, apiName: String)
}

@Composable
private fun TvShell(
    homeViewModel: HomeViewModel,
    searchViewModel: SearchViewModel,
    libraryViewModel: LibraryViewModel,
    resultViewModel: ResultViewModel2,
    fragmentActivity: FragmentActivity,
    onOpenLegacy: (String) -> Unit,
) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val currentEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentEntry?.destination?.route ?: ROUTE_HOME

    LaunchedEffect(Unit) {
        homeViewModel.reloadStored()
        homeViewModel.loadAndCancel(
            com.lagradost.cloudstream3.utils.DataStoreHelper.currentHomePage,
            forceReload = false,
        )
        searchViewModel.updateHistory()
        libraryViewModel.reloadPages(forceReload = false)
    }

    fun openDetail(item: TvMediaItem) {
        navController.navigate(
            "detail/${Uri.encode(item.url)}/${Uri.encode(item.apiName)}/${Uri.encode(item.title)}/${Uri.encode(item.posterUrl.orEmpty())}",
        )
    }

    fun navigate(route: String) {
        navController.navigate(route) {
            popUpTo(ROUTE_HOME) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    BackHandler(enabled = currentRoute != ROUTE_HOME) {
        navController.popBackStack()
    }

    NavHost(
        navController = navController,
        startDestination = ROUTE_HOME,
        modifier = androidx.compose.ui.Modifier
            .fillMaxSize()
            .background(LocalTvColors.current.background),
    ) {
        composable(ROUTE_HOME) {
            val page by homeViewModel.page.observeAsState()
            val apiName by homeViewModel.apiName.observeAsState()
            val resume by homeViewModel.resumeWatching.observeAsState()
            val bookmarks by homeViewModel.bookmarks.observeAsState()
            val preview by homeViewModel.preview.observeAsState()
            val providerNames = context.filterProviderByPreferredMedia().map { it.name }
            val state = TvHomePresenter.present(
                page = page,
                apiName = apiName,
                resume = resume,
                bookmarks = bookmarks,
                preview = preview,
                context = context,
                providerNames = providerNames,
            )
            TvHomeScreen(state = state, onEvent = { event ->
                when (event) {
                    TvHomeEvent.OpenSearch -> navigate(ROUTE_SEARCH)
                    TvHomeEvent.OpenLibrary -> navigate(ROUTE_LIBRARY)
                    TvHomeEvent.OpenDownloads -> onOpenLegacy("downloads")
                    TvHomeEvent.OpenSettings -> onOpenLegacy("settings")
                    TvHomeEvent.Refresh -> homeViewModel.loadAndCancel(apiName, forceReload = true)
                    TvHomeEvent.Random -> homeViewModel.loadAndCancel("random", forceReload = true)
                    is TvHomeEvent.OpenDetail -> openDetail(event.item)
                    is TvHomeEvent.ChangeProvider -> homeViewModel.loadAndCancel(
                        event.providerName,
                        forceReload = true,
                        fromUI = true,
                    )
                }
            })
        }
        composable(ROUTE_SEARCH) {
            val response by searchViewModel.searchResponse.observeAsState()
            val currentSearch by searchViewModel.currentSearch.observeAsState()
            val suggestions by searchViewModel.searchSuggestions.observeAsState()
            val history by searchViewModel.currentHistory.observeAsState()
            val state = TvSearchPresenter.present(response, currentSearch, suggestions, history)
            TvSearchScreen(
                state = state,
                onSearch = { query ->
                    searchViewModel.searchAndCancel(query)
                    searchViewModel.updateHistory()
                },
                onQueryChanged = searchViewModel::fetchSuggestions,
                onSuggestionSelected = { query -> searchViewModel.searchAndCancel(query) },
                onClearSuggestions = searchViewModel::clearSuggestions,
                onItemClick = ::openDetail,
                onNavigate = ::navigate,
                onLegacyNavigate = onOpenLegacy,
                onBack = { navController.popBackStack() },
            )
        }
        composable(ROUTE_LIBRARY) {
            val pages by libraryViewModel.pages.observeAsState()
            val selectedPage by libraryViewModel.currentPage.observeAsState(0)
            val apiName by libraryViewModel.currentApiName.observeAsState()
            val state = TvLibraryPresenter.present(pages, selectedPage, apiName, context)
            TvLibraryScreen(
                state = state,
                onPageSelected = libraryViewModel::switchPage,
                onItemClick = ::openDetail,
                onNavigate = ::navigate,
                onLegacyNavigate = onOpenLegacy,
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            route = ROUTE_DETAIL,
            arguments = listOf(
                navArgument("url") { type = NavType.StringType },
                navArgument("apiName") { type = NavType.StringType },
                navArgument("title") { type = NavType.StringType },
                navArgument("poster") { type = NavType.StringType; defaultValue = "" },
            ),
        ) { entry ->
            val url = Uri.decode(entry.arguments?.getString("url").orEmpty())
            val apiName = Uri.decode(entry.arguments?.getString("apiName").orEmpty())
            val page by resultViewModel.page.observeAsState()
            val episodes by resultViewModel.episodes.observeAsState()
            val movie by resultViewModel.movie.observeAsState()
            val recommendations by resultViewModel.recommendations.observeAsState()
            val trailers by resultViewModel.trailers.observeAsState()
            val seasonSelections by resultViewModel.seasonSelections.observeAsState()
            val dubSelections by resultViewModel.dubSubSelections.observeAsState()
            val state = TvDetailPresenter.present(
                page = page,
                episodes = episodes,
                movie = movie,
                recommendations = recommendations,
                trailers = trailers,
                seasonSelections = seasonSelections,
                dubSelections = dubSelections,
                context = context,
            )

            LaunchedEffect(url, apiName) {
                if (url.isNotBlank() && apiName.isNotBlank()) {
                    resultViewModel.load(
                        activity = fragmentActivity,
                        url = url,
                        apiName = apiName,
                        showFillers = false,
                        dubStatus = DubStatus.None,
                        autostart = null,
                    )
                }
            }

            TvDetailScreen(
                state = state,
                onPlay = { episode ->
                    resultViewModel.handleAction(EpisodeClickEvent(ACTION_CLICK_DEFAULT, episode))
                },
                onEpisodeClick = { episode ->
                    resultViewModel.handleAction(EpisodeClickEvent(ACTION_CLICK_DEFAULT, episode))
                },
                onSeasonSelected = resultViewModel::changeSeason,
                onDubSelected = resultViewModel::changeDubStatus,
                onFavorite = { resultViewModel.toggleFavoriteStatus(fragmentActivity) },
                onWatched = {
                    resultViewModel.updateWatchStatus(WatchType.WATCHING, fragmentActivity)
                },
                onTrailer = {
                    (fragmentActivity as? TvShellHost)?.openTvResult(state.url, apiName)
                },
                onRecommendationClick = ::openDetail,
                onNavigate = ::navigate,
                onLegacyNavigate = onOpenLegacy,
                onBack = { navController.popBackStack() },
            )
        }
    }
}
