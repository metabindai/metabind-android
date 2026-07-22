/*
 * CmsModels.kt.
 *
 * © 2026 Yap Studios LLC
 */
package ai.metabind.metabind

import ai.metabind.metabind.fragment.AssetFields
import ai.metabind.metabind.fragment.ContentFields

/**
 * A cursor-paginated page of results returned by the CMS browse queries.
 *
 * Mirrors the GraphQL `CursorPagination` shape: [cursor] is the cursor to pass
 * to the next request, [hasMore] indicates whether further pages exist, and
 * [limit] is the page size that was applied.
 */
data class Page<T>(
    val data: List<T>,
    val cursor: String?,
    val hasMore: Boolean,
    val limit: Int,
)

/**
 * Result of [ComponentRepository.executeSavedSearch]. A saved search resolves to
 * either a list of contents or a list of assets depending on its type.
 */
sealed interface SavedSearchResults {
    data class Contents(val page: Page<ContentFields>) : SavedSearchResults
    data class Assets(val page: Page<AssetFields>) : SavedSearchResults
}
