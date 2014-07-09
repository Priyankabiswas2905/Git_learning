function setPermission(fullName, email, resourceType, resourceId, permissionType, callbackName, callback, cbParam1){
	
	var setOrder = {};
	setOrder['userFullName'] = fullName;
	setOrder['userEmail'] = email;
	setOrder['resourceId'] = resourceId;
	setOrder['newPermissionLevel'] = permissionType;
	
	var request = $.ajax({
	       type: 'POST',
	       url: window.location.protocol + "//" + window.location.hostname + (window.location.port ? ':' + window.location.port: '')+"/api/users/modifyRightsTo"+capitaliseFirstLetter(resourceType),
	       data: JSON.stringify(setOrder),
	       contentType: "application/json"
	     });
	
	request.done(function (response, textStatus, jqXHR){
        console.log("Response " + response);        
        alert(response);
        if(response.indexOf("set to chosen level.") >= 0){
        	if(callbackName == "addNewRow"){
        		callback(fullName, email, permissionType, cbParam1);
        	}
        	else if(callbackName == "removeElem"){
        		callback(cbParam1);
        	}
        }
    });
	request.fail(function (jqXHR, textStatus, errorThrown){
		console.error(
    		"The following error occured: "+
    		textStatus, errorThrown		            
			);
		alert("ERROR: " + errorThrown +"." );
		if(callbackName == "resetValue"){
    		callback(cbParam1);
    	}
	
	});
	
}

function capitaliseFirstLetter(string)
{
    return string.charAt(0).toUpperCase() + string.slice(1);
}