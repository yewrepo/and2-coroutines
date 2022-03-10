import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.internal.wait
import okhttp3.logging.HttpLoggingInterceptor
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

const val BASE_URL = "http://127.0.0.1:9999/api"

fun main(args: Array<String>) {
    val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BODY))
        .build()

    val gson = Gson()

    CoroutineScope(Dispatchers.IO).launch {
        try {
            val posts = getPosts(gson, client)
                .map { post ->
                    val author = getAuthor(gson, client, post.authorId)
                    return@map PostWithAuthor(author, post)
                }
                .map { postWithAuthor ->
                    val comments = getComments(gson, client, postWithAuthor.post.id)
                    return@map PostWithComments(postWithAuthor, comments)
                }
            println(posts)
        } catch (e: Exception) {
            println(e)
        }
    }
    Thread.sleep(60)
}

suspend fun OkHttpClient.apiCall(url: String): Response {
    return suspendCoroutine { continuation ->
        Request.Builder()
            .url(url)
            .build()
            .let(::newCall)
            .enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    continuation.resumeWithException(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    continuation.resume(response)
                }
            })
    }
}

suspend fun <T> makeRequest(url: String, client: OkHttpClient, typeToken: TypeToken<T>, gson: Gson): T =
    withContext(Dispatchers.IO) {
        client.apiCall(url)
            .let { response ->
                if (!response.isSuccessful) {
                    response.close()
                    throw  RuntimeException(response.message)
                }
                val body = response.body ?: throw RuntimeException("Response body is null!")
                return@let gson.fromJson(body.string(), typeToken.type)
            }
    }

suspend fun getPosts(gson: Gson, client: OkHttpClient): List<Post> {
    return makeRequest("$BASE_URL/posts", client, object : TypeToken<List<Post>>() {}, gson)
}

suspend fun getComments(gson: Gson, client: OkHttpClient, id: Long): List<Comment> {
    return makeRequest("$BASE_URL/posts/$id/comments", client, object : TypeToken<List<Comment>>() {}, gson)
}

suspend fun getAuthor(gson: Gson, client: OkHttpClient, id: Long): Author {
    return makeRequest("$BASE_URL/authors/$id", client, object : TypeToken<Author>() {}, gson)
}