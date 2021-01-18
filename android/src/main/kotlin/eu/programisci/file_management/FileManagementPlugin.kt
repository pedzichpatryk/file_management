package eu.programisci.file_management

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Environment
import androidx.annotation.NonNull

import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import java.io.File
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
      "getAllFileAppDirInGallery" -> {
        result.success(getAllFileAppDirInGallery())
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
      context.sendBroadcast(Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, uri))
      FileResult(uri.toString().isNotEmpty(), uri.toString(), null).toHashMap()
    }  catch (e: IOException) {
      FileResult(false, null, e.toString()).toHashMap()
    }
  }

  private fun getAllFileAppDirInGallery(): MutableList<String> {
    val storePath =  Environment.getExternalStorageDirectory().absolutePath + File.separator + getApplicationName()
    val appDir = File(storePath)
    if (appDir.exists()) {
      val fileList = appDir.listFiles()?.map { Uri.fromFile(it).toString() }
      return fileList?.toMutableList() ?: mutableListOf()
    }
    return mutableListOf()
  }

  private fun generateFile(extension: String?, name: String?): File {
    val storePath =  Environment.getExternalStorageDirectory().absolutePath + File.separator + getApplicationName()
    val appDir = File(storePath)
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

class FileResult(var isSuccess: Boolean,
                 var uri: String? = null,
                 var errorMessage: String? = null) {
  fun toHashMap(): HashMap<String, Any?> {
    val hashMap = HashMap<String, Any?>()
    hashMap["isSuccess"] = isSuccess
    hashMap["uri"] = uri
    hashMap["errorMessage"] = errorMessage
    return hashMap
  }
}
