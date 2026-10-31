package dev.csaba.diygpsmanager.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.SeekBar
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import dev.csaba.diygpsmanager.R
import dev.csaba.diygpsmanager.data.Asset
import dev.csaba.diygpsmanager.data.remote.mapPeriodIntervalToProgress
import dev.csaba.diygpsmanager.databinding.RecyclerItemBinding


class AssetAdapter(private val assetInputListener: OnAssetInputListener?) :
    RecyclerView.Adapter<AssetViewHolder>(), SeekBar.OnSeekBarChangeListener {

    private val assetList = emptyList<Asset>().toMutableList()
    private var inputListener: OnAssetInputListener? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AssetViewHolder {
        inputListener = assetInputListener
        val binding = RecyclerItemBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        binding.trackAsset.setOnClickListener { button ->
            assetInputListener?.onTrackClick(button.tag as String)
        }
        binding.flipAssetLock.setOnClickListener { button ->
            assetInputListener?.run {
                val assetParts = (button.tag as String).split("_")
                val currentlyLocked = assetParts[0] == "l"
                this.onFlipAssetLockClick(assetParts[1], !currentlyLocked)
            }
        }
        binding.deleteAsset.setOnClickListener { button ->
            assetInputListener?.onDeleteClick(button.tag as String)
        }
        binding.assetLockRadiusSeekBar.setOnSeekBarChangeListener(this)
        binding.assetPeriodIntervalSeekBar.setOnSeekBarChangeListener(this)
        return AssetViewHolder(binding)
    }

    override fun getItemCount() = assetList.size

    override fun onBindViewHolder(holder: AssetViewHolder, position: Int) {
        val asset = assetList[position]
        with(holder.binding) {
            assetTitle.text = asset.title
            assetLockLat.text = "${asset.lockLat}"
            assetLockLon.text = "${asset.lockLon}"
            assetLockRadius.text = "${asset.lockRadius}"
            assetLockRadiusSeekBar.progress = asset.lockRadius / 25
            assetPeriodInterval.text = "${asset.periodInterval}"
            assetPeriodIntervalSeekBar.progress = mapPeriodIntervalToProgress(asset.periodInterval)

            val assetTag = assetList[position].id
            trackAsset.tag = assetTag
            flipAssetLock.setIconResource(
                if (asset.lock) R.drawable.ic_lock_closed else R.drawable.ic_lock_open
            )
            val lockAssetPrefix = if (asset.lock) "l" else "u"
            flipAssetLock.tag = "${lockAssetPrefix}_${assetTag}"
            deleteAsset.tag = assetTag
            assetLockRadiusSeekBar.tag = "r_${assetTag}"
            assetPeriodIntervalSeekBar.tag = "i_${assetTag}"
            statusIcon.setImageResource(
                if (asset.lockAlert) R.drawable.ic_warning else R.drawable.ic_check_circle
            )
        }
    }

    fun setItems(newAssetList: List<Asset>) {
        val diffResult = DiffUtil.calculateDiff(AssetDiffUtilCallback(assetList, newAssetList))

        assetList.clear()
        assetList.addAll(newAssetList)

        diffResult.dispatchUpdatesTo(this)
    }

    override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
        if (!fromUser)
            return

        val barTag = seekBar?.tag as String
        val assetTag = barTag.substring(2)

        if (barTag[0] == 'r') {
            inputListener?.onLockRadiusChange(assetTag, seekBar.progress)
        } else if (barTag[0] == 'i') {
            inputListener?.onPeriodIntervalChange(assetTag, seekBar.progress)
        }
    }

    override fun onStartTrackingTouch(seekBar: SeekBar?) {}

    override fun onStopTrackingTouch(seekBar: SeekBar?) {}
}

class AssetViewHolder(val binding: RecyclerItemBinding) : RecyclerView.ViewHolder(binding.root)

interface OnAssetInputListener {
    fun onTrackClick(assetId: String)
    fun onFlipAssetLockClick(assetId: String, lockState: Boolean)
    fun onDeleteClick(assetId: String)
    fun onLockRadiusChange(assetId: String, lockRadius: Int)
    fun onPeriodIntervalChange(assetId: String, periodIntervalProgress: Int)
}
