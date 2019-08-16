package com.example.receiptstake

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.DiffUtil
import com.google.firebase.ml.vision.FirebaseVision
import com.google.firebase.ml.vision.common.FirebaseVisionImage
import com.google.firebase.ml.vision.text.FirebaseVisionText
import com.yuyakaido.android.cardstackview.*
import kotlinx.android.synthetic.main.activity_main.*
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.time.LocalDateTime
import java.util.*




class MainActivity : AppCompatActivity(), CardStackListener {

    private var currentPhotoPath: String = null.toString()
    private val _photoRequest = 1

    private fun dispatchTakePictureIntent() {
        var photoFile: File? = null

        // Try and open camera
        Intent(MediaStore.ACTION_IMAGE_CAPTURE).also { takePictureIntent ->
            // On opening camera
            takePictureIntent.resolveActivity(packageManager)?.also {
                // Generate File
                photoFile = try {
                    File.createTempFile(
                        "JPEG_${LocalDateTime.now()}_", /* prefix */
                        ".jpg", /* suffix */
                        getExternalFilesDir(Environment.DIRECTORY_PICTURES) /* directory */
                    ).apply {
                        // Save a file: path for use with ACTION_VIEW intents
                        currentPhotoPath = absolutePath
                    }
                } catch (ex: IOException) {
                    return
                }
                // Generate URI; Save photo
                photoFile.also {
                    val photoURI = FileProvider.getUriForFile(
                        this,
                        "com.example.android.fileprovider",
                        it!!
                    )
                    takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI)
                    startActivityForResult(takePictureIntent, _photoRequest)
                }
            }
        }

        currentPhotoPath = photoFile!!.absolutePath
    }


    private fun processImage() {
        val bmp = BitmapFactory.decodeFile(currentPhotoPath) ?: return
        val rotation = getCameraPhotoOrientation(currentPhotoPath)
        val matrix = Matrix()

        matrix.postRotate(-1 * rotation)
        val rotatedBmp = Bitmap.createBitmap(bmp,0,0, bmp.width, bmp.height, matrix, true)

        val image = FirebaseVisionImage.fromBitmap(rotatedBmp)
        val detector = FirebaseVision.getInstance().onDeviceTextRecognizer

        val result = detector.processImage(image)
            .addOnSuccessListener {
                createSpot(it)
            }
            .addOnFailureListener {
                // Task failed with an exception
                // ...
            }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when(requestCode) {
            _photoRequest -> processImage()
            else -> super.onActivityResult(requestCode, resultCode, data)
        }
    }

    private fun getCameraPhotoOrientation(imagePath: String): Float {
        var rotate = 0f
        try {
            val orientation = ExifInterface(imagePath).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_UNDEFINED
            )
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_270 -> rotate = 270f
                ExifInterface.ORIENTATION_ROTATE_180 -> rotate = 180f
                ExifInterface.ORIENTATION_ROTATE_90 -> rotate = 90f
                ExifInterface.ORIENTATION_NORMAL -> rotate = 0f
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return rotate
    }

    private val cardStackView by lazy { findViewById<CardStackView>(R.id.card_stack_view) }
    private val manager by lazy { CardStackLayoutManager(this, this) }
    private val adapter by lazy {CardStackAdapter(createSpots())}
    private var spot: Int = 0

    private lateinit var file: File
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)
        btn_camera.setOnClickListener{dispatchTakePictureIntent()}
        btn_replace.setOnClickListener {cardStackView.rewind()}
        btn_remove.setOnClickListener{deleteCard()}

        val letDirectory = File(this.filesDir, "LET")
        letDirectory.mkdirs()
        file = File(letDirectory, "Records.txt")
        if(!file.exists())
        {
            file.createNewFile()
        }

        manager.setStackFrom(StackFrom.Top)
        manager.setVisibleCount(3)
        manager.setTranslationInterval(8.0f)
        manager.setScaleInterval(0.90f)
        manager.setSwipeThreshold(0.3f)
        manager.setMaxDegree(20.0f)
        manager.setDirections(Direction.HORIZONTAL)
        manager.setCanScrollHorizontal(true)
        manager.setCanScrollVertical(false)
        manager.setSwipeableMethod(SwipeableMethod.Manual)
        manager.setOverlayInterpolator(LinearInterpolator())
        cardStackView.layoutManager = manager
        cardStackView.adapter = adapter
        cardStackView.itemAnimator.apply {
            if (this is DefaultItemAnimator) supportsChangeAnimations = false
        }
    }

    private fun createSpots(): List<Spot> {

        val spots = ArrayList<Spot>()

        val contents = file.readText().split(0x1F.toChar())

        for (content in contents) {
            val vals = content.split(0x3.toChar())
            if (vals.size == 4) {
                spots.add(Spot(name = vals[1], city = vals[2], text = vals[3]))
            }
        }

        return spots
    }

    override fun onCardDisappeared(view: View?, position: Int) {
        // Not used
    }

    override fun onCardDragging(direction: Direction?, ratio: Float) {
        // Not used
    }

    override fun onCardCanceled() {
        // Not used
    }

    override fun onCardAppeared(view: View?, position: Int) {
        // Not used
    }

    override fun onCardSwiped(direction: Direction?) {
        spot++
    }

    override fun onCardRewound() {
        spot--
        if (spot < 0) {spot = 0}
    }

    private fun deleteCard() {
        if (adapter.isEmpty()) return

        val new = mutableListOf<Spot>().apply {
            addAll(adapter.getSpots())
            removeAt(manager.topPosition)
        }
        save(new)

    }

    private fun createSpot(text: FirebaseVisionText) {
        var str = ""
        var foundTotal = false
        var total = 0f
        str = "$str${text.textBlocks[0].lines[0].text + '\n'}"

        for(block in text.textBlocks) {
            for(line in block.lines) {
                for(element in line.elements) {
                    if(element.text.contains("total", true)) foundTotal = true
                    if(foundTotal && element.text.toFloatOrNull() != null) total = element.text.toFloat()
                }
                if(foundTotal && total != 0f) break
                foundTotal = false
            }
            if(foundTotal && total != 0f) break
        }

        str += "\nTotal: $total"

        val new = mutableListOf<Spot>().apply {
            addAll(adapter.getSpots())
            add(manager.topPosition, Spot(name = "Receipt Source", city = "Location", text = str))
        }
        save(new)
    }

    private fun save(new: List<Spot>) {
        val callback = SpotDiffCallback(adapter.getSpots(), new)
        val result = DiffUtil.calculateDiff(callback)
        adapter.setSpots(new)
        result.dispatchUpdatesTo(adapter)

        FileOutputStream(file).use {
            it.write(adapter.toString().toByteArray())
        }
    }
}