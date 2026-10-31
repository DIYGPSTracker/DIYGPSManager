package dev.csaba.diygpsmanager.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.tasks.OnCompleteListener
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import dev.csaba.diygpsmanager.ApplicationSingleton
import dev.csaba.diygpsmanager.R
import dev.csaba.diygpsmanager.data.getSecondaryFirebaseConfiguration
import dev.csaba.diygpsmanager.databinding.ActivityMainBinding
import dev.csaba.diygpsmanager.ui.adapter.AssetAdapter
import dev.csaba.diygpsmanager.ui.adapter.OnAssetInputListener
import dev.csaba.diygpsmanager.viewmodel.MainViewModel
import timber.log.Timber


class MainActivity : AppCompatActivityWithActionBar(), OnAssetInputListener {

    companion object {
        private const val SECONDARY_NAME = "secondary"
        private const val RC_NOTIFICATION_PERMISSION = 9002
        private const val GEO_FENCE_TOPIC = "geo_fence_exited"
    }

    private lateinit var binding: ActivityMainBinding
    private lateinit var viewModel: MainViewModel
    private val assetAdapter = AssetAdapter(this)
    private lateinit var auth: FirebaseAuth
    private var lookBackMinutes: Int = 10

    private val signInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            if (account != null) {
                firebaseAuthWithGoogle(account)
            }
        } catch (e: ApiException) {
            Timber.e(e, "Google sign in failed")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.recycler.layoutManager = LinearLayoutManager(this, RecyclerView.VERTICAL, false)
        binding.recycler.adapter = assetAdapter

        // Request POST_NOTIFICATIONS on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    RC_NOTIFICATION_PERMISSION
                )
            }
        }

        val appSingleton = application as ApplicationSingleton
        val projectConfiguration = this.getSecondaryFirebaseConfiguration()
        lookBackMinutes = projectConfiguration.lookBackMinutes
        // Get or initialize secondary FirebaseApp.
        if (appSingleton.firebaseApp == null) {
            val options = FirebaseOptions.Builder()
                .setProjectId(projectConfiguration.projectId)
                .setApplicationId(projectConfiguration.applicationId)
                .setApiKey(projectConfiguration.apiKey)
                .build()

            appSingleton.firebaseApp = try {
                FirebaseApp.getInstance(SECONDARY_NAME)
            } catch (e: IllegalStateException) {
                FirebaseApp.initializeApp(applicationContext, options, SECONDARY_NAME)
            }
        }

        val firebaseMessaging: FirebaseMessaging =
            appSingleton.firebaseApp!!.get(FirebaseMessaging::class.java)
        firebaseMessaging.subscribeToTopic(GEO_FENCE_TOPIC)
            .addOnCompleteListener { task ->
                if (!task.isSuccessful) {
                    Timber.d("Could not subscribe to topic $GEO_FENCE_TOPIC")
                } else {
                    Timber.d("Subscribed to topic $GEO_FENCE_TOPIC")
                }
            }

        firebaseMessaging.token.addOnCompleteListener(OnCompleteListener { task ->
            if (!task.isSuccessful) {
                Timber.e(task.exception, "Fetching FCM registration token failed")
                return@OnCompleteListener
            }

            // Log and toast
            val fenceExitedMessage = applicationContext.getString(R.string.geo_fence_exited)
            Timber.d(fenceExitedMessage)
            Snackbar.make(
                window.decorView.rootView,
                fenceExitedMessage,
                Snackbar.LENGTH_LONG
            ).show()
        })

        // Get FireStore for the secondary app.
        if (appSingleton.firestore == null) {
            appSingleton.firestore = FirebaseFirestore.getInstance(appSingleton.firebaseApp!!)
        }

        // Authenticate
        auth = FirebaseAuth.getInstance(appSingleton.firebaseApp!!)
        val shouldAuthenticate = auth.currentUser == null || auth.currentUser!!.uid.isBlank() ||
                projectConfiguration.authType != "anonymous" && auth.currentUser!!.isAnonymous
        if (shouldAuthenticate) {
            if (projectConfiguration.authType == "google") {
                val signInOptions = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                    .requestIdToken(getString(R.string.default_web_client_id))
                    .requestEmail()
                    .build()

                val account = GoogleSignIn.getLastSignedInAccount(this)
                if (account != null) {
                    firebaseAuthWithGoogle(account)
                } else {
                    val googleSignInClient = GoogleSignIn.getClient(this, signInOptions)
                    val signInIntent = googleSignInClient.signInIntent
                    signInLauncher.launch(signInIntent)
                }
            } else if (projectConfiguration.authType == "email") {
                auth.signInWithEmailAndPassword(projectConfiguration.email, projectConfiguration.code)
                    .addOnCompleteListener(this) { task ->
                        if (task.isSuccessful) {
                            Timber.d("signInWithEmailAndPassword:success")
                            populateViewModel(appSingleton.firestore!!)
                        } else {
                            Timber.e(task.exception, "signInWithEmailAndPassword:failure")
                            Snackbar.make(
                                window.decorView.rootView,
                                applicationContext.getString(R.string.authentication_failed),
                                Snackbar.LENGTH_INDEFINITE
                            ).setAction(R.string.acknowledge) {
                                Timber.d(getString(R.string.authentication_failed))
                            }.show()
                        }
                    }
            } else {
                auth.signInAnonymously()
                    .addOnCompleteListener(this) { task ->
                        if (task.isSuccessful) {
                            Timber.d("signInAnonymously:success")
                            populateViewModel(appSingleton.firestore!!)
                        } else {
                            Timber.e(task.exception, "signInAnonymously:failure")
                            Snackbar.make(
                                window.decorView.rootView,
                                applicationContext.getString(R.string.authentication_failed),
                                Snackbar.LENGTH_INDEFINITE
                            ).setAction(R.string.acknowledge) {
                                Timber.d(getString(R.string.authentication_failed))
                            }.show()
                        }
                    }
            }
        } else {
            populateViewModel(appSingleton.firestore!!)
        }
    }

    private fun firebaseAuthWithGoogle(account: GoogleSignInAccount) {
        Timber.d("firebaseAuthWithGoogle: ${account.id!!}")

        val credential = GoogleAuthProvider.getCredential(account.idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    // Sign in success, update UI with the signed-in user's information
                    Timber.d("signInWithCredential:success")
                    val appSingleton = application as ApplicationSingleton
                    populateViewModel(appSingleton.firestore!!)
                } else {
                    // If sign in fails, display a message to the user.
                    Timber.e(task.exception, "signInWithCredential:failure")
                    Snackbar.make(
                        window.decorView.rootView,
                        applicationContext.getString(R.string.authentication_failed),
                        Snackbar.LENGTH_INDEFINITE
                    ).setAction(R.string.acknowledge) {
                        Timber.d(getString(R.string.authentication_failed))
                    }.show()
                }
            }
    }

    private fun populateViewModel(firestore: FirebaseFirestore) {
        viewModel = MainViewModel(firestore)

        viewModel.assetList.observe(this) {
            assetAdapter.setItems(it)
        }

        binding.addAsset.setOnClickListener {
            viewModel.addAsset(binding.assetTitle.text.toString())
            binding.assetTitle.text?.clear()
        }
    }

    override fun onTrackClick(assetId: String) {
        val intent = Intent(this, MapsActivity::class.java)
        intent.putExtra("assetId", assetId)
        intent.putExtra("lookBackMinutes", lookBackMinutes)
        startActivity(intent)
    }

    override fun onFlipAssetLockClick(assetId: String, lockState: Boolean) {
        viewModel.setAssetLockState(assetId, lockState)
    }

    override fun onDeleteClick(assetId: String) {
        viewModel.deleteAsset(assetId)
    }

    override fun onLockRadiusChange(assetId: String, lockRadius: Int) {
        viewModel.setAssetLockRadius(assetId, lockRadius)
    }

    override fun onPeriodIntervalChange(assetId: String, periodIntervalProgress: Int) {
        viewModel.setAssetPeriodInterval(assetId, periodIntervalProgress)
    }
}
