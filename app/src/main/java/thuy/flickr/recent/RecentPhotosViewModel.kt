package thuy.flickr.recent

import android.content.res.Resources
import android.databinding.ObservableArrayList
import android.databinding.ObservableBoolean
import android.databinding.ObservableField
import android.databinding.ObservableList
import android.os.Bundle
import com.jakewharton.rxrelay2.BehaviorRelay
import com.jakewharton.rxrelay2.PublishRelay
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import thuy.flickr.*
import thuy.flickr.core.BaseViewModel
import javax.inject.Inject

internal const val KEY_QUERY = "query"

typealias PhotoViewModels = List<PhotoViewModel>

class RecentPhotosViewModel @Inject internal constructor(
    private val getPhotos: GetPhotos,
    private val photoViewModelMapper: PhotoViewModelMapper,
    private val resources: Resources,
    private val queryRepository: QueryRepository
) : BaseViewModel() {
  val title = ObservableField<String>()
  val photoCountText = ObservableField<String>()
  val isPhotoCountVisible = ObservableBoolean()
  val photos: ObservableList<PhotoViewModel> = ObservableArrayList<PhotoViewModel>()
  val isLoading = ObservableBoolean()

  private val onErrorLoadingPhotosRelay = PublishRelay.create<Unit>()
  val onErrorLoadingPhotos: Observable<Unit>
    get() = onErrorLoadingPhotosRelay.autoClear()

  private val photosRelay = BehaviorRelay.create<PhotoViewModels>()
  val onPhotoTapped: Observable<PhotoId>
    get() = photosRelay
        .switchMap {
          Observable
              .merge(it.map { it.tapAction.observe })
              .map { it.id }
        }
        .autoClear()

  init {
    queryRepository.queries
        .map {
          when (it) {
            is Search -> "\"${it.queryText}\""
            is Recent -> resources.getString(R.string.recent)
          }
        }
        .subscribe { title.set(it) }

    getPhotos(queries = queryRepository.queries)
        .subscribeOn(Schedulers.io())
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe { result ->
          when (result) {
            is Busy -> isLoading.set(true)
            is Success<Photos> -> {
              updatePhotos(result)
              isLoading.set(false)
              updatePhotoCount(result)
            }
            is Failure -> {
              onErrorLoadingPhotosRelay.accept(Unit)
              isLoading.set(false)
            }
          }
        }
  }

  fun loadPhotos(savedInstanceState: Bundle?) {
    val query = savedInstanceState?.getString(KEY_QUERY)
    queryRepository.putQueryText(query)
  }

  fun retry() {
    queryRepository.putQueryText(queryRepository.latestQuery)
  }

  fun onSaveInstanceState(outState: Bundle?) {
    outState?.putString(KEY_QUERY, queryRepository.latestQuery)
  }

  private fun updatePhotos(result: Success<Photos>) {
    val newPhotos = result.value.map {
      photoViewModelMapper(it)
    }
    photosRelay.accept(newPhotos)
    photos.clear()
    photos.addAll(newPhotos)
  }

  private fun updatePhotoCount(result: Success<Photos>) = when {
    result.value.isNotEmpty() -> {
      photoCountText.set(resources.getQuantityString(
          R.plurals.xPhotos,
          result.value.size,
          result.value.size
      ))
      isPhotoCountVisible.set(true)
    }
    else -> isPhotoCountVisible.set(false)
  }

  fun onQueryTextSubmit(query: String?): Boolean {
    queryRepository.putQueryText(query)
    return false
  }
}
