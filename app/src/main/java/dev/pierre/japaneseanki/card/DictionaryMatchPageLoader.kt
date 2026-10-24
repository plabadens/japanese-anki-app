package dev.pierre.japaneseanki.card

import dev.pierre.japaneseanki.DictionaryEntry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal data class DictionaryMatchPageRequest(
    val query: String?,
    val generation: Long,
    val expression: String?,
    val offset: Int,
    val scope: CoroutineScope,
    val isCurrent: (Long) -> Boolean,
    val search: (String, String?, Int) -> List<DictionaryEntry>,
    val setLoading: (Boolean) -> Unit,
    val append: (List<DictionaryEntry>) -> Unit,
    val setHasMore: (Boolean) -> Unit,
    val setError: (String) -> Unit,
    val errorMessage: String,
)

internal fun loadMoreDictionaryMatches(request: DictionaryMatchPageRequest) {
    val query = request.query ?: return
    request.scope.launch {
        if (!request.isCurrent(request.generation)) return@launch
        request.setLoading(true)
        try {
            val more = withContext(Dispatchers.IO) {
                request.search(query, request.expression, request.offset)
            }
            if (!request.isCurrent(request.generation)) return@launch
            request.append(more)
            request.setHasMore(more.size == 50)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            if (request.isCurrent(request.generation)) request.setError(request.errorMessage)
        } finally {
            request.setLoading(false)
        }
    }
}
