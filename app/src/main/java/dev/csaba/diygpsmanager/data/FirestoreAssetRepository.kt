package dev.csaba.diygpsmanager.data

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import dev.csaba.diygpsmanager.data.remote.getLockUpdate
import dev.csaba.diygpsmanager.data.remote.mapToAsset
import dev.csaba.diygpsmanager.data.remote.mapToAssetData
import dev.csaba.diygpsmanager.data.remote.mapToLockRadiusUpdate
import dev.csaba.diygpsmanager.data.remote.mapToPeriodIntervalUpdate
import dev.csaba.diygpsmanager.data.remote.RemoteAsset
import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.ObservableEmitter
import io.reactivex.Single
import io.reactivex.schedulers.Schedulers
import io.reactivex.subjects.BehaviorSubject
import timber.log.Timber


class FirestoreAssetRepository(secondaryDB: FirebaseFirestore) : IAssetRepository {

    companion object {
        private const val ASSET_COLLECTION = "Assets"
        private const val REPORT_COLLECTION = "Reports"
    }

    private var remoteDB: FirebaseFirestore = secondaryDB
    private var changeObservable: Observable<List<DocumentSnapshot>>

    init {
        changeObservable = BehaviorSubject.create { emitter: ObservableEmitter<List<DocumentSnapshot>> ->
            val listeningRegistration = remoteDB.collection(ASSET_COLLECTION)
                .addSnapshotListener { value, error ->
                    if (value == null || error != null) {
                        return@addSnapshotListener
                    }

                    if (!emitter.isDisposed) {
                        emitter.onNext(value.documents)
                    }

                    value.documentChanges.forEach {
                        Timber.d("Data changed type ${it.type} document ${it.document.id}")
                    }
                }

            emitter.setCancellable { listeningRegistration.remove() }
        }
    }

    override fun getAllAssets(): Single<List<Asset>> {
        return Single.create<List<DocumentSnapshot>> { emitter ->
            remoteDB.collection(ASSET_COLLECTION)
                .get()
                .addOnSuccessListener {
                    if (!emitter.isDisposed) {
                        emitter.onSuccess(it.documents)
                    }
                }
                .addOnFailureListener {
                    if (!emitter.isDisposed) {
                        emitter.onError(it)
                    }
                }
        }
            .observeOn(Schedulers.io())
            .flatMapObservable { Observable.fromIterable(it) }
            .map(::mapDocumentToRemoteAsset)
            .map(::mapToAsset)
            .toList()
    }

    private fun mapDocumentToRemoteAsset(document: DocumentSnapshot) =
        document.toObject(RemoteAsset::class.java)!!.apply { id = document.id }

    override fun addAsset(asset: Asset): Completable {
        return Completable.create { emitter ->
            remoteDB.collection(ASSET_COLLECTION)
                .add(mapToAssetData(asset))
                .addOnSuccessListener {
                    it.collection(REPORT_COLLECTION)
                    if (!emitter.isDisposed) {
                        emitter.onComplete()
                    }
                }
                .addOnFailureListener {
                    if (!emitter.isDisposed) {
                        emitter.onError(it)
                    }
                }
        }
    }

    override fun deleteAsset(assetId: String): Completable {
        // TODO: delete report collection, sub collections are not deleted automatically
        // https://stackoverflow.com/questions/49125183/how-to-model-this-structure-to-handle-delete/
        return Completable.create { emitter ->
            remoteDB.collection(ASSET_COLLECTION)
                .document(assetId)
                .delete()
                .addOnSuccessListener {
                    if (!emitter.isDisposed) {
                        emitter.onComplete()
                    }
                }
                .addOnFailureListener {
                    if (!emitter.isDisposed) {
                        emitter.onError(it)
                    }
                }
        }
    }

    override fun setAssetLockState(assetId: String, lockState: Boolean): Completable {
        return Completable.create { emitter ->
            remoteDB.collection(ASSET_COLLECTION)
                .document(assetId)
                .update(getLockUpdate(lockState))
                .addOnSuccessListener {
                    Timber.d("Unlocked!")
                    if (!emitter.isDisposed) {
                        emitter.onComplete()
                    }
                }
                .addOnFailureListener {
                    Timber.d("Unlocking fail")
                    if (!emitter.isDisposed) {
                        emitter.onError(it)
                    }
                }
        }
    }

    override fun setAssetLockRadius(assetId: String, lockRadius: Int): Completable {
        return Completable.create { emitter ->
            remoteDB.collection(ASSET_COLLECTION)
                .document(assetId)
                .update(mapToLockRadiusUpdate(lockRadius * 25))
                .addOnSuccessListener {
                    if (!emitter.isDisposed) {
                        emitter.onComplete()
                    }
                }
                .addOnFailureListener {
                    if (!emitter.isDisposed) {
                        emitter.onError(it)
                    }
                }
        }
    }

    override fun setAssetPeriodInterval(assetId: String, periodIntervalProgress: Int): Completable {
        return Completable.create { emitter ->
            remoteDB.collection(ASSET_COLLECTION)
                .document(assetId)
                .update(mapToPeriodIntervalUpdate(periodIntervalProgress))
                .addOnSuccessListener {
                    if (!emitter.isDisposed) {
                        emitter.onComplete()
                    }
                }
                .addOnFailureListener {
                    if (!emitter.isDisposed) {
                        emitter.onError(it)
                    }
                }
        }
    }

    override fun getChangeObservable(): Observable<List<Asset>> =
        changeObservable.hide()
            .observeOn(Schedulers.io())
            .map { list -> list.map(::mapDocumentToRemoteAsset).map(::mapToAsset) }

}
