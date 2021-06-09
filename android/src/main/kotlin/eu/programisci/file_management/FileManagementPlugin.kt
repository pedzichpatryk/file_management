package eu.programisci.file_management

import android.content.ContentValues
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.NonNull
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import java.io.*


/** FileManagementPlugin */
class FileManagementPlugin: FlutterPlugin, MethodCallHandler {
  /// The MethodChannel that will the communication between Flutter and native Android
  ///
  /// This local reference serves to register the plugin with the Flutter Engine and unregister it
  /// when the Flutter Engine is detached from the Activity
  private lateinit var channel : MethodChannel
  private lateinit var context: Context

  override fun onAttachedToEngine(@NonNull flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
    channel = MethodChannel(flutterPluginBinding.binaryMessenger, "file_management")
    context = flutterPluginBinding.applicationContext
    channel.setMethodCallHandler(this)
  }

  override fun onMethodCall(@NonNull call: MethodCall, @NonNull result: Result) {
    when (call.method) {
      "getPlatformVersion" -> {
        result.success("Android ${android.os.Build.VERSION.RELEASE}")
      }
      "saveFileInAppDirInGallery" -> {
        val filePath = call.argument<String>("filePath") ?: return
        val name = call.argument<String>("name")
        val extension = call.argument<String>("extension")

        result.success(saveFileInAppDirInGallery(filePath, name, extension))
      }
      "saveByteArrayToFileInAppDirInGallery" -> {
        val imageBytes = call.argument<ByteArray>("imageBytes") ?: return
        val name = call.argument<String>("name")
        val extension = call.argument<String>("extension")

        result.success(saveByteArrayToFileInAppDirInGallery(imageBytes, name, extension))
      }
      "getAllFileAppDirInGallery" -> {
        result.success(getAllFileAppDirInGallery())
      }
      "getFileByNameFromAppDirInGallery" -> {
        val name = call.argument<String>("name")
        val extension = call.argument<String>("extension")
        result.success(getFileByNameFromAppDirInGallery(extension, name))
      }
      else -> {
        result.notImplemented()
      }
    }
  }

  private fun saveFileInAppDirInGallery(filePath: String, name: String?, extension: String?): HashMap<String, Any?> {
    return try  {
      val originalFile = File(filePath)
      if (android.os.Build.VERSION.SDK_INT >= 29) {
        val (values, mimeTypeWithUri) = generateUriForFile(extension, name)
        mimeTypeWithUri.second?.let { uri ->
          val bytes = ByteArray(originalFile.length().toInt())
          val fis = FileInputStream(originalFile)
          fis.read(bytes)
          fis.close()
          context.contentResolver.openOutputStream(uri)?.use { outputStream ->
            outputStream.write(bytes)
          }
          if (mimeTypeWithUri.first.isVideo()) {
            values.put(MediaStore.Video.Media.IS_PENDING, false)
          } else {
            values.put(MediaStore.Images.Media.IS_PENDING, false)
          }
          context.contentResolver.update(uri, values, null, null)
          FileResult(uri.toString(), null).toHashMap()
        }
        FileResult(null, "File not save.").toHashMap()
      } else {
        val file = generateFile(extension, name)
        originalFile.copyTo(file, overwrite = true)

        val uri = Uri.fromFile(file)
        MediaScannerConnection.scanFile(context, arrayOf(file.toString()),
                arrayOf(file.name), null)
        FileResult(uri.toString(), null).toHashMap()
      }
    }  catch (e: IOException) {
      FileResult(null, e.toString()).toHashMap()
    }
  }

  private fun saveByteArrayToFileInAppDirInGallery(bytes: ByteArray, name: String?, extension: String?): HashMap<String, Any?> {
    return try {
      if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
        val (values, mimeTypeWithUri) = generateUriForFile(extension, name)
        mimeTypeWithUri.second?.let { uri ->
          context.contentResolver.openOutputStream(uri)?.use { outputStream ->
            outputStream.write(bytes)
          }
          if (mimeTypeWithUri.first.isVideo()) {
            values.put(MediaStore.Video.Media.IS_PENDING, false)
          } else {
            values.put(MediaStore.Images.Media.IS_PENDING, false)
          }
          context.contentResolver.update(uri, values, null, null)
          FileResult(uri.toString(), null).toHashMap()
        }
        FileResult(null, "File not save.").toHashMap()
      } else {
        val file = generateFile(extension, name)
        if (!file.exists()) {
          file.createNewFile()
        }
        val fos = FileOutputStream(file)
        fos.write(bytes)
        fos.close()
        val uri = Uri.fromFile(file)
        MediaScannerConnection.scanFile(context, arrayOf(file.toString()),
                arrayOf(file.name), null)
        FileResult(uri.toString(), null).toHashMap()
      }
    } catch (e: Exception) {
      FileResult(null, e.toString()).toHashMap()
    }
  }

  private fun getAllFileAppDirInGallery(): MutableList<String> {
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
      val filesList = mutableListOf<String>()
      getAllImageFromAppDirInGallery(filesList)
      getAllVideoFromAppDirInGallery(filesList)
      return filesList
    } else {
      val appDir = File(Environment.getExternalStorageDirectory().absolutePath + File.separator + getApplicationName())
      if (appDir.exists()) {
        val fileList = appDir.listFiles()?.map { Uri.fromFile(it).toString() }
        return fileList?.toMutableList() ?: mutableListOf()
      }
    }
    return mutableListOf()
  }

  private fun getAllImageFromAppDirInGallery(filesList: MutableList<String>) {
    val projection = arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DISPLAY_NAME)
    context.contentResolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, projection,
            MediaStore.Files.FileColumns.RELATIVE_PATH + " like ? ", arrayOf("%" + "Pictures/" + getApplicationName() + "%"), null)?.use { cursor ->
      while (cursor.moveToNext()) {
        val photoUri = Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                cursor.getString(cursor.getColumnIndex(MediaStore.Images.Media._ID)))
        val name = cursor.getString(cursor.getColumnIndex(MediaStore.Images.Media.DISPLAY_NAME))
        context.contentResolver.openInputStream(photoUri)?.use {
          val file = File(context.externalCacheDir, name)
          val fos = FileOutputStream(file)
          fos.write(it.readBytes())
          fos.close()
          val uri = Uri.fromFile(file)
          filesList.add(uri.toString())
        }
      }
    }
  }

  private fun getAllVideoFromAppDirInGallery(filesList: MutableList<String>) {
    val projection = arrayOf(MediaStore.Video.Media._ID, MediaStore.Video.Media.DISPLAY_NAME)
    context.contentResolver.query(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, projection,
            MediaStore.Files.FileColumns.RELATIVE_PATH + " like ? ", arrayOf("%" + "Movies/" + getApplicationName() + "%"), null)?.use { cursor ->
      while (cursor.moveToNext()) {
        val photoUri = Uri.withAppendedPath(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                cursor.getString(cursor.getColumnIndex(MediaStore.Video.Media._ID)))
        val name = cursor.getString(cursor.getColumnIndex(MediaStore.Video.Media.DISPLAY_NAME))
        context.contentResolver.openInputStream(photoUri)?.use {
          val file = File(context.externalCacheDir, name)
          val fos = FileOutputStream(file)
          fos.write(it.readBytes())
          fos.close()
          val uri = Uri.fromFile(file)
          filesList.add(uri.toString())
        }
      }
    }
  }

  private fun getFileByNameFromAppDirInGallery(extension: String?, name: String?) : FileResult {
    return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
      var uri: Uri? = null
      if ( mutableListOf(".avi", ".wmv", ".mp4", ".mpg", ".mpeg").contains(extension) ) {
        val projection = arrayOf(MediaStore.Video.Media._ID, MediaStore.Video.Media.DISPLAY_NAME)
        context.contentResolver.query(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, projection,
                MediaStore.Files.FileColumns.RELATIVE_PATH + " like ? ", arrayOf("%" + "Movies/" + getApplicationName() + "%"), null)?.use { cursor ->
          while (cursor.moveToNext()) {
            val displayName = cursor.getString(cursor.getColumnIndex(MediaStore.Video.Media.DISPLAY_NAME))
            if(displayName == name+extension) {
              val photoUri = Uri.withAppendedPath(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                      cursor.getString(cursor.getColumnIndex(MediaStore.Video.Media._ID)))
              context.contentResolver.openInputStream(photoUri)?.use {
                val file = File(context.externalCacheDir, displayName)
                val fos = FileOutputStream(file)
                fos.write(it.readBytes())
                fos.close()
                uri = Uri.fromFile(file)
              }
            }
            break
          }
        }
        FileResult(uri?.toString())
      } else {
        val projection = arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DISPLAY_NAME)
        context.contentResolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, projection,
                MediaStore.Files.FileColumns.RELATIVE_PATH + " like ? ", arrayOf("%" + "Pictures/" + getApplicationName() + "%"), null)?.use { cursor ->
          while (cursor.moveToNext()) {
            val displayName = cursor.getString(cursor.getColumnIndex(MediaStore.Images.Media.DISPLAY_NAME))
            if (displayName == name+extension) {
              val photoUri = Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                      cursor.getString(cursor.getColumnIndex(MediaStore.Images.Media._ID)))
              context.contentResolver.openInputStream(photoUri)?.use {
                val file = File(context.externalCacheDir, displayName)
                val fos = FileOutputStream(file)
                fos.write(it.readBytes())
                fos.close()
                uri = Uri.fromFile(file)
              }
            }
          }
        }
        FileResult(uri?.toString())
      }
    } else {
      val appDirPath = Environment.getExternalStorageDirectory().absolutePath + File.separator + getApplicationName()
      val file = File(appDirPath, extension + name)
      if (file.exists()) {
        val uri = Uri.fromFile(file)
        FileResult(uri.toString(), null)
      }
      FileResult(null, FileNotFoundException("File not found exception: ${file.absolutePath}").toString())
    }
  }

  /**generate file before api 29*/
  private fun generateFile(extension: String?, name: String?): File {
    val appDir = File(Environment.getExternalStorageDirectory().absolutePath + File.separator + getApplicationName())
    if (!appDir.exists()) {
      appDir.mkdir()
    }
    var fileName = name ?: System.currentTimeMillis().toString()
    fileName += if (!extension.isNullOrBlank()) {
      (extension)
    } else {
      (".jpg")
    }
    return File(appDir, fileName)
  }

  /**generate file for api 29 and higher*/
  private fun generateUriForFile(extension: String?, name: String?) : Pair<ContentValues, Pair<EMimeType, Uri?>> {
    val uri: Uri?
    val values = ContentValues()
    val mimeType = EMimeType.values().firstOrNull { it.extension == extension } ?: EMimeType.JPEG
    return if (mimeType.isVideo()) {
      values.put(MediaStore.Video.Media.MIME_TYPE, mimeType.mimeType)
      values.put(MediaStore.Video.Media.DATE_ADDED, System.currentTimeMillis() / 1000)
      values.put(MediaStore.Video.Media.DISPLAY_NAME, name ?: System.currentTimeMillis().toString())
      values.put(MediaStore.Video.Media.DATE_TAKEN, System.currentTimeMillis())
      values.put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/" + getApplicationName())
      values.put(MediaStore.Video.Media.IS_PENDING, true)
      uri = context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
      Pair(values, Pair(mimeType, uri))
    } else {
      values.put(MediaStore.Images.Media.MIME_TYPE, EMimeType.values().firstOrNull { it.extension == extension }?.mimeType
              ?: "image/jpeg")
      values.put(MediaStore.Images.Media.DATE_ADDED, System.currentTimeMillis() / 1000)
      values.put(MediaStore.Images.Media.DISPLAY_NAME, name
              ?: System.currentTimeMillis().toString())
      values.put(MediaStore.Images.Media.DATE_TAKEN, System.currentTimeMillis())
      values.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/" + getApplicationName())
      values.put(MediaStore.Images.Media.IS_PENDING, true)
      uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
      Pair(values, Pair(mimeType, uri))
    }

  }

  private fun getApplicationName(): String {
    var ai: ApplicationInfo? = null
    try {
      ai = context.packageManager.getApplicationInfo(context.packageName, 0)
    } catch (e: PackageManager.NameNotFoundException) {

    }
    val appName: String = if (ai != null) {
      val charSequence = context.packageManager.getApplicationLabel(ai)
      StringBuilder(charSequence.length).append(charSequence).toString()
    } else {
      "Unknown application file"
    }
    return  appName
  }

  override fun onDetachedFromEngine(@NonNull binding: FlutterPlugin.FlutterPluginBinding) {
    channel.setMethodCallHandler(null)
  }

}

class FileResult(var uri: String? = null,
                 var errorMessage: String? = null) {
  fun toHashMap(): HashMap<String, Any?> {
    val hashMap = HashMap<String, Any?>()
    hashMap["uri"] = uri
    hashMap["errorMessage"] = errorMessage
    return hashMap
  }
}
