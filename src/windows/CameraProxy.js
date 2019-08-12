cordova.define("cordova-plugin-fusion-assessment.CameraProxy", function (require, exports, module) {
    /* global Windows:true, URL:true, module:true, require:true, WinJS:true */

    var getAppData = function () {
        return Windows.Storage.ApplicationData.current;
    };
    var encodeToBase64String = function (buffer) {
        return Windows.Security.Cryptography.CryptographicBuffer.encodeToBase64String(buffer);
    };
    //var OptUnique = Windows.Storage.CreationCollisionOption.generateUniqueName;
    //var CapMSType = Windows.Media.Capture.MediaStreamType;
    //var webUIApp = Windows.UI.WebUI.WebUIApplication;
    //var fileIO = Windows.Storage.FileIO;
    var pickerLocId = Windows.Storage.Pickers.PickerLocationId;
    var WMCapture = Windows.Media.Capture;

    var windowsVideoContainers = ['.avi', '.flv', '.asx', '.asf', '.mov', '.mp4', '.mpg', '.rm', '.srt', '.swf', '.wmv', '.vob'];
    var boundary = "*****";
    var crlf = "\r\n";
    var twoHyphens = "--";
    var endingSeparator = crlf + twoHyphens + boundary + twoHyphens + crlf;
    var formFieldSeparator = twoHyphens + boundary + crlf;

    /*
    cancelled: data.cancelled,
    capturedVideo: data.capturedVideo,
    capturedImage: data.capturedImage,
    videoUrl: data.videoUrl,
    videoImage: data.videoImage,
    videoTimestamp: data.videoTimestamp
    */

    module.exports = {

        takeVideo: function (successCallback, errorCallback, args) {

            try {
                var exerciseSettings = JSON.parse(args[0]);
                var systemSettings = JSON.parse(args[1]);

                if (!!systemSettings && !!systemSettings.pluginOptions && !!systemSettings.pluginOptions.fileSelection) {
                    takePictureFromFile(successCallback, errorCallback, exerciseSettings, systemSettings);
                } else {
                    takePictureFromCamera(successCallback, errorCallback, exerciseSettings, systemSettings);
                }
            } catch (err) {
                errorCallback(err);
            }
        },
    };

    function takePictureFromCamera(successCallback, errorCallback, exerciseSettings, systemSettings) {

        var cameraCaptureUI = new WMCapture.CameraCaptureUI();

        cameraCaptureUI.videoSettings.format = WMCapture.CameraCaptureUIVideoFormat.mp4;
        cameraCaptureUI.videoSettings.maxResolution = WMCapture.CameraCaptureUIMaxVideoResolution.highestAvailable;
        cameraCaptureUI.videoSettings.maxDurationInSeconds = 20;
        cameraCaptureUI.videoSettings.allowTrimming = true;

        cameraCaptureUI.captureFileAsync(WMCapture.CameraCaptureUIMode.video).done(function (video) {
            if (!video) {
                successCallback('{"cancelled":true}');
            }
            else {
                upload(video, successCallback, errorCallback, exerciseSettings, systemSettings);
            }
        }, function () {
            errorCallback('Camera unavailable');
        });

    }

    function takePictureFromFile(successCallback, errorCallback, exerciseSettings, systemSettings) {

        var fileOpenPicker = new Windows.Storage.Pickers.FileOpenPicker();
        fileOpenPicker.fileTypeFilter.replaceAll(windowsVideoContainers);
        fileOpenPicker.suggestedStartLocation = pickerLocId.documentsLibrary;

        fileOpenPicker.pickSingleFileAsync().done(function (file) {
            if (!file) {
                successCallback('{"cancelled":true}');
            } else {
                upload(file, successCallback, errorCallback, exerciseSettings, systemSettings);
            }
        }, function () {
            errorCallback('Camera unavailable');
        });
    }

    function upload(video, successCallback, errorCallback, exerciseSettings, systemSettings) {
        var client = new Windows.Web.Http.HttpClient();

        var headers = client.defaultRequestHeaders;

        headers.connection = "Keep-Alive";
        headers.cacheControl = "no-cache";

        for (key in systemSettings.headers) {
            headers.tryAppendWithoutValidation(key, systemSettings.headers[key]);
        }

        var request = ""
        if (!!exerciseSettings.testId) request += addFormField("testId", exerciseSettings.testId);
        if (!!exerciseSettings.testTypeId) request += addFormField("testTypeId", exerciseSettings.testTypeId);
        if (!!exerciseSettings.exerciseId) request += addFormField("exerciseId", exerciseSettings.exerciseId);
        if (!!exerciseSettings.viewId) request += addFormField("viewId", exerciseSettings.viewId);
        if (!!exerciseSettings.uniqueId) request += addFormField("uniqueId", exerciseSettings.uniqueId);
        if (!!exerciseSettings.version) request += addFormField("version", exerciseSettings.version);
        if (!!exerciseSettings.bodySideId) request += addFormField("bodySideId", exerciseSettings.bodySideId);

        request += addFileInfo(video.name, exerciseSettings);


        Windows.Storage.FileIO.readBufferAsync(video).then(buffer => {
            var fileContent = encodeToBase64String(buffer);
            request += fileContent;
            request += endingSeparator

            var content = new Windows.Web.Http.HttpStringContent(request, Windows.Storage.Streams.UnicodeEncoding.utf8, "multipart/form-data;boundary=" + boundary);
            var requestUri = new Windows.Foundation.Uri(systemSettings.endpointUrl);
            client.postAsync(requestUri, content).then(response => {
                if (response.isSuccessStatusCode) {
                    successCallback('{"cancelled":false, "capturedVideo":true, "capturedImage":false, "videoUrl":null, "videoImage":null, "videoTimestamp":null}');
                } else {
                    errorCallback('An error occurred while saving video file');
                }
            }, err => {
                errorCallback('An error occurred while saving video file');
            });

        }, err => {
            errorCallback('An error occurred while saving video file');
        });
    }

    function addFormField(name, value) {
        var s = ""
            + formFieldSeparator
            + "Content-Disposition: form-data; name=\"" + name + "\"" + crlf
            + "Content-Type: text/plain; charset=UTF-8" + crlf
            + crlf
            + value
            + crlf;
        return s;
    }

    function addFileInfo(filename, exerciseSettings) {

        var s = ""
            + formFieldSeparator
            + "Content-Disposition: form-data; name=\"" + (exerciseSettings.filePrefix || "exercise") + "-video.mp4\";filename=\"" + filename + "\""
            + crlf + crlf
        return s;
    }

    require('cordova/exec/proxy').add('FusionAssessment', module.exports);
});