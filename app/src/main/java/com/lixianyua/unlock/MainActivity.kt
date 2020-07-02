package com.lixianyua.unlock

import android.os.Bundle
import android.view.View
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.Group
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.io.SuFile
import com.topjohnwu.superuser.io.SuRandomAccessFile

class MainActivity : AppCompatActivity() {
    private val avbVbMetaImageHeaderSize = 256
    private val vbMetaPath = "/dev/block/by-name/vbmeta"
    private lateinit var vbMetaFile:SuRandomAccessFile

    private val rootState = MutableLiveData<Boolean>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        Shell.Config.verboseLogging(BuildConfig.DEBUG)
        rootState.observe(this, Observer<Boolean> {
            if (it){
                vbMetaFile=SuRandomAccessFile.open(SuFile(vbMetaPath),"rw")
                findViewById<Group>(R.id.stateViewGroup).visibility= View.VISIBLE
                findViewById<Group>(R.id.loadViewGroup).visibility= View.GONE
                findViewById<TextView>(R.id.tvState).text=getStateDescription(getCurrentState())
                findViewById<Switch>(R.id.switchState).isChecked=getCurrentState()==3
                findViewById<Switch>(R.id.switchState).setOnCheckedChangeListener { _, b ->
                    writeState(b)
                    findViewById<TextView>(R.id.tvState).text=getStateDescription(getCurrentState())
                    Toast.makeText(this,"重启生效",Toast.LENGTH_SHORT).show()
                }
            }else{
                findViewById<Group>(R.id.stateViewGroup).visibility= View.GONE
                findViewById<Group>(R.id.loadViewGroup).visibility= View.VISIBLE
                findViewById<TextView>(R.id.loadText).text="Root权限都不给我，我给你解个球啊"
            }
        })
    }

    override fun onResume() {
        super.onResume()
        Root.getRootPrivilege(object :Root.GotRootListener{
            override fun onGotRootResult(hasRoot: Boolean) {
                if (rootState.value!=hasRoot) rootState.value=hasRoot
            }
        })
    }

    private fun getCurrentState(): Int {
        val headerData=ByteArray(avbVbMetaImageHeaderSize)
        vbMetaFile.seek(0)
        vbMetaFile.read(headerData,0,headerData.size)
        val header=AvbVBMetaImageHeader.unpack(headerData)
        return header.flags.last().toInt()
    }

    private fun getStateDescription(state:Int):String{
        return if (state==3){
            "状态$state：已解锁"
        }else{
            "状态$state：未解锁"
        }
    }

    private fun writeState(isUnlock:Boolean){
        Shell.su("cat $vbMetaPath > ${filesDir.absolutePath}/vbmeta.img").exec()
        val writeData= byteArrayOf(if (isUnlock) 3 else 0)
        val raf = SuRandomAccessFile.open("${filesDir.absolutePath}/vbmeta.img","rw")
        raf.seek(123)
        raf.write(writeData,0,writeData.size)
        Shell.su("cat ${filesDir.absolutePath}/vbmeta.img > $vbMetaPath").exec()
    }
}




class AvbVBMetaImageHeader {
    /*   0: Four bytes equal to "AVB0" (AVB_MAGIC). */
    val magic = ByteArray(4)
    /*   4: The major version of libavb required for this header. */
    val requiredLibavbVersionMajor  = ByteArray(4)
    /*   8: The minor version of libavb required for this header. */
    val requiredLibavbVersionMinor = ByteArray(4)
    /*  12: The size of the signature block. */
    val authenticationDataBlockSize = ByteArray(8)
    /*  20: The size of the auxiliary data block. */
    val auxiliaryDataBlockSize=ByteArray(8)
    /*  28: The verification algorithm used, see |AvbAlgorithmType| enum. */
    val algorithmType=ByteArray(4)
    /*  32: Offset into the "Authentication data" block of hash data. */
    val hashOffset=ByteArray(8)
    /*  40: Length of the hash data. */
    val hashSize=ByteArray(8)
    /*  48: Offset into the "Authentication data" block of signature data. */
    val signatureOffset=ByteArray(8)
    /*  56: Length of the signature data. */
    val signatureSize=ByteArray(8)
    /*  64: Offset into the "Auxiliary data" block of public key data. */
    val publicKeyOffset=ByteArray(8)
    /*  72: Length of the public key data. */
    val publicKeySize=ByteArray(8)
    /*  80: Offset into the "Auxiliary data" block of public key metadata. */
    val publicKeyMetadataOffset=ByteArray(8)
    /*  88: Length of the public key metadata. Must be set to zero if there
     *  is no public key metadata.
     */
    val publicKeyMetadataSize=ByteArray(8)
    /*  96: Offset into the "Auxiliary data" block of descriptor data. */
    val descriptorsOffset=ByteArray(8)
    /* 104: Length of descriptor data. */
    val descriptorsSize =ByteArray(8)
    /* 112: The rollback index which can be used to prevent rollback to
     *  older versions.
     */
    val rollbackIndex=ByteArray(8)
    /* 120: Flags from the AvbVBMetaImageFlags enumeration. This must be
     * set to zero if the vbmeta image is not a top-level image.
     */
    val flags=ByteArray(4)
    /* 124: The location of the rollback index defined in this header.
     * Only valid for the main vbmeta. For chained partitions, the rollback
     * index location must be specified in the AvbChainPartitionDescriptor
     * and this value must be set to 0.
     */
    val rollbackIndexLocation=ByteArray(4)
    /* 128: The release string from avbtool, e.g. "avbtool 1.0.0" or
     * "avbtool 1.0.0 xyz_board Git-234abde89". Is guaranteed to be NUL
     * terminated. Applications must not make assumptions about how this
     * string is formatted.
     */
    val releaseString=ByteArray(48)
    /* 176: Padding to ensure struct is size AVB_VBMETA_IMAGE_HEADER_SIZE
     * bytes. This must be set to zeroes.
     */
    val reserved=ByteArray(80)

    companion object{
        fun unpack(data:ByteArray):AvbVBMetaImageHeader{
            val header=AvbVBMetaImageHeader()
            var flag=0
            System.arraycopy(data,flag,header.magic,0,header.magic.size)
            flag+=header.magic.size //5
            System.arraycopy(data,flag,header.requiredLibavbVersionMajor,0,header.requiredLibavbVersionMajor.size)
            flag+=header.requiredLibavbVersionMajor.size //9
            System.arraycopy(data,flag,header.requiredLibavbVersionMinor,0,header.requiredLibavbVersionMinor.size)
            flag+=header.requiredLibavbVersionMinor.size
            System.arraycopy(data,flag,header.authenticationDataBlockSize,0,header.authenticationDataBlockSize.size)
            flag+=header.authenticationDataBlockSize.size
            System.arraycopy(data,flag,header.auxiliaryDataBlockSize,0,header.auxiliaryDataBlockSize.size)
            flag+=header.auxiliaryDataBlockSize.size
            System.arraycopy(data,flag,header.algorithmType,0,header.algorithmType.size)
            flag+=header.algorithmType.size
            System.arraycopy(data,flag,header.hashOffset,0,header.hashOffset.size)
            flag+=header.hashOffset.size
            System.arraycopy(data,flag,header.hashSize,0,header.hashSize.size)
            flag+=header.hashSize.size
            System.arraycopy(data,flag,header.signatureOffset,0,header.signatureOffset.size)
            flag+=header.signatureOffset.size
            System.arraycopy(data,flag,header.signatureSize,0,header.signatureSize.size)
            flag+=header.signatureSize.size
            System.arraycopy(data,flag,header.publicKeyOffset,0,header.publicKeyOffset.size)
            flag+=header.publicKeyOffset.size
            System.arraycopy(data,flag,header.publicKeySize,0,header.publicKeySize.size)
            flag+=header.publicKeySize.size
            System.arraycopy(data,flag,header.publicKeyMetadataOffset,0,header.publicKeyMetadataOffset.size)
            flag+=header.publicKeyMetadataOffset.size
            System.arraycopy(data,flag,header.publicKeyMetadataSize,0,header.publicKeyMetadataSize.size)
            flag+=header.publicKeyMetadataSize.size
            System.arraycopy(data,flag,header.descriptorsOffset,0,header.descriptorsOffset.size)
            flag+=header.descriptorsOffset.size
            System.arraycopy(data,flag,header.descriptorsSize,0,header.descriptorsSize.size)
            flag+=header.descriptorsSize.size
            System.arraycopy(data,flag,header.rollbackIndex,0,header.rollbackIndex.size)
            flag+=header.rollbackIndex.size
            System.arraycopy(data,flag,header.flags,0,header.flags.size)
            flag+=header.flags.size
            System.arraycopy(data,flag,header.rollbackIndexLocation,0,header.rollbackIndexLocation.size)
            flag+=header.rollbackIndexLocation.size
            System.arraycopy(data,flag,header.releaseString,0,header.releaseString.size)
            flag+=header.releaseString.size
            System.arraycopy(data,flag,header.reserved,0,header.reserved.size)
            flag+=header.reserved.size
            return header
        }
    }
}