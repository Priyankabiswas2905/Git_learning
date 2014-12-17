function removeDataset(datasetId,event, reloadPage){
	if(reloadPage === undefined) reloadPage = false;
	
	var request = jsRoutes.api.Datasets.deleteDataset(datasetId).ajax({
		type: 'POST'
	});
	request.done(function (response, textStatus, jqXHR){
        console.log("Response " + response);
        if($(event.target).is("span")){
        	$(event.target.parentNode.parentNode.parentNode).remove();
        }
        else{
        	$(event.target.parentNode.parentNode).remove();
        }
        
        if(reloadPage == true)
        	location.reload(true);
    });
	request.fail(function (jqXHR, textStatus, errorThrown){
		console.error("The following error occured: "+textStatus, errorThrown);
        var errMsg = "You must be logged in to remove a dataset from the system.";
        alert("The dataset was not removed due to : " + errorThrown); 
		
	});	
}