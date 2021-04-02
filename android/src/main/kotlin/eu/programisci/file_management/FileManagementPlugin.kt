package eu.programisci.file_management

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import androidx.annotation.NonNull
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException


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
      val file = generateFile(extension, name)
      originalFile.copyTo(file, overwrite = true)

      val uri = Uri.fromFile(file)
      MediaScannerConnection.scanFile(context, arrayOf(file.toString()),
              arrayOf(file.name), null)
      FileResult(uri.toString(), null).toHashMap()
    }  catch (e: IOException) {
      FileResult(null, e.toString()).toHashMap()
    }
  }

  private fun saveByteArrayToFileInAppDirInGallery(bytes: ByteArray, name: String?, extension: String?) {
    try {
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
    } catch (e: Exception) {
      FileResult(null, e.toString()).toHashMap()
    }
  }

  private fun getAllFileAppDirInGallery(): MutableList<String> {
    val picturesStoryPath = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
    val moviesStoryPath = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
    if (picturesStoryPath != null && moviesStoryPath != null) {
      val pictureAppDir = File(picturesStoryPath.absolutePath, getApplicationName())
      val movieAppDir = File(moviesStoryPath.absolutePath, getApplicationName())
      val fileList = mutableListOf<String>()
      if (pictureAppDir.exists()) {
        val pictures = pictureAppDir.listFiles()?.map { Uri.fromFile(it).toString() }
        pictures?.let { fileList.addAll(it) }
      }
      if (movieAppDir.exists()) {
        val movies = movieAppDir.listFiles()?.map { Uri.fromFile(it).toString() }
        movies?.let { fileList.addAll(it) }
      }
      return fileList
    }
    return mutableListOf()
  }

  private fun getFileByNameFromAppDirInGallery(extension: String?, name: String?) {
    val appDirPath = if ( mutableListOf(".avi", ".wmv", ".mp4", ".mpg", ".mpeg").contains(extension) ) {
      File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)?.absolutePath, getApplicationName()).absolutePath
    } else {
      File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)?.absolutePath, getApplicationName()).absolutePath
    }
    val file = File(appDirPath, extension + name)
    if (file.exists()) {
      val uri = Uri.fromFile(file)
      FileResult(uri.toString(), null).toHashMap()
    }
    FileResult(null, FileNotFoundException("File not found exception: ${file.absolutePath}").toString()).toHashMap()
  }

  private fun generateFile(extension: String?, name: String?): File {
    val appDir = if ( mutableListOf(".avi", ".wmv", ".mp4", ".mpg", ".mpeg").contains(extension) ) {
      File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)?.absolutePath, getApplicationName())
    } else {
      File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)?.absolutePath, getApplicationName())
    }
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

  private fun getApplicationName(): String {
    var ai: ApplicationInfo? = null
    try {
      ai = context.packageManager.getApplicationInfo(context.packageName, 0)
    } catch (e: PackageManager.NameNotFoundException) {

    }
    val appName: String
    appName = if (ai != null) {
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
