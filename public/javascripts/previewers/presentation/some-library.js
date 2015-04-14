(function ($, Configuration) {
	  console.log("video presentation previewer for " + Configuration.id);  
	  console.log("Updating tab " + Configuration.tab);
	  
	  /*IF
	  	1)We are in the single file screen AND
	  	2)The user does not have editing rights
	  	In that case, we want the button for downloading the original ZIP file to be disabled when we have the merged video ready for download,
	  	to avoid confusion of general users between downloading the merged video and the original ZIP file.
	  */
	  if(Configuration.authenticatedFileModify){
		  $(document).ready(function() {
			  $("#downloadButtonFile").css("display","none");
		  });	  
	  }
	  
	  var hostAddress = window.location.protocol + "//" + window.location.hostname + (window.location.port ? ':' + window.location.port: '');
	  var pathJs = hostAddress + Configuration.jsPath + "/";
	  
	  var useTab = Configuration.tab;
	  var referenceUrl = Configuration.url;
	  var confId = Configuration.id;
	  var origFileId = Configuration.originalFileId;
	  
	  var s = document.createElement("script");
	  s.type = "text/javascript";
	  s.src = pathJs + "popcorn-complete.js";
	  $(useTab).append(s);
	  
	  $.ajax({
		    url: referenceUrl,
		    async:true,
		    success: function (data) {
		    	  var videosIds = data.split("\n");
		    		
		    	  $(useTab).append(			  
		    	     "<video width='100%' id='ourvideo_"+origFileId+"' controls>" +
		    	     		"<source src='" + jsRoutes.api.Previews.download(videosIds[1]).url  + "' type='video/mp4'></source>" +
		    	     		"<source src='" + jsRoutes.api.Previews.download(videosIds[2]).url  + "' type='video/webm'></source>"+
		    	     		"<p>Your browser cannot play MP4 or WebM (maybe no codex), cannot play video.</p>"+
		    	     "</video>"
		    	  );
		    	  $(useTab).append("<br/>");
		    	  $(useTab).append("<a class='btn btn-default' href='" + jsRoutes.api.Previews.download(videosIds[0]).url + "'>Download merged high-quality video.</a>");
		    	 },
		    	 error: function(jqXHR, textStatus, errorThrown) { 
		    	        alert("Status: " + textStatus); alert("Error: " + errorThrown); 
		    	    },
		    dataType: 'text'
		});
	  

	}(jQuery, Configuration));


