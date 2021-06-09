import Flutter
import UIKit
import Photos

public class SwiftFileManagementPlugin: NSObject, FlutterPlugin {
    public static func register(with registrar: FlutterPluginRegistrar) {
        let channel = FlutterMethodChannel(name: "file_management", binaryMessenger:registrar.messenger())
        let instance = SwiftFileManagementPlugin()
        registrar.addMethodCallDelegate(instance, channel: channel)
    }

    var result: FlutterResult?;
    
    var assetCollection: PHAssetCollection!;
    var albumFound : Bool = false;
    var assetCollectionPlaceholder: PHObjectPlaceholder!;
    var photosAsset: PHFetchResult<PHAsset>!;
    var videoAsset: PHFetchResult<PHAsset>!;
    
    public func handle(_ call: FlutterMethodCall, result: @escaping FlutterResult) {
        self.result = result
        if (call.method == "getPlatformVersion") {
            result("iOS " + UIDevice.current.systemVersion)
        } else if (call.method == "saveFileInAppDirInGallery") {
            guard let arguments = call.arguments as? [String: Any],
                  let fileExtension = arguments["extension"] as? String,
                  let path = arguments["filePath"] as? String else {return}
            if (isImageFile(fileExtension: fileExtension)) {
                createAlbum(fileOperation: FileOperation.saveImage, path: path)
            } else {
                if (UIVideoAtPathIsCompatibleWithSavedPhotosAlbum(path)) {
                    createAlbum(fileOperation: FileOperation.saveVideo, path: path)
                } else {
                    //TODO convert video
                    result(FlutterMethodNotImplemented)
                }
            }
        } else if (call.method == "saveByteArrayToFileInAppDirInGallery") {
            result(FlutterMethodNotImplemented)
        } else if (call.method == "getAllFileAppDirInGallery") {
            createAlbum(fileOperation: FileOperation.getAllFileAppDirInGallery, path: nil)
        } else if (call.method == "getFileByNameFromAppDirInGallery") {
            result(FlutterMethodNotImplemented)
        } else {
            result(FlutterMethodNotImplemented)
        }
    }
    
    func saveVideo(_ path: String) {
        PHPhotoLibrary.shared().performChanges({
            let assetRequest = PHAssetChangeRequest.creationRequestForAssetFromVideo(atFileURL:URL(string: path)!)
            let assetPlaceholder = assetRequest?.placeholderForCreatedAsset
            self.videoAsset = PHAsset.fetchAssets(in: self.assetCollection, options: nil)
            let albumChangeReques = PHAssetCollectionChangeRequest(for:self.assetCollection, assets: self.videoAsset)
            albumChangeReques?.addAssets([assetPlaceholder] as NSFastEnumeration)
        }, completionHandler: { success, error in
            if (success) {
                self.saveResult()
            } else {
                print(error)
                self.saveResult(error: "Video file is not save.")
            }
        })
//        if !isReturnImagePath {
//            UISaveVideoAtPathToSavedPhotosAlbum(path, self,#selector(didFinishSavingVideo(videoPath:error:contextInfo:)), nil)
//            return
//        }
//        var videoIds: [String] = []
//
//        PHPhotoLibrary.shared().performChanges( {
//            let req = PHAssetChangeRequest.creationRequestForAssetFromVideo(atFileURL:URL(string: path)!)
//            if let videoId = req?.placeholderForCreatedAsset?.localIdentifier {
//                videoIds.append(videoId)
//            }
//        }, completionHandler: { [unowned self] (success, error) in
//            DispatchQueue.main.async {
//                if (success && videoIds.count > 0) {
//                    let assetResult = PHAsset.fetchAssets(withLocalIdentifiers:videoIds, options: nil)
//                    if (assetResult.count > 0) {
//                        let videoAsset = assetResult[0]
//                        PHImageManager().requestAVAsset(forVideo: videoAsset, options:nil) { (avurlAsset, audioMix, info) in
//                            if let urlStr = (avurlAsset as?AVURLAsset)?.url.absoluteString {
//                                self.saveResult(uri: urlStr)
//                            }
//                        }
//                    }
//                } else {
//                    self.saveResult(error: "Video file is not save.")
//                }
//            }
//        })
    }
    
    func saveImage(_ path: String) {
        PHPhotoLibrary.shared().performChanges({
            let assetRequest = PHAssetChangeRequest.creationRequestForAssetFromImage(atFileURL:URL(string: path)!)
            let assetPlaceholder = assetRequest?.placeholderForCreatedAsset
            self.photosAsset = PHAsset.fetchAssets(in: self.assetCollection, options: nil)
            let albumChangeReques = PHAssetCollectionChangeRequest(for:                         self.assetCollection, assets: self.photosAsset)
            albumChangeReques?.addAssets([assetPlaceholder] as NSFastEnumeration)
        }, completionHandler: { success, error in
            if (success) {
                self.saveResult()
            } else {
                print(error)
                self.saveResult(error: "Image file is not save.")
            }
        })
    }
    
    func getAllFileAppDirInGallery() {
        let assets : PHFetchResult = PHAsset.fetchAssets(in: assetCollection, options: nil)
        var fileId = [String?]()
        assets.enumerateObjects{(object: AnyObject!, index: Int, stop:UnsafeMutablePointer<ObjCBool>) in
            if object is PHAsset {
                let asset = object as! PHAsset
                let options = PHContentEditingInputRequestOptions()
                options.canHandleAdjustmentData = { (adjustmeta)-> Bool in true }
                asset.requestContentEditingInput(with: options) { (contentEditingInput, info) in
                    if let urlStr = contentEditingInput?.fullSizeImageURL?.absoluteString {
                        fileId.append(urlStr)
                        if (index+1 == assets.count) {
                            self.result?(fileId)
                        }
                    }
                }
            }
        }
        if (assets.count == 0) {
            self.result?(fileId)
        }
    }
    
    func createAlbum(fileOperation: FileOperation, path: String?) {
        let fetchOptions = PHFetchOptions()
        fetchOptions.predicate = NSPredicate(format: "title = %@", displayName ?? "file_management")
        let collection : PHFetchResult = PHAssetCollection.fetchAssetCollections(with:.album, subtype: .any, options: fetchOptions)
        //Check return value - If found, then get the first album out
        if let _: AnyObject = collection.firstObject {
            self.albumFound = true
            assetCollection = collection.firstObject as! PHAssetCollection
            callFileOperation(fileOperation: fileOperation, path: path)
        } else {
            //If not found - Then create a new album
            PHPhotoLibrary.shared().performChanges({
                let createAlbumRequest : PHAssetCollectionChangeRequest = PHAssetCollectionChangeRequest.creationRequestForAssetCollection(withTitle: self.displayName ?? "file_management")
                self.assetCollectionPlaceholder = createAlbumRequest.placeholderForCreatedAssetCollection
                }, completionHandler: { success, error in
                    self.albumFound = success

                    if (success) {
                        let collectionFetchResult = PHAssetCollection.fetchAssetCollections(withLocalIdentifiers: [self.assetCollectionPlaceholder.localIdentifier], options: nil)
                        print(collectionFetchResult)
                        self.assetCollection = collectionFetchResult.firstObject as! PHAssetCollection
                        self.callFileOperation(fileOperation: fileOperation, path: path)
                    }
            })
        }
    }
    
    func callFileOperation(fileOperation: FileOperation, path: String?) {
        switch fileOperation {
        case FileOperation.saveImage:
            saveImage(path!)
        case FileOperation.saveVideo:
            saveVideo(path!)
        case FileOperation.getAllFileAppDirInGallery:
            getAllFileAppDirInGallery()
        default:
            self.saveResult(error: "File operation is incorrect")
        }
    }

    var displayName: String? {
            return Bundle.main.infoDictionary!["CFBundleName"] as? String
        }
    
    @objc func didFinishSavingImage(image: UIImage, error: NSError?, contextInfo: UnsafeMutableRawPointer?) {
        saveResult(error: error?.description)
    }
    
    @objc func didFinishSavingVideo(videoPath: String, error: NSError?, contextInfo:    UnsafeMutableRawPointer?) {
        saveResult(error: error?.description)
    }
    
    func saveResult(error: String? = nil, uri: String? = nil) {
        var saveResult = FileResult()
        saveResult.errorMessage = error?.description
        saveResult.uri = uri
        result?(saveResult.toDic())
    }
    
    func isImageFile(fileExtension: String) -> Bool {
        return fileExtension.hasSuffix(".jpg")
            || fileExtension.hasSuffix(".png")
            || fileExtension.hasSuffix(".JPEG")
            || fileExtension.hasSuffix(".JPG")
            || fileExtension.hasSuffix(".PNG")
            || fileExtension.hasSuffix(".gif")
            || fileExtension.hasSuffix(".GIF")
    }
}

public struct FileResult: Encodable {
    var uri: String?
    var errorMessage: String?
    func toDic() -> [String:Any]? {
        let encoder = JSONEncoder()
        guard let data = try? encoder.encode(self) else { return nil }
        if (!JSONSerialization.isValidJSONObject(data)) {
            return try? JSONSerialization.jsonObject(with: data, options: .mutableContainers) as? [String:Any]
        }
        return nil
    }
}

enum FileOperation {
    case saveImage
    case saveVideo
    case getAllFileAppDirInGallery
    case west
}
