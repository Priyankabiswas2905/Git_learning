
function addPromotedMetadataField(data) {

    return new Promise(function(resolve, reject) {

        var addApi = jsRoutes.api.Metadata.addPromotedMetadataField();
        var request = addApi.ajax({
            type: 'POST',
            data: JSON.stringify(data),
            contentType: "application/json"
        });

        request.done(function (response, textStatus, jqXHR) {
            if (textStatus == "success") {
                notify("Metadata field successfully promoted.", "success", 3000);
                resolve(response);
            }

        });

        request.fail(function (jqXHR, textStatus, errorThrown) {
            notify("ERROR: " + jqXHR.responseJSON + " Metadata field promotion failed.", "error");
            reject(Error(jqXHR.statusText));
        });
    });
}

function editPromotedMetadataField() {

}

function deletePromotedMetadataField(id) {

    return new Promise(function(resolve, reject) {

        var deleteApi = jsRoutes.api.Metadata.deletePromotedMetadataField(id);
        var request = deleteApi.ajax({
            type: 'DELETE'
        });

        request.done(function (response, textStatus, jqXHR) {
            if (textStatus == "success") {
                notify("Metadata field successfully demoted.", "success", 3000);
                resolve(response);
            }
        });

        request.fail(function (jqXHR, textStatus, errorThrown) {
            notify("ERROR: " + jqXHR.responseJSON + " Metadata field demotion failed.", "error");
            reject(Error(jqXHR.statusText));
        });
    });

}

function getPromotedMetadataFields() {

    return new Promise(function(resolve, reject) {

        var getApi = jsRoutes.api.Metadata.getPromotedMetadataFields();
        var request = getApi.ajax({
            type: 'GET',
            contentType: "application/json"
        });

        request.done(function (response, textStatus, jqXHR) {
            if (textStatus == "success") {
                resolve(response);
            }
        });

        request.fail(function (jqXHR, textStatus, errorThrown) {
            notify("ERROR: " + jqXHR.responseJSON + " Fetching promoted metadata fields failed.", "error");
            reject(Error(jqXHR.statusText))
        });
    });

}