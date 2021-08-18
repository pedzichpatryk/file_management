package eu.programisci.file_management

import android.annotation.SuppressLint
import android.content.*
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.database.Cursor
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.annotation.NonNull
import androidx.annotation.RequiresApi
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import java.io.*
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.collections.LinkedHashMap
import kotlin.collections.set


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
      "shareFiles" -> {
        try {
          val fileUris = call.argument<List<String>>("fileUris")
          val mimeTypes = call.argument<List<String>>("mimeTypes") ?: emptyList()
          shareFiles(fileUris, mimeTypes)
          result.success(null)
        } catch (e: IOException) {
          result.error(e.message, null, null)
        }
      }
      "deleteFiles" -> {
        try {
          val fileUris = call.argument<List<String>>("fileUris")
          deleteFiles(fileUris)
          result.success(null)
        } catch (e: IOException) {
          result.error(e.message, null, null)
        }
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
        }
        FileResult(mimeTypeWithUri.second?.toString(), "File not save.").toHashMap()
      } else {
        val file = generateFile(extension, name)
        originalFile.copyTo(file, overwrite = true)

        val uri = Uri.fromFile(file)
        val mimeType = if (arrayOf(".avi", ".wmv", ".mp4", ".mpg", ".mpeg").contains(extension)) {
          "video/x-msvideo"
        } else {
          "image/jpeg"
        }
        MediaScannerConnection.scanFile(context, arrayOf(file.path),
                arrayOf(mimeType), null)
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

        val mimeType = if (arrayOf(".avi", ".wmv", ".mp4", ".mpg", ".mpeg").contains(extension)) {
          "video/x-msvideo"
        } else {
          "image/jpeg"
        }
        MediaScannerConnection.scanFile(context, arrayOf(file.path),
                arrayOf(mimeType), null)
        FileResult(uri.toString(), null).toHashMap()
      }
    } catch (e: Exception) {
      FileResult(null, e.toString()).toHashMap()
    }
  }

  private fun getAllFileAppDirInGallery(): LinkedHashMap<String, String?> {
    val filesMap = linkedMapOf<String, String?>()
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
      getAllImageFromAppDirInGallery(filesMap)
      getAllVideoFromAppDirInGallery(filesMap)
      return filesMap
    } else {
      val appDir = File(Environment.getExternalStorageDirectory().absolutePath + File.separator + getApplicationName())
      if (appDir.exists()) {
        appDir.listFiles()?.forEach { filesMap[Uri.fromFile(it).toString()] = null }
        return filesMap
      }
    }
    return filesMap
  }

  private fun getAllImageFromAppDirInGallery(filesMap: LinkedHashMap<String, String?>) {
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
          filesMap.put(uri.toString(), photoUri.toString())
        }
      }
    }
  }

  private fun getAllVideoFromAppDirInGallery(filesMap: LinkedHashMap<String, String?>) {
    val projection = arrayOf(MediaStore.Video.Media._ID, MediaStore.Video.Media.DISPLAY_NAME)
    context.contentResolver.query(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, projection,
            MediaStore.Files.FileColumns.RELATIVE_PATH + " like ? ", arrayOf("%" + "Movies/" + getApplicationName() + "%"), null)?.use { cursor ->
      while (cursor.moveToNext()) {
        val videoUri = Uri.withAppendedPath(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                cursor.getString(cursor.getColumnIndex(MediaStore.Video.Media._ID)))
        val name = cursor.getString(cursor.getColumnIndex(MediaStore.Video.Media.DISPLAY_NAME))
        context.contentResolver.openInputStream(videoUri)?.use {
          val file = File(context.externalCacheDir, name)
          val fos = FileOutputStream(file)
          fos.write(it.readBytes())
          fos.close()
          val uri = Uri.fromFile(file)
          filesMap.put(uri.toString(), videoUri.toString())
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

  @Throws(IOException::class)
  fun shareFiles(uris: List<String>?, mimeTypes: List<String>)  {
    require(!(uris == null || uris.isEmpty())) { "Non-empty path expected" }
    val fileUris: ArrayList<Uri> = ArrayList(uris.map { Uri.parse(it) })
    val shareIntent = Intent()
    if (fileUris.size == 1) {
      shareIntent.action = Intent.ACTION_SEND
      shareIntent.putExtra(Intent.EXTRA_STREAM, fileUris[0])
      shareIntent.type = if (!mimeTypes.isEmpty()) mimeTypes[0] else "*/*"
    } else {
      shareIntent.action = Intent.ACTION_SEND_MULTIPLE
      shareIntent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, fileUris)
      shareIntent.type = reduceMimeTypes(mimeTypes)
    }
    shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    val chooserIntent = Intent.createChooser(shareIntent, null /* dialog title optional */)
    val resInfoList: List<ResolveInfo> = context
      .packageManager
      .queryIntentActivities(chooserIntent, PackageManager.MATCH_DEFAULT_ONLY)
    for (resolveInfo in resInfoList) {
      val packageName = resolveInfo.activityInfo.packageName
      for (fileUri in fileUris) {
        context.grantUriPermission(
            packageName,
            fileUri,
            Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION
          )
      }
    }
    chooserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(chooserIntent)
  }

  fun deleteFiles(uris: List<String>?) {
    uris?.forEach { uri ->
      context.contentResolver.delete(Uri.parse(uri), null, null)
    }
  }

  private fun reduceMimeTypes(mimeTypes: List<String>): String {
    return if (mimeTypes.size > 1) {
      var reducedMimeType = mimeTypes[0]
      for (i in 1 until mimeTypes.size) {
        val mimeType = mimeTypes[i]
        if (reducedMimeType != mimeType) {
          if (getMimeTypeBase(mimeType) == getMimeTypeBase(reducedMimeType)) {
            reducedMimeType = getMimeTypeBase(mimeType) + "/*"
          } else {
            reducedMimeType = "*/*"
            break
          }
        }
      }
      reducedMimeType
    } else if (mimeTypes.size == 1) {
      mimeTypes[0]
    } else {
      "*/*"
    }
  }

  private fun getMimeTypeBase(mimeType: String?): String {
    return if (mimeType == null || !mimeType.contains("/")) {
      "*"
    } else mimeType.substring(0, mimeType.indexOf("/"))
  }

}

class FileResult(var uri: String? = null,
                 var errorMessage: String? = null,
                 var contentUri: String? = null) {
  fun toHashMap(): HashMap<String, Any?> {
    val hashMap = HashMap<String, Any?>()
    hashMap["uri"] = uri
    hashMap["errorMessage"] = errorMessage
    hashMap["contentUri"] = contentUri
    return hashMap
  }
}
