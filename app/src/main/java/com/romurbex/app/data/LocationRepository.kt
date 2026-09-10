package com.romurbex.app.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class LocationRepository(private val context: Context) {
    private val db = RomurbexDatabase.get(context)
    private val locationDao = db.locationDao()
    private val photoDao = db.photoDao()
    private val listVisibilityDao = db.listVisibilityDao()

    fun observeLocations(): Flow<List<LocationEntity>> = locationDao.observeAll()

    fun observeListSummaries(): Flow<List<ListSummary>> = locationDao.observeListSummaries()

    fun observeHiddenLists(): Flow<Set<String>> = listVisibilityDao.observeAll()
        .map { entries -> entries.filter { !it.isVisible }.map { it.name }.toSet() }

    suspend fun setListVisible(name: String, visible: Boolean) {
        if (visible) listVisibilityDao.clearVisibility(name) else listVisibilityDao.setVisibility(ListVisibilityEntity(name, false))
    }

    /** Supprime tous les lieux importés sous ce nom de liste (et leurs photos attachées, en cascade). */
    suspend fun deleteList(name: String) {
        locationDao.deleteByListName(name)
        listVisibilityDao.clearVisibility(name)
    }

    fun observeLocation(id: Long): Flow<LocationEntity?> = locationDao.observeById(id)

    fun observePhotos(locationId: Long): Flow<List<PhotoEntity>> = photoDao.observeForLocation(locationId)

    fun observeFirstPhotoUri(locationId: Long): Flow<String?> = photoDao.observeFirstPhotoUri(locationId)

    suspend fun getLocation(id: Long): LocationEntity? = locationDao.getById(id)

    suspend fun saveLocation(location: LocationEntity): Long =
        if (location.id == 0L) locationDao.insert(location) else {
            locationDao.update(location)
            location.id
        }

    suspend fun importLocations(locations: List<LocationEntity>): List<Long> = locationDao.insertAll(locations)

    suspend fun deleteLocation(location: LocationEntity) = locationDao.delete(location)

    /** Attache des photos déjà sélectionnées via le sélecteur système (SAF) à un lieu, en conservant l'accès. */
    suspend fun attachPhotos(locationId: Long, uris: List<Uri>) {
        uris.forEach { uri ->
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
        }
        photoDao.insertAll(uris.map { PhotoEntity(locationId = locationId, uri = it.toString()) })
    }

    suspend fun removePhoto(photo: PhotoEntity) = photoDao.delete(photo)
}
